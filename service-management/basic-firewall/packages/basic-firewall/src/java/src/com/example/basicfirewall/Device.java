package com.example.basicfirewall;

import com.tailf.conf.ConfException;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;


public abstract class Device {

    protected NavuContainer deviceContainer;

    protected Device(NavuContainer deviceContainer) {
        this.deviceContainer = deviceContainer;
    }

    abstract public void createACL(NavuContainer service)
        throws ConfException;

    abstract public void attachACL(NavuContainer service, NavuList intList)
        throws ConfException;

    public String getName() {
        return null;
    }

    public static Device getDevice(NavuContainer managedDevice)
        throws ConfException {
        devType dt = devType.neither;
        Device device = null;

        if ((dt = type(managedDevice)) == devType.cisco){
            device = new CiscoDevice(managedDevice);
        } else if (dt == devType.junos) {
            device = new JuniperDevice(managedDevice);
        }
        return device;
    }


    static private enum devType {
        cisco, junos, neither;
    };

    /**
     *
     * Figure out what kind of device are we.
     *
     * We figure that out by walking through the loaded modules for
     * this device
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

            if(leaf.value().toString().equals("junos")){
                type = devType.junos;
            }else if(leaf.value().toString().equals("tailf-ned-cisco-ios")){
                type = devType.cisco;
            }
        }
        return type;
    }
}
