package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class Close{

    private static Logger LOGGER =
        LogManager.getLogger(Close.class);

    public boolean closeConnection(){
        LOGGER.info("Handler invoking Close Connection()");
        return DeviceModelController.getInstance().close();
    }
}
