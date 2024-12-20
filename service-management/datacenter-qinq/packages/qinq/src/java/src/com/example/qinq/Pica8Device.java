package com.example.qinq;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfValue;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuList;

public class Pica8Device extends Device {

    public Pica8Device(NavuContainer deviceContainer) {
        super(deviceContainer);
    }

    private NavuContainer getInterface(String i) throws ConfException {
        String custInt = i;
        String custIntType = custInt.split("-")[0];
        String listName = "";
        if (custIntType.equals("ge")) {
            listName = "gigabit-ethernet";
        } else if (custIntType.contains("ae")){
            listName = "aggregate-ethernet";
        }

        NavuContainer confCont = deviceContainer.
                    container("config");
        NavuContainer intCont = confCont.namespace("p8").container("interface");

        NavuList interfaceList =
                intCont.list(listName);
        return this.getInstanceIfExists(interfaceList, custInt,
           "Invalid interface "+custInt + ": "+listName+": "+
        custInt.split("-")[0]+": "+custIntType);
    }

    private void configureVLAN(NavuLeaf sVLAN) throws ConfException {
        deviceContainer.
            container("config").
            namespace("p8").container("vlans").
            list("vlan-id").sharedCreate(sVLAN.valueAsString());
    }

    @Override
    public void configureEdgeSwitch(NavuContainer service, NavuContainer sw)
        throws ConfException {

        NavuLeaf sVLAN = service.leaf("s-vlan");
        this.configureVLAN(sVLAN);

        NavuList edgeInterfaceList = sw.list("edge-interface");

        //configure edge interfaces
        for (NavuContainer edgeInt: edgeInterfaceList.elements()){
            NavuContainer edgeInterface = this.
                                    getInterface(edgeInt.leaf("interface").
                                                          valueAsString());
            NavuContainer switchCont = edgeInterface.container("family").
                            container("ethernet-switching");

            switchCont.leaf("port-mode").sharedSet("trunk");
            switchCont.container("vlan").
                        list("members").sharedCreate(sVLAN.valueAsString());

            switchCont.container("qinq").leaf("mode").sharedSet("internal");
            NavuContainer ingressCont = switchCont.container("qinq").
                                        container("ingress");

            NavuLeafList cVlans = edgeInt.leafList("c-vlan");

            for(ConfValue vlan : cVlans){
                NavuContainer l = ingressCont.list("cvid").
                    sharedCreate(vlan.toString());
                l.list("qvid").sharedCreate(sVLAN.value().toString());
            }

            NavuContainer egressCont = switchCont.container("qinq").
                container("egress");

            for(ConfValue vlan : cVlans){
                NavuContainer l = egressCont.list("cvid").
                    sharedCreate(vlan.toString());
                NavuContainer qvidList = l.list("qvid").
                    sharedCreate(sVLAN.value().toString());
                qvidList.leaf("tag-action").sharedSet("one");
                qvidList.leaf("set-qvid").sharedSet("0");
            }
        }

        // configure core interface

        NavuLeafList trunkInterfaces = sw.leafList("trunk-interface");

        for(ConfValue i : trunkInterfaces){
            NavuContainer trunkInterface = this.getInterface(i.toString());
            NavuContainer switchCont = trunkInterface.container("family").
                container("ethernet-switching");
            switchCont.leaf("port-mode").sharedSet("trunk");
            switchCont.container("vlan").
                        list("members").sharedCreate(sVLAN.valueAsString());
        }
    }

    @Override
    public void configureCoreSwitch(NavuContainer service, NavuContainer sw)
        throws ConfException {
    }
}
