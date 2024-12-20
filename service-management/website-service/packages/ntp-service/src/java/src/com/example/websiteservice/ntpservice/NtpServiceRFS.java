package com.example.websiteservice.ntpservice;


// generated symbols from the device models
import java.util.Properties;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIPv4;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;

/**
 * This class implement the create logic for the NTP Service.
 *
 * The example will show how to navigate with NAVU (Navigation Utility).
 *
 * When we set values to our ntp service (example from the NCS CLI):
 *
 * % set services ntp myntp 2.3.4.5
 *
 * % commit
 *
 * Then this mapping logic will run i.e the create() method will be
 * invoked by NCS.
 *
 * This example will map values from our abstract service model to our
 * more concrete device layer. It will get the ip address from
 * service myntp and set it to all managed devices (lb,www{1-3}).
 */
public class NtpServiceRFS  {


    /**
     *  Create callback method.
     *
     *  @param service - The read/write NavuContainer reference
     *                   representing the service node.  (keypath
     *                   /ncs:services/ntp)
     *  @param ncs     - The NavuContainer reference to the ncs root.
     *
     *  @param opaque - Parameter contains a serializable object.
     *                   This object may be used to transfer
     *                   additional information between consecutive
     *                   calls to the create callback.  It is always
     *                   null in the first call. I.e. when the service
     *                   is first created.
     */


    @ServiceCallback(servicePoint="ntpservice", callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode root,
                             Properties opaque)
        throws DpCallbackException {

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

        // service NavuContainer points to /ncs:services/ntp

        try {
        //Now get the ntp-server leaf.
        NavuLeaf ntpServerLeaf = service.leaf("ntp-server");

        //..and its value (wich is a ipv4-addrees )
        ConfIPv4 ntpServerIp = (ConfIPv4)ntpServerLeaf.value();

        //Get the list of all managed devices.
        NavuList managedDevices = root.
            container("devices").list("device");

        //Just set this to a initial value.
        devType dt = devType.neither;

        //Now we will need to iterate over all of our managed
        //devices (lb,www{0-2} and set ntp server
        for(NavuContainer deviceContainer : managedDevices.elements()){

            //Is the device a loadbalancer?
            if ((dt = type(deviceContainer)) == devType.loadBalancer){

                NavuLeaf changeNtpServerLeaf  =
                    deviceContainer.container("config").
                    namespace("lb").container("lbConfig").
                    container("system").
                    leaf("ntp-server");

                changeNtpServerLeaf.sharedSet(ntpServerIp);

                //Or are we a webserver.?
            } else if (dt == devType.webServer) {

                NavuList serverlist = deviceContainer.
                    container("config").
                    namespace("sys").container("host-settings").
                    container("ntp").list("server");

                serverlist.sharedCreate(ntpServerIp.toString());
            }


        }
        } catch (ConfException e) {
            throw new DpCallbackException("Not able to setup ntp service", e);
        }
        return opaque;
    }

    static private enum devType {
        webServer, loadBalancer, neither;
    };

    /**
     *
     * Figure out what kind of device are we.
     *
     * We figure that out by walking through the loaded modules for
     * this device
     *
     * @param managedDevice - Element that represent /devices/device{x}
     *                        where the key x is either lb0,www0,www1 or www2.
     *
     * @return devType indicating the device type of the supplied
     * managed device.
     */

    private devType type(NavuContainer managedDevice) throws NavuException{

        //default value.
        devType type = devType.neither;

        //Retrive a representation of a list of /devices/device/{x}/module
        NavuList modules = managedDevice.list("module");

        for(NavuContainer cont : modules.elements()){
            NavuLeaf leaf = cont.leaf("name");

            if(leaf.value().toString().equals("lb")){
                type = devType.loadBalancer;
            }else if(leaf.value().toString().equals("webserver")){
                type = devType.webServer;
            }
        }
        return type;
    }

}
