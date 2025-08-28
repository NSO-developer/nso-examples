package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;


public class UpdateDeviceInterfaceAlias{

    private static Logger LOGGER =
        LogManager.getLogger(UpdateDeviceInterfaceAlias.class);

    public boolean setAlias(String ifname,
                            String number,
                            String tag ,
                            String val){

        DeviceModel model =
            DeviceModelController.getInstance().getModel();

        for(DeviceInterface interf: model.getInterfaces()){

            if(interf.getName().equals(ifname)){

                for(DeviceInterfaceAlias alias: interf.getAliases()){
                    String aliasnr = alias.getNumber() + "";
                    if(aliasnr.equals(number)){

                        if(tag.equals("ipv4-address")){
                            alias.setAddress(val);
                            return true;
                        }else if(tag.equals("ipv4-mask")){
                            alias.setMask(val);
                            return true;
                        }else if(tag.equals("number")){
                            alias.setNumber(Integer.parseInt(val));
                            return true;
                        }
                    }
                }
            }
        }

        return false;

    }


    void updateInterface(DeviceInterface interf,String prop,String val){
        if(prop.equals("macaddr")){
            interf.setMacAddress(val);

        }else if(prop.equals("ipv4-address")){
            interf.setAddress(val);
        }else if(prop.equals("ipv4-mask")){
            interf.setMask(val);
        }else if(prop.equals("status")){
            int status = 0;
            if(val.equals("Up"))
                interf.setStatus(0);
            else if(val.equals("Down"))
                interf.setStatus(1);
        }else if(prop.equals("mtu")){
            interf.setMtu(val);
        }else if(prop.equals("speed")){
            interf.setSpeed(val);
        }else if(prop.equals("txqueuelen")){
            interf.setTxQueuelen(val);
        }

    }


}
