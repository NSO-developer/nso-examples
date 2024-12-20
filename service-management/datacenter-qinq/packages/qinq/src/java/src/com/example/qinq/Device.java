package com.example.qinq;

import com.tailf.conf.ConfException;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public abstract class Device {


    private static Logger LOGGER = LogManager.getLogger(Device.class);

    protected NavuContainer deviceContainer;

    protected Device(NavuContainer deviceContainer) {
        this.deviceContainer = deviceContainer;
    }

    /*abstract public void createACL(NavuContainer service)
        throws ConfException;

    abstract public void attachACL(NavuContainer service, NavuList intList)
        throws ConfException;
    */

    abstract public void configureEdgeSwitch(NavuContainer service,
                                             NavuContainer sw)
        throws ConfException;

    abstract public void configureCoreSwitch(NavuContainer service,
                                             NavuContainer sw)
        throws ConfException;

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

        if ((dt = type(managedDevice)) == devType.ciscoios){
            device = new CiscoDevice(managedDevice);
        } else if (dt == devType.pica8) {
            device = new Pica8Device(managedDevice);
        }
        else {
            LOGGER.info("Devicetype: unknown");
        }
        return device;
    }


    static private enum devType {
        ciscoios, pica8, neither;
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

            if(leaf.value().toString().equals("pica8")){
                type = devType.pica8;
            }else if(leaf.value().toString().equals("tailf-ned-cisco-ios")){
                type = devType.ciscoios;
            }
        }
        return type;
    }
}
