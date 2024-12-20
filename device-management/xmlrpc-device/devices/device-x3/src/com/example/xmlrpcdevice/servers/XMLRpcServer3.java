package com.example.xmlrpcdevice.servers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.xmlrpc.common.TypeConverterFactoryImpl;
import java.io.IOException;
import com.example.xmlrpcdevice.controller.DeviceModelController;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;

import org.apache.xmlrpc.webserver.WebServer;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.xmlrpcdevice.server.XMLRpcGenericServer;

public class XMLRpcServer3 extends XMLRpcGenericServer{

    private static Logger LOGGER =
        LogManager.getLogger(XMLRpcServer3.class);

    private WebServer webServer;
    private XmlRpcServer server;


    public XMLRpcServer3(String name,InetAddress address,int port){
        super(name,address,port);
    }

    public void startDeviceServer(){
        DeviceModelController controller =
            DeviceModelController.getInstance();

        controller.setFileName("device3");
        controller.reload(3);

    }


    public static void main(String[] arg) throws Exception{
        String defaultName = "XMLRpcServer3";
        InetAddress defaultAddress = InetAddress.getByName("127.0.0.1");
        int defaultPort = 8047;

        if(arg.length >0) defaultName = arg[0];
        if(arg.length >1) defaultAddress = InetAddress.getByName(arg[1]);
        if(arg.length >2) defaultPort = Integer.parseInt(arg[2]);

        XMLRpcGenericServer srv3 =
            new XMLRpcServer3(defaultName,defaultAddress,defaultPort);
        srv3.start();
    }

}
