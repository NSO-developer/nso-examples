package com.example.xmlrpcdevice.model;

import java.util.List;
import java.util.ArrayList;

import java.io.Serializable;

public class DeviceModel implements Serializable{

    String namespace = "http://example.com/xmlrpcdevice/xmlrpc/if";
    String prefix = "if";
    String revision = "2006-11-05";
    String description = "Interface configuration";


    List<DeviceInterface> devInterfaces =
        new ArrayList<DeviceInterface>();

    public List<DeviceInterface> getInterfaces(){
        return devInterfaces;
    }

    public void addInterface(DeviceInterface interf){
        devInterfaces.add(interf);
    }

    public String toString(){
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append(prefix + ":" + namespace + " rev:" + revision);
        builder.append("\n");
        builder.append(description);
        builder.append("\n");

        for(DeviceInterface ifs: devInterfaces){
            builder.append(ifs.toString());
        }

        return builder.toString();


    }


}
