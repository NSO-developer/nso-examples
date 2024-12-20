package com.example.connectivity;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfNamespace;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuNode;

import java.io.IOException;

public class Force10Device extends Device{

   private final String nsName = "http://tail-f.com/ned/dell-ftos";

   public Force10Device(NavuContainer deviceContainer) {
       super(deviceContainer);
   }

   public void configureAccessSwitch(NavuNode service,
                                     NavuContainer endpoint,
                                     NavuContainer role)
       throws ConfException, IOException {

       String vlan = service.leaf("vlan").valueAsString();
       NavuContainer vInt = deviceContainer.container("config").
                           namespace(nsName).
                           container("interface").
                           list("Vlan").sharedCreate(vlan);
       vInt.leaf("description").sharedSet(service.leaf("name").
                          valueAsString() + " - endpoint interface");

       NavuLeafList trunkInterfaces =
           role.container("settings").leafList("trunk-ports");

       for(ConfValue i : trunkInterfaces){
           vInt.list("tagged").sharedCreate(i);
       }
       if (endpoint.container("endpoint-settings").
               leaf("connect-multiple-vlans").exists()) {
               vInt.list("tagged").
               sharedCreate(endpoint.leaf("interface").valueAsString());
       }
       else {
           vInt.list("untagged").
           sharedCreate(endpoint.leaf("interface").valueAsString());
       }
   }

   @Override
   public void configureAggregationSwitch(NavuNode service,
           NavuContainer role,
           String hsrpAddress,
           String bviAddress)
   throws ConfException {
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
