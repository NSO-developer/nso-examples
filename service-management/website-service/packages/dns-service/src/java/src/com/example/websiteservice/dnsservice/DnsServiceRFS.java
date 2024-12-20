package com.example.websiteservice.dnsservice;


// generated symbols from the device models
import java.util.Properties;

import com.example.websiteservice.dnsservice.namespaces.dns;
import com.example.websiteservice.lb.namespaces.lb;
import com.example.websiteservice.webserver.namespaces.sys;
import com.tailf.conf.ConfIPv4;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfException;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.ns.Ncs;
import java.io.IOException;

/**
 * This class implement the create logic for our DNS Service.
 *
 * The example will show how to navigate with NAVU (Navigation Utility).
 *
 * When we set values to our dns service (example from the NCS CLI):
 *
 * % set services dns nameserver 1.2.3.4 search acme.com
 *
 * % commit
 *
 * Then this create() code will run i.e the create() method will be
 * invoked by NCS.
 *
 * This example will map values from our abstract service model to our
 * more concreate device layer. It will get the namserver,search values and
 * add them to our managed devices (lb,www{0-2}).
 *
 */
public class DnsServiceRFS {

    /* (non-Javadoc)
     * @see
     * com.tailf.examples.website.
     *     iNcsService#create(com.tailf.navu.NavuContainer,
     * com.tailf.navu.NavuContainer)
     */



    /**
     *  Create callback method.
     *
     *  @param service - The read/write NavuContainer reference
     *                   representing the service node.  (keypath
     *                   /ncs:ncs/services/dns)
     *  @param ncs     - The NavuContainer reference to the ncs root.
     *
     *  @param opaque - Parameter contains a serializable object.
     *                   This object may be used to transfer
     *                   additional information between consecutive
     *                   calls to the create callback.  It is always
     *                   null in the first call. I.e. when the service
     *                   is first created.
     */
    @ServiceCallback(servicePoint="dnsservice", callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode root,
                             Properties opaque)
        throws DpCallbackException, ConfException, IOException {

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
            throw new DpCallbackException("Not able check devices", e);
        }

        try {
        //  Get the container at keypath:
        //  /ncs:ncs/services/dns:dns
        NavuNode dnsContainer = service;

        //Now get the nameserver leaf
        NavuLeaf dnsServerLeaf  = dnsContainer.leaf(dns._nameserver_);

        //..and the dns search leaf
        NavuLeaf dnsSearchLeaf = dnsContainer.leaf(dns._search_);


        //..and corresponding values:

        //IP address of the nameserver
        ConfIPv4 dnsServerIp = (ConfIPv4)dnsServerLeaf.value();

        //..and the search domain
        ConfValue searchDomain = dnsSearchLeaf.value();

        //Get the list of all managed devices.
        NavuList managedDevices = (NavuList)
            root.container(Ncs._devices_).list(Ncs._device_);

        //Just set this to a initial value.
        devType dt = devType.neither;

        //Now we will need to iterate over all of our managed
        //devices (lb,www{1-3} and set dns server and search
        //domain for all of them
        for(NavuContainer deviceContainer : managedDevices.elements()){

            //Is the device a loadbalancer?
            if((dt = type(deviceContainer)) == devType.loadBalancer){
                NavuContainer resolverCont =
                    deviceContainer.container(Ncs._config_).namespace(lb.id)
                    .container(lb._lbConfig_).container(lb._system_)
                    .container(lb._resolver_);

                NavuLeaf nameServerLeaf =  resolverCont.leaf(
                    lb._nameserver_);
                NavuLeaf searchLeaf =      resolverCont.leaf(lb._search_);

                //Here we write to our leaf
                nameServerLeaf.sharedSet(dnsServerIp);
                searchLeaf.sharedSet(searchDomain);

                //Or are we a webserver.?
            }else if (dt == devType.webServer) {
                NavuLeaf devName = deviceContainer.leaf(Ncs._name_);
                String nedIdStr =
                        context.getNedIdByDeviceName(devName.valueAsString());

                NavuList serverlist =
                    deviceContainer.container(Ncs._config_).namespace(nedIdStr)
                    .container(sys._host_settings_)
                    .container(sys._dns_).list(sys._server_);


                //Here we add a new dns server list entry "0" is
                //the key of the list entry if it exists then just
                //update the entry with the new dns server ip
                //address.
                NavuContainer serverlistElem =
                    serverlist.sharedCreate(new String[]{"0"});
                serverlistElem.leaf(sys._address_).sharedSet(dnsServerIp);

                NavuList searchList =
                    deviceContainer.container(Ncs._config_).namespace(nedIdStr)
                    .container(sys._host_settings_)
                    .container(sys._dns_).list(sys._search_);

                //Here we add a new search list entry "0" is the
                //key of the list entry if it exists then just
                //update the entry with the new search entry
                NavuContainer searchlistElem =
                    searchList.sharedCreate(new String[]{"0"});
                searchlistElem.leaf(sys._domain_).sharedSet(searchDomain);

            }
        }
        } catch (NavuException e) {
            throw new DpCallbackException("Not able to setup dns service", e);
        }
        return opaque;
    }


    static private enum devType {
        webServer, loadBalancer, neither;
    };

    /**
     *  Figure out what kind of device are we.
     *
     *  We figure that out by walking through the loaded modules for
     *  this device
     *  @param managedDevice - Element that represent
     *                         /devices/device/{x} where the key x
     *                         is either lb0,www0,www1 or www2
     *
     *  @return devType indicating the device type of the supplied
     *  managed device.
     *
     */

    private devType type(NavuContainer managedDevice) throws NavuException{

        //default value.
        devType type = devType.neither;

        //Retrive a representation of a list of /devices/device/{x}/module
        NavuList modules = managedDevice.list(Ncs._module_);

        for(NavuContainer cont : modules.elements()){
            NavuLeaf leaf = cont.leaf(Ncs._name_);

            if(leaf.value().toString().equals("lb")){
                type = devType.loadBalancer;
            }else if(leaf.value().toString().equals("webserver")){
                type = devType.webServer;
            }
        }
        return type;
    }
}

