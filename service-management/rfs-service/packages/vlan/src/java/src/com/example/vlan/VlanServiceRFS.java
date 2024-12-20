package com.example.vlan;


import java.util.Properties;

import com.example.vlan.namespaces.vlanService;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;

/**
 * This class implement the create logic for the Vlan Service.
 *
 * The example will show how to navigate with NAVU (Navigatinal Utillity).
 *
 * When we set values to our vlan service (example from the NCS CLI):
 *
 * % set services vlan slog unit 1 if eth0 vid 44
 *
 * % commit
 *
 * Then this mapping logic will run i.e the create() method will be
 * invoked by NCS.
 *
 */

public class VlanServiceRFS {

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
     * @param root    - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws DpCallbackException
     */
    @ServiceCallback(servicePoint = "vlanspnt",
        callType = ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode root,
                             Properties opaque) throws DpCallbackException {

        try {
            // check if it is reasonable to assume that devices
            // initially has been sync-from:ed
            NavuList managedDevices = root.
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
            // Get the container at keypath
            // /services/vlan{s1}

            NavuNode vlan = service;
            ConfPath kp = new ConfPath(vlan.getKeyPath());

            //Now we will need to iterate over all of our managed
            //devices and do a shareCreate of the interface and the unit

            //Get the list of all managed devices.
            NavuList managedDevices = root.container("devices").list("device");

            for(NavuContainer deviceContainer : managedDevices.elements()){

                NavuContainer ifs = deviceContainer.container("config").
                    namespace("r").container("sys").container("interfaces");

                // execute as shared create of the path
                //   /interfaces/interface[name='x']/unit[name='i']

                NavuContainer iface =
                    ifs.list("interface").sharedCreate(
                        vlan.leaf("iface").value());
                iface.leaf("enabled").sharedCreate();

               NavuContainer unit = iface.
                        list("unit").sharedCreate(
                            vlan.leaf("unit").value());

                unit.leaf("vlan-id").sharedSet(vlan.leaf("vid").value());
                unit.leaf("enabled").sharedSet(new ConfBool(true));
                unit.leaf("description").sharedSet(
                    vlan.leaf("description").value());
                for (ConfValue arpValue : vlan.leafList("arp")) {
                    unit.leafList("arp").sharedCreate(arpValue);
                }
            }
        } catch (Exception e) {
            throw new DpCallbackException("Could not instantiate service", e);
        }
        return opaque;
    }

    @ActionCallback(callPoint="vlanselftest", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    @ActionCallback(callPoint="vlanselftest", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {

        ConfNamespace ws = new vlanService();
        String str = ((ConfKey)kp[0]).toString();

        return new ConfXMLParam[]
            {new ConfXMLParamValue(ws, "success", new ConfBool(true)),
             new ConfXMLParamValue(ws, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("selftest failed", e);
        }

    }
}
