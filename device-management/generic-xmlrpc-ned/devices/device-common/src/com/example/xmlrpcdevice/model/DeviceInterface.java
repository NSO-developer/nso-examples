package com.example.xmlrpcdevice.model;

import java.util.List;
import java.io.Serializable;
import java.util.ArrayList;
import java.net.InetAddress ;
import java.net.UnknownHostException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class DeviceInterface implements Serializable{

    public static final int UP = 0;
    public static final int  DOWN = 1;
    private static Logger LOGGER = LogManager.getLogger(DeviceInterface.class);

    private String name;
    private DeviceMacAddress macAddress;
    private InetAddress ipv4Address;
    private InetAddress ipv4Mask;
    private int status = DeviceInterface.UP;
    private int mtu;
    private List<DeviceInterfaceAlias> alias =
        new ArrayList<DeviceInterfaceAlias>();
    private int speed = 1000000;
    private int txqueuelen;


    public String toString(){
        StringBuilder builder = new StringBuilder();
        builder.append("    name:" + name);
        builder.append("\n");
        builder.append("    macAddress:" + macAddress);
        builder.append("\n");
        builder.append("    ipv4Address:" + ipv4Address);
        builder.append("\n");
        builder.append("    ipv4Mask:" + ipv4Mask);
        builder.append("\n");
        builder.append("    status:" + ((status == 1)? "Down": "Up" ) );
        builder.append("\n");
        builder.append("    mtu:" + mtu);
        builder.append("\n");
        for(DeviceInterfaceAlias al: alias) {
            builder.append(al.toString());
        }

        return builder.toString();
    }

    public DeviceInterface(String name){

        this.name = name;


        this.macAddress = new DeviceMacAddress("00:00:00:00:00:00");
        try{
            this.ipv4Address = InetAddress.getByName("0.0.0.0");
            this.ipv4Mask = InetAddress.getByName("0.0.0.0");
            this.alias.add(new DeviceInterfaceAlias(0,
                           InetAddress.getByName("0.0.0.0"),
                           InetAddress.getByName("0.0.0.0")));

        }catch(UnknownHostException e){
            LOGGER.error("Error:" , e);
        }
        this.mtu = 0;
        this.txqueuelen = 0;

    }

    public DeviceInterface(String name,
                           DeviceMacAddress mac,
                           InetAddress ipv4Address,
                           InetAddress ipv4Mask,
                           int status,
                           int mtu,
                           List<DeviceInterfaceAlias> alias,
                           int speed,
                           int txqueuelen){

        this.name = name;
        this.macAddress = mac;
        this.ipv4Address = ipv4Address;
        this.ipv4Mask = ipv4Mask;
        this.status = status;
        this.mtu = mtu;
        this.alias = alias;
        this.speed = speed;
        this.txqueuelen = txqueuelen;

    }

    public List<DeviceInterfaceAlias> getAliases(){
        return alias;
    }

    public void addAlias(DeviceInterfaceAlias a){
        alias.add(a);
    }

    public InetAddress getAddress(){
        return ipv4Address;
    }
    public InetAddress getMask(){
        return ipv4Mask;
    }
    public void setStatus(int status){
        this.status = status;
    }
    public void setMask(String mask){
        try{
            this.ipv4Mask = InetAddress.getByName(mask);
        }catch(UnknownHostException e){
            LOGGER.error("", e);
        }
    }

    public void setAddress(String addr){
        try{
            this.ipv4Address = InetAddress.getByName(addr);
        }catch(UnknownHostException e){
            LOGGER.error("",e);
        }
    }

    public String getName(){
        return name;
    }
    public DeviceMacAddress getMacAddress(){
        return macAddress;
    }
    public void setMacAddress(String mac){
        macAddress.setAddress(mac);
    }

    public int getStatus(){
        return status;
    }

    public int getMtu(){
        return mtu;
    }
    public void setMtu(String mtu){
        this.mtu = Integer.parseInt(mtu);
    }

    public int getSpeed(){
        return speed;
    }
    public void setSpeed(String speed){
        this.speed = Integer.parseInt(speed);
    }

    public void setTxQueuelen(String txqueuelen){
        this.txqueuelen = Integer.parseInt(txqueuelen);
    }

    public int getTxQueuelen(){
        return txqueuelen;
    }


}
