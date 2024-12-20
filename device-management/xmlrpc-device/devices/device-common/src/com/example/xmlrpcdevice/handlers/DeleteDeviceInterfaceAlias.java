package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class DeleteDeviceInterfaceAlias{

    private static Logger LOGGER =
        LogManager.getLogger(DeleteDeviceInterfaceAlias.class);

    public boolean delete(String ifname,String name){
        LOGGER.info("Alias remove: ifname=" + ifname + ",name:" + name);
        DeviceModel model =
            DeviceModelController.getInstance().getModel();

        int removeindex = -1;
        int aliasindex = -1;

        for(int i = 0; i < model.getInterfaces().size(); ++i){

            DeviceInterface interf = model.getInterfaces().get(i);

            if(interf.getName().equals(ifname)){
                removeindex = i;

                for(int j = 0; j < interf.getAliases().size() ; j++){
                    DeviceInterfaceAlias alias = interf.getAliases().get(j);
                    String aliaskey = alias.getNumber() + "";

                    if(aliaskey.equals(name)){
                        LOGGER.info("ohoj");
                        aliasindex = j;
                        break;
                    }
                }

            }
            if((removeindex > 0) && (aliasindex > 0))
                break;
        }

        LOGGER.info("removeindex:" + removeindex);
        LOGGER.info("alias:" + aliasindex);


        if((removeindex > -1) && (aliasindex > -1)){
            model.getInterfaces().get(removeindex)
                .getAliases().remove(aliasindex);
            return true;
        }
        return false;
    }
}
