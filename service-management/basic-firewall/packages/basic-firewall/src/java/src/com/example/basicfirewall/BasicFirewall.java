package com.example.basicfirewall;

import java.util.List;
import java.util.Properties;

import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;


public class BasicFirewall  {


    @ServiceCallback(servicePoint="bfwservice", callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode root,
                             Properties opaque)
                             throws DpCallbackException {
        NavuContainer sContainer = (NavuContainer) service;
        try {
            NavuList interfaceList =
                root.container("services").
                container("properties").
                namespace("bfw").container("basic-firewall").
                list("firewall-interface");

            if (sContainer.leaf("firewall-device-group").exists() ) {
                NavuLeaf deviceGroups = sContainer.
                    leaf("firewall-device-group");
                List<NavuNode> deviceGroupsList = deviceGroups.deref();

                for(NavuNode deviceGroupNode: deviceGroupsList){
                    NavuLeaf devgrpleaf = (NavuLeaf) deviceGroupNode;
                    NavuContainer deviceDeviceGroup = (NavuContainer)
                        ((NavuLeaf) devgrpleaf).getParent();

                    NavuLeaf memberLeaf = deviceDeviceGroup.leaf("device-name");
                    List<NavuNode> memberList = memberLeaf.deref();

                    for(NavuNode memberNode: memberList) {
                        NavuLeaf deviceLeaf = (NavuLeaf) memberNode;
                        NavuContainer deviceContainer = (NavuContainer)
                            ((NavuLeaf) deviceLeaf).getParent();

                        Device device = Device.getDevice(deviceContainer);
                        try {
                            device.createACL(sContainer);
                            device.attachACL(sContainer, interfaceList);
                        }
                        catch (NullPointerException e) {
                            throw new DpCallbackException(
                              "No data on device, did you sync from network?");
                        }

                    }
                }
            }
        } catch (Exception e) {
            throw new DpCallbackException("Service: " +
                                          sContainer.getKeyPath() +
                                          " failed, " + e.getMessage(), e);

        }
        return opaque;
    }

}
