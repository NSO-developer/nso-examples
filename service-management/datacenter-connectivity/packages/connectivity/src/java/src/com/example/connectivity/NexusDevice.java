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

public class NexusDevice extends Device{

    private final String nsName = "http://tail-f.com/ned/cisco-nx";

    private String ospfArea = "";
    private String ospfProcess = "";

    public NexusDevice(NavuContainer deviceContainer) {
        super(deviceContainer);
    }

    public void configureAccessSwitch(NavuNode service,
            NavuContainer endpoint,
            NavuContainer role) throws ConfException {
    }

    public void configureAggregationSwitch(NavuNode service,
                                           NavuContainer role,
                                           String hsrpAddress,
                                           String bviAddress)
        throws ConfException, IOException {
        String vlan = service.leaf("vlan").valueAsString();
        this.configureVlan(vlan);

        NavuContainer vlanList = deviceContainer.container("config").
            namespace(nsName).
            container("spanning-tree").
            container("vlan").
            list("vlan-list").sharedCreate(vlan);
        vlanList.leaf("priority").sharedSet("24576");

        NavuLeafList trunkInterfaces =
            role.container("settings").leafList("trunk-ports");

        for(ConfValue i : trunkInterfaces){
            NavuContainer trunkInterface = this.getInterface(i.toString());
            this.configureTrunkPort(trunkInterface, vlan);
        }

        trunkInterfaces =
            role.container("settings").leafList("core-trunk-ports");

        for(ConfValue i : trunkInterfaces){
            NavuContainer trunkInterface = this.getInterface(i.toString());
            this.configureTrunkPort(trunkInterface, vlan);
        }

        this.ospfArea = "81";
        this.ospfProcess = "1";
        this.configureVRF(service.leaf("name").valueAsString());
        this.configureOSPF(service.leaf("name").valueAsString());
        this.configureVlanInterface(service.leaf("name").valueAsString(), vlan,
                                    hsrpAddress, bviAddress);
    }

    private void configureVlanInterface(String vrf, String vlan,
                                        String hsrpAddress, String bviAddress)
        throws NavuException, ConfException, IOException {

        NavuContainer vlanInt = deviceContainer.
            container("config").
            namespace(nsName).
            container("interface").
            list("Vlan").sharedCreate(vlan);

        vlanInt.container("vrf").leaf("member").sharedSet(vrf);

        vlanInt.container("ip").container("address").leaf("ipaddr").
            sharedSet(bviAddress);
        vlanInt.container("ip").container("router").
            container("ospf").leaf("name").sharedSet(this.ospfProcess);
        vlanInt.container("ip").container("router").container("ospf").
            leaf("area").sharedSet(this.ospfArea);

        NavuContainer hsrp = vlanInt.container("hsrp").list("hsrp-list").
            sharedCreate(new String[] {"1","ipv4"});
        hsrp.list("ip").sharedCreate(hsrpAddress);
        hsrp.container("timers").leaf("hello-seconds").sharedSet("1");
        hsrp.container("timers").leaf("hold-seconds").sharedSet("3");
        hsrp.container("preempt").sharedCreate().container("delay").
            leaf("minimum").sharedSet("180");

        // FIXME should be 10 on of the aggregation
        // switches and 20 on the other
        hsrp.leaf("priority").sharedSet("10");

    }

    private void configureVRF(String vrf)
            throws NavuException, ConfException, IOException {
        deviceContainer.container("config").namespace(nsName).
            container("vrf").list("context").
            sharedCreate(vrf);
    }

    private void configureOSPF(String vrf)
        throws ConfException, IOException {

        NavuContainer ospf = deviceContainer.container("config").
            namespace(nsName).container("router").
            list("ospf").sharedCreate(this.ospfProcess);

        NavuContainer ospfVRF = ospf.list("vrf").sharedCreate(vrf);
        String lb = this.getLoopbackAddress();
        ospfVRF.leaf("router-id").sharedSet(lb.substring(0, lb.indexOf("/")));

        NavuContainer area = ospfVRF.list("area").
            sharedCreate(this.ospfArea);
        area.container("nssa").sharedCreate();
        area.container("authentication").sharedCreate().
            leaf("message-digest").sharedCreate();

        ospfVRF.container("timers").container("throttle").
            container("lsa").leaf("start").sharedSet("1000");
        ospfVRF.container("timers").container("throttle").
            container("lsa").leaf("hold").sharedSet("1000");
        ospfVRF.container("timers").container("throttle").
            container("spf").leaf("initial").sharedSet("10");
        ospfVRF.container("timers").container("throttle").
            container("spf").leaf("minimum").sharedSet("100");
        ospfVRF.container("timers").container("throttle").
            container("spf").leaf("maximum").sharedSet("5000");

    }

    private void configureTrunkPort(NavuContainer trunkI, String vlan)
        throws NavuException {
        trunkI.container("switchport").sharedCreate().
            leaf("mode").sharedSet("trunk");

        ConfUInt16 newVal = new ConfUInt16(Integer.parseInt(vlan));

        trunkI.container("switchport").
            container("trunk").
            container("allowed").
            container("vlan").
            leafList("ids").sharedCreate(newVal);
    }

    private void configureVlan(String vlan)
            throws NavuException, ConfException, IOException {
        deviceContainer.
            container("config").
            namespace(nsName).
            container("vlan").
            list("vlan-list").sharedCreate(vlan);
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

    public String getLoopbackAddress() throws ConfException, IOException {
            return deviceContainer.
                container("config").
                namespace(nsName).
                container("interface").
                list("loopback").elem("0").container("ip").
                container("address").leaf("ipaddr").valueAsString();

    }

    public void configureCoreLayer(NavuNode service, NavuContainer role,
            String lb, String endpointName) throws ConfException {
    }
}
