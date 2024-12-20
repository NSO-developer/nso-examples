package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import com.example.xmlrpcdevice.controller.DeviceModelController;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class CreateDeviceInterface{

    private static Logger LOGGER =
        LogManager.getLogger(CreateDeviceInterface.class);

    public boolean createInterface(String name){
        DeviceModel model =
            DeviceModelController.getInstance().getModel();

        DeviceInterface interf =
            new DeviceInterface(name);

        model.addInterface(interf);

        return true;

    }



}
