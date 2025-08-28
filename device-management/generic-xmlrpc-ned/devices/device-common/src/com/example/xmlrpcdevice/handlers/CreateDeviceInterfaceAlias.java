package com.example.xmlrpcdevice.handlers;

import com.example.xmlrpcdevice.model.*;
import java.io.IOException;
import com.example.xmlrpcdevice.controller.DeviceModelController;

public class CreateDeviceInterfaceAlias{

    public boolean createInterfaceAlias(String aliasname, String ifname){

        DeviceModel model =
            DeviceModelController.getInstance().getModel();

        for(DeviceInterface devif: model.getInterfaces()){

            if(devif.getName().equals(ifname)){

                DeviceInterfaceAlias alias =
                    new DeviceInterfaceAlias(Integer.parseInt(aliasname));
                devif.addAlias(alias);
                break;
            }

        }

        return true;

    }



}
