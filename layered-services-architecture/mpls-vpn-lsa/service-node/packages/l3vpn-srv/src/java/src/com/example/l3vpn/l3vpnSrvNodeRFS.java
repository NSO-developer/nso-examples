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
import com.tailf.maapi.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;


public class l3vpnSrvNodeRFS {


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
            String serviceName = service.leaf("name").valueAsString();
            NavuList managedDevices = ncsRoot.
                container("devices").list("device");

            for (NavuContainer device : managedDevices) {
                Maapi maapi = service.context().getMaapi();
                int tHandle = service.context().getMaapiHandle();


                NavuNode dstVpn = device.container("config").
                    namespace("l3vpn").container("vpn").
                    list("l3vpn").
                    sharedCreate(serviceName);
                ConfPath dst = dstVpn.getConfPath();
                ConfPath src = service.getConfPath();

                System.out.println("Copying from " + src + " to: " + dst);
                maapi.copyTree(tHandle, true, src, dst);
            }
        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
    }


    @ServiceCallback(servicePoint="l3vpn-shared-data",
        callType=ServiceCBType.CREATE)
    public Properties createShared(ServiceContext context,
                                   NavuNode service,
                                   NavuNode ncsRoot,
                                   Properties opaque)
        throws ConfException {

        try {
            NavuList managedDevices = ncsRoot.
                container("devices").list("device");

            String strType;
            if (service.getKeyPath().toString().equals("/l3vpn-srv:qos"))
                strType = "qos";
            else
                strType = "topology";


            for (NavuContainer device : managedDevices) {
                Maapi maapi = service.context().getMaapi();
                int tHandle = service.context().getMaapiHandle();


                NavuNode dstCont = device.container("config").
                    namespace("l3vpn").container(strType);
                ConfPath dst = dstCont.getConfPath();
                ConfPath src = service.getConfPath();
                maapi.copyTree(tHandle, true, src, dst);
            }
        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
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
