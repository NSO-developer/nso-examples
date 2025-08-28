package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class Abort{

    private static Logger LOGGER =
        LogManager.getLogger(Abort.class);

    //Abort the current transaction
    public void abort(){
        DeviceModelController.getInstance().abort();
    }
}
