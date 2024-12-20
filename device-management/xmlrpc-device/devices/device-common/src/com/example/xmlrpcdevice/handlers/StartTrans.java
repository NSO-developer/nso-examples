package com.example.xmlrpcdevice.handlers;

import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class StartTrans{

    private static Logger LOGGER =
        LogManager.getLogger(StartTrans.class);

    public String start(){
        LOGGER.info("Handler invoking startTrans()");
        return DeviceModelController.getInstance().startTrans();

    }
}
