package com.example.connectivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

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


public class connectivityRFS {


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
     * @throws ConfException
     */

    @ServiceCallback(servicePoint="connectivity-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode root,
                             Properties opaque)
                             throws ConfException, IOException {
        String servicePath = null;
        ArrayList<String> dcList = new ArrayList<String>();
        try {

            NavuList endpoints = service.list("endpoint");

            NavuContainer topology = root.getParent().
                container("http://com/example/connectivity").
                container("topology");

            String hsrpAddress = getNextIPV4Address(service.
                                                 leaf("ip-network").
                                                 valueAsString());
            String bviAddress = getNextIPV4Address(hsrpAddress);

            // iterate over the endpoints configured in the service
            for(NavuContainer endpoint : endpoints.elements()) {

                // get the role in the topology for this device
                NavuContainer role = this.getRole(topology,
                                     ((NavuLeaf)endpoint.leaf("device").
                                     deref().get(0)).valueAsString());
                // get the topology for the data center this device
                // lives in .
                NavuContainer dcTopology =
                    (NavuContainer) role.getParent().getParent();

                // save all the data centers we have endpoints in.
                if (!dcList.contains(dcTopology.leaf("id").valueAsString())) {
                    dcList.add(dcTopology.leaf("id").valueAsString());
                }
                // Configure the access switch.
                if (role != null) {
                    NavuContainer deviceContainer =
                        getDeviceContainer(endpoint.leaf("device"));

                    Device device = Device.getDevice(deviceContainer);
                    try {
                        device.configureAccessSwitch(service,endpoint, role);
                    }
                    catch (NullPointerException e) {
                        throw new DpCallbackException(
                            "No data on device, did you sync from network?",e);
                    }
                }
            }


            // configure aggregation and core devices if needed in
            //    affected datacenters
            for (String dc : dcList) {
                for (NavuContainer d : topology.list("datacenter").
                    elem(dc).
                    list("device").elements() ) {

                    // configure the access switches in all data centers
                    if ( "aggregation-layer".equals(d.leaf("role").
                                                    valueAsString()) ){
                        NavuContainer deviceContainer =
                                    getDeviceContainer(d.leaf("device"));

                        Device device = Device.getDevice(deviceContainer);
                        try {
                            device.configureAggregationSwitch(service,d,
                                                              hsrpAddress,
                                                              bviAddress);
                            bviAddress = getNextIPV4Address(bviAddress);
                        }
                        catch (NullPointerException e) {
                            throw new DpCallbackException(
                                "No data on device, did you sync from network?"
                                ,e);
                        }
                    }
                    // Check if this is a core router
                    // and we have more then one data center involved
                    if ( "core-layer".equals(d.leaf("role").
                                             valueAsString()) &&
                                             (dcList.size() > 1)
                                             ){
                        NavuContainer deviceContainer =
                            getDeviceContainer(d.leaf("device"));
                        Device device = Device.getDevice(deviceContainer);

                        // find all core devices in all other data centers
                        // and configured pseudo wires towards them.
                        for (String dc2 : dcList ) {
                            if (dc2 != dc) {
                                for (NavuContainer d2 :
                                    topology.list("datacenter").
                                    elem(dc2).
                                    list("device").elements() ) {
                                        if ( "core-layer".
                                            equals(d2.leaf("role").
                                         valueAsString()) ){
                                            NavuContainer xcDevice =
                                getDeviceContainer(d2.leaf("device"));
                                Device xcdev = Device.getDevice(xcDevice);
                                try {
                                    device.configureCoreLayer(service,d,
                                               xcdev.getLoopbackAddress(),
                                               d2.leaf("device").
                                               valueAsString());
                                }
                                catch (NullPointerException e) {
                                    throw new DpCallbackException(
                           "No data on device, did you sync from network?", e);
                                }
                                        }
                                }
                            }
                        }
                    }
                }
            }


        } catch (NavuException e) {
            throw new DpCallbackException("Cannot create service " +
                                          servicePath, e);
        }
        return opaque;
    }

    private NavuContainer getRole(NavuContainer topology, String deviceName)
        throws NavuException {
        for(NavuContainer dc : topology.list("datacenter").elements()) {
            if (dc.list("device").containsNode(deviceName)) {
                return dc.list("device").elem(deviceName);
            }
        }
        return null;
    }

    private NavuContainer getDeviceContainer(NavuLeaf device)
                        throws NavuException {
        NavuNode n = device.deref().get(0);
        return (NavuContainer) ((NavuLeaf) n).getParent();
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
}

