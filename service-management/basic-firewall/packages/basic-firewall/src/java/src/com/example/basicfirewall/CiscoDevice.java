package com.example.basicfirewall;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIPv4Prefix;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfUInt16;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuList;

public class CiscoDevice extends Device{


   public CiscoDevice(NavuContainer deviceContainer) {
       super(deviceContainer);
   }

   public void attachACL(NavuContainer service, NavuList intList)
       throws ConfException{

       String interfaceName = "Loopback";
       String interfaceNumber = "0";

       if (intList.containsNode(this.deviceContainer.
                                leaf("name").valueAsString())) {
           interfaceName = intList.elem(this.deviceContainer.
                                 leaf("name").valueAsString()).
                                 leaf("interface-name").valueAsString();
           interfaceNumber = intList.elem(this.deviceContainer.
                                 leaf("name").valueAsString()).
                                 leaf("interface-number").valueAsString();
       }

       NavuContainer interfaceContainer =
               NavuUtil.getInstanceIfExists(this.deviceContainer.
                                       container("config").
                                       namespace("ios").
                                       container("interface").
                                       list(interfaceName),
                                       interfaceNumber,
                                       "Invalid interface: "+
                                       interfaceName + " " +
                                           interfaceNumber);

       ConfKey key = service.getKey();
       interfaceContainer.container("ip").
                       container("access-group").
                       leaf("access-list").
                       sharedSet(key.elementAt(0).toString());

       ConfEnumeration dirEnum = ConfEnumeration.
                               getEnumByLabel(interfaceContainer.
                               container("ip").
                               container("access-group").
                               leaf("direction").
                               getKeyPath(),"in");

       interfaceContainer.container("ip").
                       container("access-group").
                       leaf("direction").sharedSet(dirEnum);
   }

   public void createACL(NavuContainer service)
       throws ConfException{

       NavuContainer sContainer = (NavuContainer) service;
       ConfKey key = sContainer.getKey();
       String fwName = key.elementAt(0).toString();
       NavuList rules = sContainer.list("rule");

       NavuContainer aclContainer  =
           this.deviceContainer.container("config").
           namespace("ios").container("ip").
           container("access-list").
           container("extended").
           list("ext-named-acl").sharedCreate(fwName);

       for(NavuContainer ruleContainer : rules.elements()){
           ConfIPv4Prefix sourcePrefix = (ConfIPv4Prefix)ruleContainer.
                                           leaf("source-ip-prefix").value();
           ConfUInt16 sourcePort = (ConfUInt16)ruleContainer.
                                           leaf("source-port").value();
           ConfEnumeration protocol = (ConfEnumeration)ruleContainer.
                                           leaf("protocol").value();
           String strProtocol = ConfEnumeration.
               getLabelByEnum(ruleContainer.
               leaf("protocol").getKeyPath(), protocol);

           String mask = "";
           try {
               mask = prefixToWildcardMask(sourcePrefix.
                      getMaskLength());
           } catch (UnknownHostException e) {
               e.printStackTrace();
               throw new ConfException("Unknown prefix", e);
           }
           String rule = "permit " + strProtocol + " " +
               sourcePrefix.getAddress().getHostAddress() +
               " "+mask+" any eq " + sourcePort.toString();

           if (ruleContainer.leaf("log").exists()) {
               rule = rule + " log";
           }
           aclContainer.list("ext-access-list-rule").sharedCreate(rule);
       }
   }

   private String prefixToWildcardMask(int prefix)
       throws UnknownHostException{

       int mask = 0xffffffff << (32 - prefix);
       int value = mask;
       byte[] bytes = new byte[]{
           (byte)(~(value >>> 24) & 0xFF),
           (byte)( ~(value >> 16 & 0xff) & 0xFF),
           (byte)( ~(value >> 8 & 0xff) & 0xFF),
           (byte)( ~(value & 0xff) & 0xFF ) };

       InetAddress netAddr = InetAddress.getByAddress(bytes);
       return netAddr.getHostAddress();
   }
}
