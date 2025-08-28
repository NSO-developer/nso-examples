package com.example.xmlrpcdevice.model;

import java.io.*;

public class DeviceMacAddress implements Serializable{

    private String address;

    public DeviceMacAddress(String address){
        this.address = address;
    }
    public String getAddress(){
        return address;
    }
    public void setAddress(String addr){
        this.address = addr;
    }

    public String toString(){
        return address;
    }
}
