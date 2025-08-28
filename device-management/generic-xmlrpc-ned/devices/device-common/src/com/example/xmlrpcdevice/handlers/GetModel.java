package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import com.example.xmlrpcdevice.controller.DeviceModelController;
public class GetModel {

    ///Handler that revrievs the model
    public DeviceModel get(){
        return DeviceModelController.getInstance().getModel();
    }
}
