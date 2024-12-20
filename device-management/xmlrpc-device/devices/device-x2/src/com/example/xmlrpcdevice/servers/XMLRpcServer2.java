package com.example.xmlrpcdevice.servers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.xmlrpc.common.TypeConverterFactoryImpl;
import com.example.xmlrpcdevice.controller.DeviceModelController;
import java.io.IOException;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.webserver.WebServer;
import org.apache.xmlrpc.XmlRpcException;
import com.example.xmlrpcdevice.server.XMLRpcGenericServer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class XMLRpcServer2 extends XMLRpcGenericServer{

    private static Logger LOGGER =
        LogManager.getLogger(XMLRpcServer2.class);

    private WebServer webServer;
    private XmlRpcServer server;


    public XMLRpcServer2(String name,InetAddress address,int port){
        super(name,address,port);
    }

    public void startDeviceServer(){
        DeviceModelController controller =
            DeviceModelController.getInstance();

        controller.setFileName("device2");
        controller.reload(2);
    }


    public static void main(String[] arg) throws Exception{
        String defaultName = "XMLRpcServer2";
        InetAddress defaultAddress = InetAddress.getByName("127.0.0.1");
        int defaultPort = 8046;

        if(arg.length > 0) defaultName = arg[0];
        if(arg.length > 1) defaultAddress = InetAddress.getByName(arg[1]);
        if(arg.length > 2) defaultPort = Integer.parseInt(arg[2]);

        XMLRpcGenericServer srv2 =
            new XMLRpcServer2(defaultName,defaultAddress,defaultPort);
        srv2.start();
    }

}
