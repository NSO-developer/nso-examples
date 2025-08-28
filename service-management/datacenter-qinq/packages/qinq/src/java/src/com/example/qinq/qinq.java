package com.example.qinq;

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


public class qinq {




    /**
     *  Create callback method.
     *
     *  @param service - The read/write NavuContainer reference
     *                   representing the service node.  (keypath
     *                   /ncs:services//ncs:service{key} where
     *                   key is your newly created service name)
     *  @param ncs     - The NavuContainer reference to the ncs root.
     *
     *  @param opaque -  Parameter contains a serializable object.
     *                   This object may be used to transfer
     *                   additional information between consecutive
     *                   calls to the create callback.  It is always
     *                   null in the first call. I.e. when the service
     *                   is first created.
     */

     @ServiceCallback(servicePoint="qinqservice",
                      callType=ServiceCBType.CREATE)
     public Properties create(ServiceContext context,
                              NavuNode service,
                              NavuNode ncs,
                              Properties opaque)
                              throws DpCallbackException {

         try {
             // Get the container at keypath
             // /services/service{key}/type/ntp:ntp

             NavuNode sContainer = service;

             NavuList edgeSwitches = sContainer.list("edge-switch");
             NavuList coreSwitches = sContainer.list("core-switch");

             // iterate through all edge switches
             for(NavuContainer switchContainer : edgeSwitches.elements()){

                 NavuLeaf deviceLeaf = switchContainer.leaf("switch");
                 List<NavuNode> nList = deviceLeaf.deref();
                 NavuNode n = nList.get(0);
                 NavuContainer deviceContainer =
                          (NavuContainer) ((NavuLeaf) n).getParent();

                 Device device = Device.getDevice(deviceContainer);
                 try {
                     device.configureEdgeSwitch((NavuContainer)sContainer,
                                                switchContainer);
                 } catch (NullPointerException e) {
                     throw new DpCallbackException(
                    "No data on device, did you sync from network?");
                 }
             }   //

             //  iterate through all core switches
             for(NavuContainer switchContainer : coreSwitches.elements()){

                 NavuLeaf deviceLeaf = switchContainer.leaf("switch");
                 List<NavuNode> nList = deviceLeaf.deref();
                 NavuNode n = nList.get(0);
                 NavuContainer deviceContainer =
                          (NavuContainer) ((NavuLeaf) n).getParent();

                 Device device = Device.getDevice(deviceContainer);
                 try {
                     device.configureCoreSwitch((NavuContainer)sContainer,
                                                switchContainer);
                 } catch (NullPointerException e) {
                     throw new DpCallbackException(
                         "No data on device, did you sync from network?", e);
                 }
             }
         } catch (Exception e) {
             throw new DpCallbackException(
                 "No data on device, did you sync from network?", e);
         }   //
         return null;
     }

}
