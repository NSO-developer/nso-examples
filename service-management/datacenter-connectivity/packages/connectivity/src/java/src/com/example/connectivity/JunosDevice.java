package com.example.connectivity;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfValue;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuNode;

import java.io.IOException;

public class JunosDevice extends Device{

    private final String nsName = "http://xml.juniper.net/xnm/1.1/xnm";

    public JunosDevice(NavuContainer deviceContainer) {
        super(deviceContainer);
    }

    public void configureAccessSwitch(NavuNode service,
            NavuContainer endpoint,
            NavuContainer role)
        throws ConfException {
    }

    @Override
    public void configureAggregationSwitch(NavuNode service,
            NavuContainer role,
            String hsrpAddress,
            String bviAddress)
    throws ConfException {
    }

    @Override
    public String
    getLoopbackAddress() throws ConfException, IOException {
        String s = deviceContainer.
            container("config").
            namespace(nsName).
            container("configuration").
            container("interfaces").
            list("interface").elem("lo0").
            list("unit").elem("0").
            container("family").
            container("inet").
            list("address").iterator().next().
            leaf("name").valueAsString();
        return s.substring(0, s.indexOf("/"));
    }

    @Override
    public void
    configureCoreLayer(NavuNode service, NavuContainer role, String lb,
            String endpointName) throws ConfException {
        String vlan = service.leaf("vlan").valueAsString();

        NavuLeafList trunkInterfaces =
            role.container("settings").leafList("trunk-ports");

        for(ConfValue i : trunkInterfaces){
            NavuContainer tInt = deviceContainer.container("config").
                container("configuration").
                container("interfaces").
                list("interface").elem(i.toString());
            tInt.leaf("encapsulation").sharedSet("flexible-ethernet-services");
            tInt.leaf("flexible-vlan-tagging").create();
        }
    }
}
