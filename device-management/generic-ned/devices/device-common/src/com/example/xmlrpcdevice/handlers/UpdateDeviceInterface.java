package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class UpdateDeviceInterface{

    private static Logger LOGGER =
        LogManager.getLogger(UpdateDeviceInterface.class);

    public boolean setInterface(String name, String prop , String val){
        LOGGER.info("name:" + name + ",prop: " + prop + ",val:" + val);
        DeviceModel model =
            DeviceModelController.getInstance().getModel();

        for(DeviceInterface interf: model.getInterfaces()){

            if(name.equals(interf.getName())){
                updateInterface(interf,prop,val);
            }
        }

        return true;

    }


    void updateInterface(DeviceInterface interf,String prop,String val){
        if(prop.equals("if:macaddr")){
            interf.setMacAddress(val);
        }else if(prop.equals("if:ipv4-address")){
            interf.setAddress(val);
        }else if(prop.equals("if:ipv4-mask")){
            interf.setMask(val);
        }else if(prop.equals("if:status")){
            int status = 0;
            if(val.equals("Enum(0)"))
                interf.setStatus(0);
            else if(val.equals("Enum(1)"))
                interf.setStatus(1);
        }else if(prop.equals("if:mtu")){
            interf.setMtu(val);
        }else if(prop.equals("if:speed")){
            interf.setSpeed(val);
        }else if(prop.equals("if:txqueuelen")){
            interf.setTxQueuelen(val);
        }

    }


}
