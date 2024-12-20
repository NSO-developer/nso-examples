package com.example.syslog;


import java.util.ArrayList;
import java.util.Properties;

import com.tailf.conf.ConfPath;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.maapi.Maapi;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;

/**
 * This class implement the create logic for the Syslog Service.
 *
 * The example will show how to navigate with NAVU (Navigatinal Utillity).
 *
 * When we set values to our syslog service (example from the NCS CLI):
 *
 * % set services syslog slog server 2.3.4.5
 *
 * % commit
 *
 * Then this mapping logic will run i.e the create() method will be
 * invoked by NCS.
 *
 * This example takes the entire data structure in the service
 * and copies it to each managed device, after deleting the old settings
 */

public class SyslogServiceRFS {

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
    @ServiceCallback(servicePoint = "syslogspnt",
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

            // Get the list at keypath
            // /services/syslog/server

            NavuList serverList = service.list("server");
            ConfPath kp = new ConfPath(serverList.getKeyPath());
            System.out.println("kp = " + kp);

            //Now we will need to iterate over all of our managed
            //devices and copy the syslog settings

            //Get the list of all managed devices.
            NavuList managedDevices = root.container("devices").list("device");

            for(NavuContainer deviceContainer : managedDevices.elements()){

                NavuContainer sLog = deviceContainer.container("config").
                    namespace("r").container("sys").container("syslog");
                NavuList servers = sLog.list("server");

                // delete all previous syslog settings, these previous
                // settings will end up in the FASTMAP diffset, thus when we
                // delete this service, the previous syslog settings on the
                // device will be automatically restored (re-created)


                ArrayList<NavuContainer> cloned =
                    new ArrayList<NavuContainer>(servers.elements());
                for (NavuContainer c : cloned) {
                    c.delete();
                }


                ConfPath dtPath = new ConfPath(servers.getKeyPath());
                Maapi m = sLog.context().getMaapi();
                int tHandle = sLog.context().getMaapiHandle();

                // Since the data model is identical, we can use copyTree
                // had the data models deiffered, we would have had to
                // traverse syslogContainer and copy field by field
                // we use sharedCreate here b/c we're in fastmap code
                m.copyTree(tHandle, true, kp, dtPath);
            }
        } catch (Exception e) {
            throw new DpCallbackException("", e);
        }
        return opaque;
    }

}
