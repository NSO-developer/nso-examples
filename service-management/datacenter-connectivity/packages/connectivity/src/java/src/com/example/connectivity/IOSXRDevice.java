package com.example.connectivity;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfNamespace;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuNode;

import java.io.IOException;

public class IOSXRDevice extends Device{

    private final String nsName = "http://tail-f.com/ned/cisco-ios-xr";

    public IOSXRDevice(NavuContainer deviceContainer) {
        super(deviceContainer);
    }

    @Override
    public void configureAccessSwitch(NavuNode service,
            NavuContainer endpoint,
            NavuContainer role) throws ConfException {
    }

    @Override
    public void configureAggregationSwitch(NavuNode service,
            NavuContainer role,
            String hsrpAddress,
            String bviAddress) throws ConfException {
    }

    @Override
    public void configureCoreLayer(NavuNode service, NavuContainer role,
            String lb, String endpointName) throws ConfException, IOException {
        String vlan = service.leaf("vlan").valueAsString();

        NavuLeafList trunkInterfaces =
            role.container("settings").leafList("trunk-ports");

        for(ConfValue i : trunkInterfaces){
            String intName = i.toString().split("(?<=[\\w&&\\D])(?=\\d)")[0];
            String intKey  = i.toString().split("(?<=[\\w&&\\D])(?=\\d)")[1];

            NavuContainer vlanInterface = deviceContainer.
                container("config").
                namespace(nsName).
                container("interface").
                container(intName+"-subinterface").
                list(intName).sharedCreate(intKey+"."+vlan);

            this.configurePseudowire(vlanInterface,
                    service, lb,
                    intKey+"."+vlan+"-"+endpointName);

        }
    }

    private void configurePseudowire(NavuContainer i, NavuNode service,
            String lb, String endpointName)
            throws NavuException, ConfException, IOException {
        String vlan = service.leaf("vlan").valueAsString();

        String evcID = service.leaf("name").valueAsString();
        String pwID = vlan;

        String ifName = i.getParent().getName()+i.leaf("id").valueAsString();

        i.leaf("description").sharedSet("pseudowire for - " +
                service.leaf("name").valueAsString());
        i.container("encapsulation").sharedCreate().
            container("dot1q").leaf("vlan-id").sharedSet(vlan);

        //        NavuLeaf evcID = evc.leaf("evc-id");
        //        NavuLeaf ceVlan = evc.leaf("ce-vlan");
        //        String ifName = "TenGigE"+
        //            ifContainer.leaf("id").valueAsString()+
        //            "."+ceVlan.valueAsString();

        NavuContainer l2VPN = deviceContainer.container("config").
            namespace(nsName).
            container("l2vpn").
            sharedCreate();

        l2VPN.list("pw-class").sharedCreate(evcID).
            container("encapsulation").
            container("mpls").
            container("transport-mode").
            leaf("ethernet").sharedCreate();

        NavuContainer p2p = l2VPN.container("xconnect").sharedCreate().
            list("group").sharedCreate(evcID).
            list("p2p").sharedCreate(evcID+"-"+endpointName);

        p2p.list("interface").sharedCreate(ifName);
        NavuContainer neighbor = p2p.list("neighbor").
            sharedCreate(new String[]{lb,pwID});
        neighbor.leaf("ip-version").sharedSet("ipv4");
        neighbor.leaf("pw-class").sharedSet(evcID);

    }

    public String getLoopbackAddress() throws ConfException, IOException {
        return deviceContainer.
            container("config").
            namespace(nsName).
            container("interface").
            list("Loopback").elem("0").container("ipv4").
            container("address").leaf("ip").valueAsString();
    }
}
