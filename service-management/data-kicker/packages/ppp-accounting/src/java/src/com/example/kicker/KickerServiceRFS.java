package com.example.kicker;


import java.util.ArrayList;
import java.util.Properties;

import com.tailf.conf.*;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.dp.services.ServiceOperationType;
import com.tailf.maapi.Maapi;
import com.tailf.navu.*;


import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import  com.example.kicker.namespaces.*;

/**
 * This class implement the create logic for the Kicker Example Service.
 *
 *
 */

public class KickerServiceRFS {

    private static Logger LOGGER  = LogManager.getLogger(
            KickerServiceRFS.class);


    // Helper method to create the kicker

    public static void kicker(NavuNode ncsRoot,
                              String name,
                              String monitor,
                              String kickNode) throws NavuException {

        NavuContainer kicker = ncsRoot.getParent()
            .container("http://tail-f.com/ns/kicker")
            .container("kickers")
            .list("data-kicker").safeCreate(name);

        kicker.leaf("monitor").set(monitor);
        kicker.leaf("kick-node").set(new ConfBuf(kickNode));
        kicker.leaf("action-name").set("reactive-re-deploy");
    }




    // This type of help structures are usually created and deleted
    // in a PRE_MODIFICATION callback, sometimes we want them to
    // contain operational data, actually the entire structure can
    // be operational. Thus - we usually don't want the administrative
    // helper structure to be part of the diff-set

    @ServiceCallback(servicePoint = "kickerspnt",
                     callType = ServiceCBType.PRE_MODIFICATION)
    public Properties preModification(ServiceContext context,
                                      ServiceOperationType operation,
                                      ConfPath path,
                                      Properties opaque)
        throws DpCallbackException {
        LOGGER.info("KickerTest: PREcreate("+path+") " + operation);
        try {
            ConfKey pppIface = (ConfKey) path.getKP()[0];

            NavuList dataList = context.getRootNode().
                getParent().
                container(kickerService.hash).
                list("ppp-accounting-data");

            if (ServiceOperationType.DELETE.equals(operation)) {
                dataList.delete(pppIface);
            }

            else if ((ServiceOperationType.CREATE.equals(operation)
                      || ServiceOperationType.UPDATE.equals(operation))) {

                dataList.safeCreate(pppIface);
            }
        }
        catch (Exception e) {
            LOGGER.error("", e);
        }
        return opaque;
    }





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
    @ServiceCallback(servicePoint = "kickerspnt",
        callType = ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode root,
                             Properties opaque) throws DpCallbackException {

        try {
            // check if it is reasonable to assume that devices
            // initially has been sync-from:ed
            // This is example code only, production services don't do this

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
            String pppIface = service.leaf("interface").valueAsString();

            NavuContainer dataEntry = service.getParent().getParent().
                list("ppp-accounting-data").elem(pppIface);
            if (! dataEntry.leaf("accounting").exists()) {
                // Create kicker and return, if the "accounting" leaf exist
                // we don't create the kicker. This means we'll only be
                // redeployed when the "accounting" leaf is first created,
                // subsequent modifications of the leaf will not trigger
                // a redploy.

                kicker(
                    root,
                    String.format("ppp-accounting-%s", pppIface),
                    String.format(
                        "/ppp-accounting-data[interface='%s']/accounting",
                                  pppIface),
                    String.format("/ppp-accounting[interface='%s']", pppIface));
                return opaque;
            }
            String accounting = dataEntry.leaf("accounting").valueAsString();

            //Get the list of all managed devices.
            NavuList managedDevices = root.container("devices").list("device");

            for(NavuContainer dev : managedDevices.elements()){

                NavuList ifList = dev.container("config").
                    namespace("r").container("sys").namespace("ex").
                    container("interfaces").list("serial");

                // Set the accounting leaf in the ppp interface on the
                // router

                if (ifList.elem(pppIface) != null)
                    ifList.elem(pppIface).container("ppp").
                        leaf("accounting").sharedSet(accounting);
                else
                    LOGGER.info("No interface called " + pppIface +
                                " on router: " +
                                dev.leaf("name").valueAsString());
            }
        } catch (Exception e) {
            throw new DpCallbackException("", e);
        }
        return opaque;
    }
}
