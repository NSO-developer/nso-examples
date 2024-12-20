package com.example.connectivity;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfUInt16;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfNamespace;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;

import java.io.IOException;

public class IOSDevice extends Device{

   private final String nsName = "urn:ios";

   public IOSDevice(NavuContainer deviceContainer) {
       super(deviceContainer);
   }

   public void configureAccessSwitch(NavuNode service,
                                     NavuContainer endpoint,
                                     NavuContainer role)
       throws ConfException, IOException {

       String vlan = service.leaf("vlan").valueAsString();
       this.configureVlan(vlan);

       NavuLeafList trunkInterfaces =
           role.container("settings").leafList("trunk-ports");

       for(ConfValue i : trunkInterfaces){
           NavuContainer trunkInterface = this.getInterface(i.toString());
           this.configureTrunkPort(trunkInterface, vlan);
       }

       NavuContainer aPort = this.
           getInterface(endpoint.leaf("interface").valueAsString());

       if (endpoint.container("endpoint-settings").
               leaf("connect-multiple-vlans").exists()) {
           this.configureTrunkPort(aPort, vlan);
       }
       else {
           aPort.leaf("description").sharedSet(service.leaf("name").
                   valueAsString() + " - endpoint interface");
           aPort.container("switchport").sharedCreate().
           container("mode").
           container("access").sharedCreate();

           aPort.container("switchport").sharedCreate().
               container("access").
               leaf("vlan").sharedSet(vlan);

           aPort.container("switchport").
               leaf("nonegotiate").sharedCreate();

           aPort.container("spanning-tree").
               container("portfast").sharedCreate();

           aPort.container("spanning-tree").
               container("bpduguard").
               leaf("enable").sharedCreate();

           aPort.container("spanning-tree").
               leaf("guard").sharedSet("root");
       }

   }

   private void configureTrunkPort(NavuContainer trunkI, String vlan)
           throws NavuException {
       trunkI.container("switchport").sharedCreate().
       container("mode").
       container("trunk").sharedCreate();

       ConfUInt16 newVal = new ConfUInt16(Integer.parseInt(vlan));

       trunkI.container("switchport").
           container("trunk").
           container("allowed").
           container("vlan").
           leafList("vlans").sharedCreate(newVal);
   }

   private void configureVlan(String vlan)
           throws NavuException, ConfException, IOException {
       NavuList vlanList =
           deviceContainer.
           container("config").
           namespace(nsName).
           container("vlan").
           list("vlan-list");
       vlanList.sharedCreate(vlan);
   }

   private NavuContainer getInterface(String accessInt)
       throws ConfException, IOException {


       String[] split = Device.splitInterface(accessInt);
       String accessIntName = split[0];
       String accessIntKey = split[1];

       NavuList interfaceList = deviceContainer.
           container("config").
           namespace(nsName).
           container("interface").
           list(accessIntName);

       return this.getInstanceIfExists(interfaceList, accessIntKey,
                              "Invalid interface "+accessInt);
   }

   @Override
   public void configureAggregationSwitch(NavuNode service,
           NavuContainer role,
           String hsrpAddress,
           String bviAddress) throws ConfException {
   }

   @Override
   public String getLoopbackAddress() throws ConfException {
       return null;
   }

   @Override
   public void configureCoreLayer(NavuNode service, NavuContainer role,
           String lb, String endpointName) throws ConfException {
   }
}
