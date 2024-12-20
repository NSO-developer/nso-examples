package com.example.qinq;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfUInt16;
import com.tailf.conf.ConfValue;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuList;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CiscoDevice extends Device{

   private static Logger LOGGER = LogManager.getLogger(CiscoDevice.class);

   public CiscoDevice(NavuContainer deviceContainer) {
       super(deviceContainer);
   }


   private NavuContainer getInterface(String i) throws ConfException {

       String custInt = i;
       String custIntName = custInt.split(" ")[0];
       String custIntKey = custInt.split(" ")[1];

       NavuList interfaceList = deviceContainer.
           container("config").
           namespace("ios").container("interface").
           list(custIntName);

       return this.getInstanceIfExists(interfaceList, custIntKey,
                              "Invalid interface "+custInt);
   }

   private void configureVLAN(NavuLeaf sVLAN) throws ConfException {

       deviceContainer.
           container("config").
           namespace("ios").container("vlan").
           list("vlan-list").sharedCreate(sVLAN.valueAsString());
   }

   private void configureTrunkInterface(NavuLeafList trunkInt, String svlan)
       throws ConfException {

       for(ConfValue i : trunkInt){
           NavuContainer trunkInterface = this.getInterface(i.toString());

           trunkInterface.container("switchport").sharedCreate().
                           container("mode").
                           container("trunk").sharedCreate();

           ConfUInt16 newVal = new ConfUInt16(Integer.parseInt(svlan));

           trunkInterface.container("switchport").
                container("trunk").
                container("allowed").
                container("vlan").
                leafList("vlans").sharedCreate(newVal);
       }
   }


   @Override
   public void configureEdgeSwitch(NavuContainer service, NavuContainer sw)
       throws ConfException {

       NavuLeaf sVLAN = service.leaf("s-vlan");
       this.configureVLAN(sVLAN);

       NavuList edgeInterfaceList = sw.list("edge-interface");

       for (NavuContainer edgeInt: edgeInterfaceList.elements()){
           // FIXME: Add c-vlan info to the Cisco config
           NavuContainer custInterface = this.getInterface(
                                              edgeInt.leaf("interface").
                                                  valueAsString());

           custInterface.container("switchport").sharedCreate().
                           container("mode").
                           container("dot1q-tunnel").sharedCreate();
           custInterface.container("switchport").
                           container("access").
                           leaf("vlan").sharedSet(sVLAN.valueAsString());

           custInterface.container("spanning-tree").
               container("portfast").sharedCreate();

           custInterface.container("spanning-tree").
           container("bpduguard").
           leaf("enable").sharedCreate();
       }

       // Configure the trunk interface

       this.configureTrunkInterface(sw.leafList("trunk-interface"),
                                        sVLAN.valueAsString());

   }

   @Override
   public void configureCoreSwitch(NavuContainer service, NavuContainer sw)
       throws ConfException {

       NavuLeaf sVLAN = service.leaf("s-vlan");
       this.configureVLAN(sVLAN);

       this.configureTrunkInterface(sw.leafList("trunk-interface"),
                                    sVLAN.valueAsString());
   }
}
