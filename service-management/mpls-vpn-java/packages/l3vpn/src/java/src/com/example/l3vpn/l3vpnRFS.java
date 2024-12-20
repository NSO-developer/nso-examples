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

        Template peTemplate = new Template(context,
                "l3vpn-pe");
        Template ceTemplate = new Template(context,
               "l3vpn-ce");
        Template qosTemplate = new Template(context,
               "l3vpn-qos");
        Template qosPeTemplate = new Template(context,
                "l3vpn-qos-pe");
        Template qosPrioTemplate = new Template(context,
                "l3vpn-qos-prio");
        Template qosPePrioTemplate = new Template(context,
                "l3vpn-qos-pe-prio");
        Template aclTemplate = new Template(context,
                 "l3vpn-acl");
        Template classTemplate = new Template(context,
                 "l3vpn-qos-class");
        Template peClassTemplate = new Template(context,
                 "l3vpn-qos-pe-class");

        NavuList endpoints = service.list("endpoint");
        NavuContainer topology = ncsRoot.getParent().
                container("http://com/example/l3vpn").
                container("topology");
        for(NavuContainer endpoint : endpoints.elements()) {
            try {
                String ceName =  endpoint.leaf("ce-device").
                                                    valueAsString();
                // Get the PE connection for this endpoint router
                NavuContainer conn = getConnection(topology,
                                               endpoint.leaf("ce-device").
                                               valueAsString());
                NavuContainer peEndpoint = getConnectedEndpoint(
                                                conn,ceName);
                NavuContainer ceEndpoint = getMyEndpoint(
                                                conn,ceName);

                NavuLeaf vlan = conn.leaf("link-vlan");

                TemplateVariables vpnVar = new TemplateVariables();

                vpnVar.putQuoted("PE",peEndpoint.leaf("device").
                                            valueAsString());
                vpnVar.putQuoted("CE",endpoint.leaf("ce-device").
                                            valueAsString());
                vpnVar.putQuoted("CE_AS_NUM",endpoint.leaf("as-number").
                        valueAsString());
                vpnVar.putQuoted("VLAN_ID", vlan.valueAsString());
                vpnVar.putQuoted("LINK_PE_ADR", getIPAddress(peEndpoint.
                                                    leaf("ip-address").
                                                    valueAsString()));
                vpnVar.putQuoted("LINK_CE_ADR", getIPAddress(ceEndpoint.
                                                    leaf("ip-address").
                                                    valueAsString()));
                vpnVar.putQuoted("LINK_MASK", getNetMask(ceEndpoint.
                                                    leaf("ip-address").
                                                    valueAsString()));
                vpnVar.putQuoted("LINK_PREFIX",getIPPrefix(ceEndpoint.
                                    leaf("ip-address").
                                    valueAsString()));
                vpnVar.putQuoted("PE_INT_NAME", peEndpoint.
                                                 leaf("interface").
                                                 valueAsString());
                vpnVar.putQuoted("CE_INT_NAME", ceEndpoint.
                                                leaf("interface").
                                                valueAsString());
                vpnVar.putQuoted("CE_LOCAL_INT_NAME", endpoint.
                                            leaf("ce-interface").
                                            valueAsString());
                vpnVar.putQuoted("LOCAL_CE_ADR", getIPAddress(
                                                    getNextIPV4Address(
                                                     endpoint.
                                                     leaf("ip-network").
                                                     valueAsString())));
                vpnVar.putQuoted("LOCAL_CE_NET", getIPAddress(
                                                    endpoint.
                                                    leaf("ip-network").
                                                    valueAsString()));
                vpnVar.putQuoted("CE_MASK", getNetMask(endpoint.
                                                leaf("ip-network").
                                                valueAsString()));
                vpnVar.putQuoted("BW", endpoint.leaf("bandwidth").
                                                    valueAsString());

                peTemplate.apply(service, vpnVar);
                ceTemplate.apply(service, vpnVar);


                // Start of QOS section

                if (service.container("qos").leaf("qos-policy").exists()) {
                    Map<String, List<String>> qosClassMap= new HashMap<String,
                            List<String>>();

                    TemplateVariables qosVar = new TemplateVariables();
                    qosVar.putQuoted("POLICY_NAME",service.container("qos").
                            leaf("qos-policy").valueAsString());
                    qosVar.putQuoted("CE_INT_NAME", ceEndpoint.
                            leaf("interface").
                            valueAsString());
                    qosVar.putQuoted("PE_INT_NAME", peEndpoint.
                            leaf("interface").
                            valueAsString());
                    qosVar.putQuoted("VLAN_ID", vlan.valueAsString());
                    qosVar.putQuoted("PE",peEndpoint.leaf("device").
                            valueAsString());
                    qosVar.putQuoted("CE",endpoint.leaf("ce-device").
                            valueAsString());

                    // Find the globally defined QOS policy our service is
                    // referring to.
                    NavuNode n = service.container("qos").
                            leaf("qos-policy").deref().get(0);
                    NavuContainer qosPolicy =
                            (NavuContainer) ((NavuLeaf) n).getParent();
                    NavuList policyClass = qosPolicy.list("class");
                    // Iterate over all classes for this policy and its
                    //settings.
                    int classCounter = 0;
                    for(NavuContainer c : policyClass.elements()) {
                        NavuNode qosClass = c.leaf("qos-class").deref().get(0);

                        qosClassMap.put(c.leaf("qos-class").valueAsString(),
                                new ArrayList<String>());
                        NavuContainer cl =  (NavuContainer)
                                            ((NavuLeaf) qosClass).getParent();

                        if (cl.leaf("dscp-value").exists()) {
                            qosVar.putQuoted("CLASS_DSCP",cl.leaf("dscp-value").
                                valueAsString());
                            if (cl.leaf("dscp-value").valueAsString().
                                    equals("ef") ||
                                cl.leaf("dscp-value").valueAsString().
                                    equals("af31")) {
                                    qosVar.putQuoted("CLASS_PRIORITY","high");
                            }
                            else {
                                qosVar.putQuoted("CLASS_PRIORITY","low");
                            }
                        }
                        else {
                            qosVar.putQuoted("CLASS_PRIORITY","low");
                            qosVar.putQuoted("CLASS_DSCP", "");
                        }


                        qosVar.putQuoted("CLASS_NAME",c.leaf("qos-class").
                                valueAsString());
                        qosVar.putQuoted("CLASS_BW",
                                c.leaf("bandwidth-percentage").
                                valueAsString());
                        qosVar.putQuoted("CLASS_COUNTER",String.
                                                valueOf(classCounter));


                        if(c.leaf("priority").exists()) {
                            qosPrioTemplate.apply(service,qosVar);
                            qosPePrioTemplate.apply(service,qosVar);

                        }
                        else {
                            qosTemplate.apply(service, qosVar);
                            qosPeTemplate.apply(service, qosVar);

                        }
                        peClassTemplate.apply(service, qosVar);

                        // Also list all the globally defined traffic match
                        // statements for this class and add them to a arraylist
                        // to use for processing.
                        for(NavuContainer match : cl.list("match-traffic").
                                elements()) {
                            qosClassMap.get(c.leaf("qos-class").
                                    valueAsString()).
                                    add("GLOBAL-"+match.leaf("name").
                                            valueAsString());

                            TemplateVariables aclVar =
                                    setAclVars(match,"GLOBAL");
                            aclVar.putQuoted("CE",endpoint.leaf("ce-device").
                                    valueAsString());
                            aclTemplate.apply(service, aclVar);
                        }
                        classCounter++;
                    }

                    // Create ACL entries for all service specific match rules

                    NavuList matchRules = service.container("qos").
                                    list("custom-qos-match");
                    for(NavuContainer match : matchRules.elements()) {
                        String namePrefix = service.leaf("name").
                                                    valueAsString();
                        if(qosClassMap.containsKey(match.
                                leaf("qos-class").valueAsString())) {
                            qosClassMap.get(match.leaf("qos-class")
                                    .valueAsString()).
                            add(namePrefix+"-"+match.leaf("name").
                                                    valueAsString());
                        }
                        TemplateVariables aclVar = setAclVars(match,namePrefix);

                        aclVar.putQuoted("CE",endpoint.leaf("ce-device").
                                valueAsString());
                        aclTemplate.apply(service, aclVar);
                    }

                    for (Map.Entry<String, List<String>> entry :
                                            qosClassMap.entrySet()) {
                        for (String matchEntry : entry.getValue() ) {
                            TemplateVariables classVar = new
                                                    TemplateVariables();
                            classVar.putQuoted("CLASS_NAME", entry.getKey());
                            classVar.putQuoted("MATCH_ENTRY", matchEntry);
                            classVar.putQuoted("CE",endpoint.leaf("ce-device").
                                    valueAsString());
                            classTemplate.apply(service, classVar);
                        }
                    }
                }
                //qosTemplate.apply(service, variables);

            } catch (Exception e) {
                throw new DpCallbackException(e.getMessage(), e);
            }
        }
        return opaque;
    }

    private TemplateVariables setAclVars(NavuContainer match,
                                        String namePrefix)
        throws NavuException, UnknownHostException {
        TemplateVariables aclVar = new TemplateVariables();

        aclVar.putQuoted("ACL_NAME", namePrefix + "-" +
                                    match.leaf("name").
                                       valueAsString());
        aclVar.putQuoted("PROTOCOL", match.leaf("protocol").
            valueAsString());
        aclVar.putQuoted("SOURCE_IP", match.leaf("source-ip").
                valueAsString());
        if ("any".equals(match.leaf("source-ip").valueAsString())) {
            aclVar.putQuoted("SOURCE_IP_ADR","any");
            aclVar.putQuoted("SOURCE_WMASK"," ");
        }
        else {
            aclVar.putQuoted("SOURCE_IP_ADR",
                    getIPAddress(match.leaf("source-ip").
                            valueAsString()));
            aclVar.putQuoted("SOURCE_WMASK",
                    prefixToWildcardMask(getIPPrefix(
                            match.leaf("source-ip").
                            valueAsString())));
        }
        if ("any".equals(match.leaf("destination-ip").
                valueAsString())) {
            aclVar.putQuoted("DEST_IP_ADR","any");
            aclVar.putQuoted("DEST_WMASK"," ");
        }
        else {
            aclVar.putQuoted("DEST_IP_ADR",
                    getIPAddress(match.leaf("destination-ip").
                            valueAsString()));
            aclVar.putQuoted("DEST_WMASK",
                    prefixToWildcardMask(getIPPrefix(
                            match.leaf("destination-ip").
                            valueAsString())));
        }
        aclVar.putQuoted("PORT_START", match.leaf("port-start").
                valueAsString());
        aclVar.putQuoted("PORT_END", match.leaf("port-end").
                valueAsString());
        return aclVar;
    }

    private NavuContainer getConnectedEndpoint(NavuContainer conn,
                                    String deviceName)
            throws NavuException {
        if (deviceName.equals(conn.container("endpoint-1").
                leaf("device").valueAsString() )  ) {
                return conn.container("endpoint-2");
            }
            else {
                return conn.container("endpoint-1");
            }
    }

    private NavuContainer getMyEndpoint(NavuContainer conn,
            String deviceName)
                    throws NavuException {
        if (deviceName.equals(conn.container("endpoint-1").
                leaf("device").valueAsString() )  ) {
            return conn.container("endpoint-1");
        }
        else {
            return conn.container("endpoint-2");
        }
    }


    private NavuContainer getConnection(NavuContainer topology,
            String deviceName)
                    throws NavuException {

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
        return parts[1];
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

    private String prefixToWildcardMask(String pre)
            throws UnknownHostException{
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

    /**
     * Init method for selftest action
     */
    @ActionCallback(callPoint="l3vpn-self-test", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    /**
     * Selftest action implementation for service
     */
    @ActionCallback(callPoint="l3vpn-self-test", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            // Refer to the service yang model prefix
            String nsPrefix = "l3vpn";
            // Get the service instance key
            String str = ((ConfKey)kp[0]).toString();

          return new ConfXMLParam[] {
              new ConfXMLParamValue(nsPrefix, "success", new ConfBool(true)),
              new ConfXMLParamValue(nsPrefix, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("self-test failed", e);
        }
    }
}
