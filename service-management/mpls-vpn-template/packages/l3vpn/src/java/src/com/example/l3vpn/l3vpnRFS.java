package com.example.l3vpn;

import com.example.l3vpn.namespaces.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;

public class l3vpnRFS {
    /**
     * Create callback method.
     * This method is called when a service instance committed due to a create
     * or update event.
     *
     * This method returns a opaque as a Properties object that can be null.
     * If not null it is stored persistently by Ncs.
     * This object is then delivered as argument to new calls of the create
     * method for this service (fastmap algorithm).
     * This way the user can store and later modify persistent data outside
     * the service model that might be needed.
     *
     * @param context - The current ServiceContext object
     * @param service - The NavuNode references the service node.
     * @param ncsRoot - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws ConfException
     */
    @ServiceCallback(servicePoint="l3vpn-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws ConfException {

        try {
            // check if it is reasonable to assume that devices
            // initially has been sync-from:ed
            NavuList managedDevices = ncsRoot.
                container("devices").list("device");
            for (NavuContainer device : managedDevices) {
                if (device.list("capability").isEmpty()) {
                    String mess = "Device %1$s has no known capabilities, " +
                                   "has sync-from been performed?";
                    String key = device.getKey().elementAt(0).toString();
                    throw new DpCallbackException(String.format(mess, key));
                }
            }
        } catch (DpCallbackException e) {
            throw (DpCallbackException) e;
        } catch (Exception e) {
            throw new DpCallbackException("Not able to check devices", e);
        }

        try {
            Template t = new Template(context, "l3vpn");
            t.apply(service, new TemplateVariables());
        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
    }

    @ServiceCallback(servicePoint="l3vpn-servicepoint",
        callType=ServiceCBType.PRE_MODIFICATION)
    public Properties preModification(ServiceContext context,
            ServiceOperationType operation,
            ConfPath path,
            Properties opaque) throws DpCallbackException {
        if (operation == ServiceOperationType.DELETE) {
            // do nothing on delete
            return opaque;
        }
        // oper data in the shared structures needs to be
        // written in PRE_MODIFICATION so that it is not deleted
        // with the service
        // oper data in the service tree can be written in PRE_MOD
        // or in CREATE
        try {
            NavuNode service = context.getServiceNode();

            NavuList endpoints = service.list("endpoint");
            NavuContainer topology = context.getRootNode().getParent().
                container("http://com/example/l3vpn").
                container("topology");
            for(NavuContainer endpoint : endpoints.elements()) {
                String ceName =  endpoint.leaf("ce-device").
                                                    valueAsString();
                NavuContainer conn = getConnection(topology,
                                               endpoint.leaf("ce-device").
                                               valueAsString());
                NavuContainer ceEndpoint = getMyEndpoint(
                                                conn,ceName);
                ceEndpoint.leaf("ip-mask").set(getNetMask(ceEndpoint.
                                                    leaf("ip-address").
                                                    valueAsString()));
                endpoint.leaf("ip-address").set(getIPAddress(
                                                    getNextIPV4Address(
                                                     endpoint.
                                                     leaf("ip-network").
                                                     valueAsString())));
                endpoint.leaf("ip-mask").set(getNetMask(endpoint.
                                                leaf("ip-network").
                                                valueAsString()));
            }

            if (service.container("qos").leaf("qos-policy").exists()) {
                NavuNode n = service.container("qos").
                    leaf("qos-policy").deref().get(0);
                NavuContainer qosPolicy =
                    (NavuContainer) ((NavuLeaf) n).getParent();
                NavuList policyClass = qosPolicy.list("class");
                for(NavuContainer c : policyClass.elements()) {
                    NavuNode qosClass = c.leaf("qos-class").deref().get(0);

                    NavuContainer cl =  (NavuContainer)
                        ((NavuLeaf) qosClass).getParent();
                    for(NavuContainer match : cl.list("match-traffic").
                            elements()) {
                        match.leaf("source-mask").set(
                                prefixToWildcardMask(getIPPrefix(
                                        match.leaf("source-ip").
                                        valueAsString())));
                        match.leaf("destination-mask").set(
                                prefixToWildcardMask(getIPPrefix(
                                        match.leaf("destination-ip").
                                        valueAsString())));
                            }
                }
                NavuList matchRules = service.container("qos").
                    list("custom-qos-match");
                for(NavuContainer match : matchRules.elements()) {
                    match.leaf("source-mask").set(
                            prefixToWildcardMask(getIPPrefix(
                                    match.leaf("source-ip").
                                    valueAsString())));
                    match.leaf("destination-mask").set(
                            prefixToWildcardMask(getIPPrefix(
                                    match.leaf("destination-ip").
                                    valueAsString())));
                }
            }
        } catch (Exception e) {
            throw new DpCallbackException("", e);
        }
        return opaque;
    }

    private NavuContainer getMyEndpoint(NavuContainer conn,
            String deviceName) throws NavuException {
        if (deviceName.equals(conn.container("endpoint-1").
                leaf("device").valueAsString() )  ) {
            return conn.container("endpoint-1");
        }
        else {
            return conn.container("endpoint-2");
        }
    }


    private NavuContainer getConnection(NavuContainer topology,
            String deviceName) throws NavuException {
        NavuList connections = topology.list("connection");
        for(NavuContainer conn : connections.elements()) {
            if (deviceName.equals(conn.container("endpoint-1").
                    leaf("device").valueAsString() ) ||
                deviceName.equals(conn.container("endpoint-2").
                    leaf("device").valueAsString())) {
                return conn;
            }
        }
        return null;
    }

    private String getIPAddress(String prefix) {
        String[] parts = prefix.split("/");
        return parts[0];
    }

    private String getIPPrefix(String prefix) {
        String[] parts = prefix.split("/");
        if (parts.length > 1) {
            return parts[1];
        } else {
            return null;
        }
    }

    private String getNetMask(String addr) throws UnknownHostException {
        String[] parts = addr.split("/");
        String ip = parts[0];
        int prefix;
        if (parts.length < 2) {
            prefix = 0;
        } else {
            prefix = Integer.parseInt(parts[1]);
        }
        int mask = 0xffffffff << (32 - prefix);

        int value = mask;
        byte[] bytes = new byte[]{
                (byte)(value >>> 24), (byte)(value >> 16 & 0xff),
                (byte)(value >> 8 & 0xff), (byte)(value & 0xff) };

        InetAddress netAddr = InetAddress.getByAddress(bytes);
        return netAddr.getHostAddress();
    }

    private String getNextIPV4Address(String ip) {
        String ipAddr = ip.split("/")[0];
        String mask = ip.split("/")[1];

        String[] nums = ipAddr.split("\\.");
        int i = (Integer.parseInt(nums[0]) << 24 |
                        Integer.parseInt(nums[2]) << 8
              |  Integer.parseInt(nums[1]) << 16 |
                      Integer.parseInt(nums[3])) + 1;

        // If you wish to skip over .255 addresses.
        if ((byte) i == -1) i++;

        return String.format("%d.%d.%d.%d",
                             i >>> 24 & 0xFF, i >> 16 & 0xFF,
                             i >>   8 & 0xFF, i >>  0 & 0xFF)
                             +"/"+mask;
    }

    private String prefixToWildcardMask(String pre) throws UnknownHostException{
        if (pre == null) {
            return "";
        }
        int prefix = Integer.parseInt(pre);
        int mask = 0xffffffff << (32 - prefix);
        int value = mask;
        byte[] bytes = new byte[]{
            (byte)(~(value >>> 24) & 0xFF),
                (byte)( ~(value >> 16 & 0xff) & 0xFF),
                (byte)( ~(value >> 8 & 0xff) & 0xFF),
                (byte)( ~(value & 0xff) & 0xFF ) };

        InetAddress netAddr = InetAddress.getByAddress(bytes);
        return netAddr.getHostAddress();
    }
}
