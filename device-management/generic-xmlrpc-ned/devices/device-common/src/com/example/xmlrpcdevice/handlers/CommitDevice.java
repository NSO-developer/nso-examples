package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class CommitDevice{

    private static Logger LOGGER =
        LogManager.getLogger(CommitDevice.class);

    public boolean commit(String rev){
        try{
            return DeviceModelController.getInstance().commit(rev);

        }catch(IOException e){
            LOGGER.error("",e);
            return false;
        }
    }
}
