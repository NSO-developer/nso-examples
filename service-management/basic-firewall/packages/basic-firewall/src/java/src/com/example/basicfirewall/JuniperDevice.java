package com.example.basicfirewall;

import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIPv4Prefix;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfUInt16;
import com.tailf.conf.ConfUInt64;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuList;

public class JuniperDevice extends Device {

    public JuniperDevice(NavuContainer deviceContainer) {
        super(deviceContainer);
    }

    public void attachACL(NavuContainer service, NavuList intList)
        throws ConfException{

        String interfaceName = "lo0";

        if (intList.containsNode(this.deviceContainer.
                                 leaf("name").valueAsString())) {
            interfaceName = intList.elem(this.deviceContainer.
                                    leaf("name").valueAsString()).
                                    leaf("interface-name").valueAsString();
            interfaceName = interfaceName +
                                    intList.elem(this.deviceContainer.
                                         leaf("name").valueAsString()).
                                         leaf("interface-number").
                                         valueAsString();
        }
        NavuContainer interfaceContainer =
            this.deviceContainer.container("config").
                namespace("junos").container("configuration").
                container("interfaces").
                list("interface").elem(interfaceName);

        NavuContainer filterContainer = interfaceContainer.list("unit").
                        elem(new ConfKey(new ConfUInt64(0))).
                        container("family").
                        container("inet").
                        container("filter").
                        container("input").sharedCreate();

        ConfKey key = service.getKey();
        filterContainer.leaf("filter-name").
                        sharedSet(key.elementAt(0).toString());

    }

    public void createACL(NavuContainer service)
        throws ConfException{

        NavuContainer sContainer = (NavuContainer) service;
        ConfKey key = sContainer.getKey();
        String fwName = key.elementAt(0).toString();
        NavuList rules = sContainer.list("rule");
        NavuContainer aclContainer  =
            deviceContainer.container("config").
            namespace("junos").container("configuration").
            container("firewall").
            list("filter").sharedCreate(fwName);

        int i = 0;
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

            NavuContainer termContainer = aclContainer.
                list("term").sharedCreate("term" + Integer.toString(i));

            termContainer.container("from").list("source-address").
                    sharedCreate(sourcePrefix.toString());
            termContainer.container("from").leaf("destination-port").
                    sharedSet(sourcePort.toString());
            termContainer.container("from").leaf("protocol").
                    sharedSet(strProtocol);
            termContainer.container("then").leaf("accept").sharedCreate();

            if (ruleContainer.leaf("log").exists()) {
                termContainer.container("then").leaf("log").sharedCreate();
            }
            i++;
        }
    }
}
