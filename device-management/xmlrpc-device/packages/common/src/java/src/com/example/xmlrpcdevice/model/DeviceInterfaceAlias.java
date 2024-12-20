package com.example.xmlrpcdevice.model;

import java.util.List;
import java.io.Serializable;
import java.util.ArrayList;
import java.net.InetAddress ;
import java.net.UnknownHostException ;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class DeviceInterfaceAlias implements Serializable{
    private static Logger LOGGER =
        LogManager.getLogger(DeviceInterfaceAlias.class);
    private int number;
    private InetAddress ipv4Address;
    private InetAddress ipv4Mask;

    public String toString(){
        StringBuilder builder = new StringBuilder();

        builder.append("\n");
        builder.append("    Alias:");
        builder.append("\n");
        builder.append("        number: "+ number);
        builder.append("\n");
        builder.append("        ipv4Address: "+ ipv4Address);
        builder.append("\n");
        builder.append("        ipv4Mask: "+ ipv4Mask);
        builder.append("\n");
        return builder.toString();
    }

    public DeviceInterfaceAlias(int number){
        this.number = number;
        try{
            this.ipv4Address = InetAddress.getByName("0.0.0.0");
            this.ipv4Mask = InetAddress.getByName("0.0.0.0");
        }catch(UnknownHostException e){
            LOGGER.error("Could not create DeviceInterface", e);
        }
    }


    public DeviceInterfaceAlias(int number,
                                InetAddress ipv4Address,
                                InetAddress ipv4Mask){
        this.number = number;
        this.ipv4Address = ipv4Address;
        this.ipv4Mask = ipv4Mask;
    }

    public int getNumber(){
        return number;
    }
    public void setNumber(int number){
        this.number = number;
    }

    public InetAddress getAddress(){
        return ipv4Address;
    }

    public void setAddress(String address){
        try{
        this.ipv4Address = InetAddress.getByName(address);
        }catch(Exception e){}
    }

    public void setMask(String address){
        try{
            this.ipv4Mask = InetAddress.getByName(address);
        }catch(Exception e){}
    }

    public InetAddress getMask(){
        return ipv4Mask;
    }


}
