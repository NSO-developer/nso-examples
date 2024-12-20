package com.example.vpnep;

import java.util.List;
import java.util.Properties;
import java.io.IOException;

import com.example.vpnep.namespaces.vpnep;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ConfException;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.dp.services.ServiceOperationType;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.ns.Ncs;


public class vpnepRFS {


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
     * @throws DpCallbackException
     */

    @ServiceCallback(servicePoint="vpnep-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
            throws DpCallbackException, ConfException, IOException {
        NavuList managedDevices = null;
        try {
            // check if it is reasonable to assume that devices
            // initially has been sync-from:ed
            managedDevices = ncsRoot.container("devices").list("device");
            for (NavuContainer device : managedDevices) {
                if (device.list("capability").isEmpty()) {
                    String mess = "Device %1$s has no known capabilities, " +
                                   "has sync-from been performed?";
                    String key = device.getKey().elementAt(0).toString();
                    throw new DpCallbackException(String.format(mess, key));
                }
            }
        } catch (DpCallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new DpCallbackException("Not able to check devices", e);
        }


        String servicePath = null;
        try {
            NavuNode vpnep = service;
            servicePath = service.getKeyPath();

            // Get a list of all managed devices

            for(ConfValue device : vpnep.leafList("router")) {
                NavuContainer deviceContainer = (NavuContainer) ncsRoot.
                    container("devices").list("device").
                    elem(device.toString());
                NavuContainer ifs = deviceContainer.container("config").
                    namespace("r").container("sys").container("interfaces");

                // execute as shared create of the path
                // /interfaces/interface[name='x']/unit[name='i']

                NavuContainer unit =
                ifs.list("interface").sharedCreate(
                    vpnep.leaf("iface").value()).
                    list("unit").sharedCreate(
                        vpnep.leaf("unit").value());

                unit.leaf("vlan-id").sharedSet(vpnep.leaf("vid").value());
                unit.leaf("enabled").sharedSet(new ConfBool(true));
                ConfValue descr = vpnep.leaf("description").value();
                if (descr != null) {
                    unit.leaf("description").sharedSet(descr);
                }
            }
        } catch (NavuException e) {
            throw new DpCallbackException("Cannot create service " +
                                          servicePath, e);
        }
        return opaque;
    }

    @ServiceCallback(servicePoint = "vpnep-servicepoint",
                     callType = ServiceCBType.PRE_MODIFICATION)
    public Properties preModification(ServiceContext context,
                                      ServiceOperationType operation,
                                      ConfPath path,
                                      Properties opaque)
                                      throws DpCallbackException {
        try {
            vpnep vep = new vpnep();
            if (ServiceOperationType.DELETE.equals(operation)) {
                return opaque;
            }

            // get the in transaction changes for the current
            // service instance
            NavuNode service = context.getRootNode().getParent().
                container(vpnep.hash).container(vpnep._vpn_endpoints_).
                list(vpnep._vpn_endpoint_).elem((ConfKey) path.getKP()[0]);
            List<NavuNode> changedNodes = service.getChanges(null, true);

            for (NavuNode n : changedNodes) {
                if (n.getName().equals(vpnep._router_)) {
                    NavuLeafList routers = (NavuLeafList) n;
                    for(ConfValue device : routers) {
                        NavuNode devices = context.getRootNode().
                            container(Ncs._devices_);
                        NavuContainer sys = (NavuContainer) devices.
                            list("device").elem(device.toString()).
                            container("config").container("sys");

                        NavuList serverList = sys.container("dns").
                            list("server");
                        if (!serverList.containsNode("10.10.10.1")) {
                            serverList.create("10.10.10.1");
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            throw new DpCallbackException("Pre modification failed",e);
        }
        return opaque;
    }

    /**
     * Init method for selftest action
     */
    @ActionCallback(callPoint="vpnep-self-test", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    /**
     * Selftest action implementation for service
     */
    @ActionCallback(callPoint="vpnep-self-test", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            // Refer to the service yang model prefix
            String nsPrefix = "vpnep";
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
