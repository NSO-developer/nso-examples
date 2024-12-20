package com.example.connectivity;

import com.tailf.conf.ConfException;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;

import java.io.IOException;


public abstract class Device {

    protected NavuContainer deviceContainer;

    protected Device(NavuContainer deviceContainer) {
        this.deviceContainer = deviceContainer;
    }

    abstract public void configureAccessSwitch(NavuNode service,
                                               NavuContainer endpoint,
                                               NavuContainer role
                                               )
        throws ConfException, IOException;

    abstract public void configureAggregationSwitch(NavuNode service,
                                               NavuContainer role,
                                               String hsrpAddress,
                                               String bviAddress
                                               )
        throws ConfException, IOException;

    abstract public void configureCoreLayer(NavuNode service,
                                                    NavuContainer role,
                                                    String lb,
                                                    String endpointName
                                                    )
             throws ConfException, IOException;



    abstract public String getLoopbackAddress()
        throws ConfException, IOException;


    public String getName() {
        return null;
    }

    protected void checkLeafExistance(NavuLeaf leaf, String errorMessage)
        throws ConfException
    {
        if ( ! leaf.exists() ) {
            throw new ConfException(errorMessage);
        }
    }

    protected NavuContainer getInstanceIfExists(NavuList myList, String key,
                                          String errorMessage)
        throws ConfException
    {
        try {
            if (!myList.containsNode(key) ) {
                throw new ConfException(errorMessage);
            }
        }
        catch (Exception e) {
            throw new ConfException("Can't find interface list "+ errorMessage);
        }
        return myList.elem(key);
    }

    //checkLeafExistance(interfaceNode.leaf("vlan-tagging"),
    //"VLAN Tagging not set on " + iface + " on " + routerName);

    public static Device getDevice(NavuContainer managedDevice)
        throws ConfException {
        devType dt = devType.neither;
        Device device = null;

        if ((dt = type(managedDevice)) == devType.iosxr){
            device = new IOSXRDevice(managedDevice);
        }
        else if (dt == devType.ios) {
            device = new IOSDevice(managedDevice);
        }
        else if (dt == devType.nexus) {
            device = new NexusDevice(managedDevice);
        }
        else if (dt == devType.force10) {
            device = new Force10Device(managedDevice);
        }
        return device;
    }

    // Handle the two cases of GigabitEthernet0/11 and also
    // GigabitEthernet 0/11
    public static String[] splitInterface(String s) {

        int i = 0;
        String[] ret = new String[2];
        while (i <= s.length()) {
            char c = s.charAt(i);

            if (c == ' ') {
                ret[0] = s.substring(0, i);
                ret[1] = s.substring(i+1, s.length());
                return ret;
            }
            else if (c >= '0' && c <= '9') {
                ret[0] = s.substring(0, i);
                ret[1] = s.substring(i, s.length());
                return ret;
            }
            i++;
        }
        return null;
    }



    static private enum devType {
        iosxr, ios, nexus, force10, neither;
    };

    /**
     *
     * Figure out what kind of device are we.
     *
     * We figure that out by walking through the loaded modules for
     * this devices
     *
     * @param managedDevice - Element that represent /devices/device{x}
     *                        where the key x is either ios or junos.
     *
     * @return devType indicating the device type of the supplied
     * managed device.
     */

    private static devType type(NavuContainer managedDevice)
        throws NavuException{

        //default value.
        devType type = devType.neither;

        //Retrieve a representation of a list of /devices/device/{x}/module
        NavuList modules = managedDevice.list("module");

        for(NavuContainer cont : modules.elements()){
            NavuLeaf leaf = cont.leaf("name");

            if(leaf.value().toString().equals("tailf-ned-cisco-ios-xr")){
                type = devType.iosxr;
            }
            else if(leaf.value().toString().equals("tailf-ned-cisco-nx")){
                type = devType.nexus;
            }
            else if(leaf.value().toString().equals("tailf-ned-cisco-ios")){
                type = devType.ios;
            }
            else if(leaf.value().toString().equals("tailf-ned-dell-ftos")){
                type = devType.force10;
            }

        }
        return type;
    }


}
