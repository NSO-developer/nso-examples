package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class Connect{

    private static Logger LOGGER =
        LogManager.getLogger(Connect.class);

    public boolean newConnection(){
        LOGGER.info("Handler invoking New Connection()");
        return DeviceModelController.getInstance().connect();

    }
}
