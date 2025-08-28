package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class DeleteDeviceInterface{

    private static Logger LOGGER =
        LogManager.getLogger(DeleteDeviceInterface.class);

    public boolean deleteInterface(String name){
        DeviceModel model =
            DeviceModelController.getInstance().getModel();

        int removeindex = -1;
        for(int i = 0; i < model.getInterfaces().size(); ++i){
            DeviceInterface interf = model.getInterfaces().get(i);
            if(interf.getName().equals(name)){
                removeindex = i;
                break;
            }
        }

        if(removeindex > -1){
            model.getInterfaces().remove(removeindex);
        }

        return true;

    }
}
