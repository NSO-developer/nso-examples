package com.example.xmlrpcdevice.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.xmlrpc.common.TypeConverterFactoryImpl;
import java.io.IOException;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.webserver.WebServer;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public abstract class XMLRpcGenericServer{

    //private static final int PORT = 8045;
    protected int port;
    protected InetAddress address;
    protected String name;


    private static Logger LOGGER =
        LogManager.getLogger(XMLRpcGenericServer.class);

    private WebServer webServer;
    private XmlRpcServer server;


    public XMLRpcGenericServer(String name, InetAddress address, int port){
        this.port = port;
        this.address = address;
        this.name = name;


        try{
            webServer =
                new WebServer(port,
                              address);
            //InetAddress.getByName("localhost"));

            XmlRpcServer xmlRpcServer =
                webServer.getXmlRpcServer();

            PropertyHandlerMapping phm =
                new PropertyHandlerMapping();

            phm.addHandler("Connect",
                           com.example.xmlrpcdevice.handlers.Connect.class);

            phm.addHandler("Close",
                           com.example.xmlrpcdevice.handlers.Close.class);


            // phm.addHandler("Calculator",
            //          com.example.xmlrpcdevice.handlers.Calculator.class);

            phm.addHandler("GetModel",
                           com.example.xmlrpcdevice.handlers.GetModel.class);

            phm.addHandler("UpdateInterface",
                           com.example.xmlrpcdevice.handlers
                           .UpdateDeviceInterface.class);

            phm.addHandler("UpdateInterfaceAlias",
                           com.example.xmlrpcdevice.handlers
                           .UpdateDeviceInterfaceAlias.class);

            phm.addHandler("CreateInterface",
                           com.example.xmlrpcdevice.handlers
                           .CreateDeviceInterface.class);

            phm.addHandler("CreateInterfaceAlias",
                           com.example.xmlrpcdevice.handlers
                           .CreateDeviceInterfaceAlias.class);


             phm.addHandler("GetDeviceStats",
                            com.example.xmlrpcdevice.handlers
                            .GetDeviceStats.class);


            phm.addHandler("CommitDevice",
                         com.example.xmlrpcdevice.handlers.CommitDevice.class);

            phm.addHandler("Start",
                           com.example.xmlrpcdevice.handlers.StartTrans.class);


            phm.addHandler("PersistDevice",
                        com.example.xmlrpcdevice.handlers.PersistDevice.class);


            phm.addHandler("DeleteInterface",
               com.example.xmlrpcdevice.handlers.DeleteDeviceInterface
                           .class);

            phm.addHandler("DeleteInterfaceAlias",
                           com.example.xmlrpcdevice.handlers
                           .DeleteDeviceInterfaceAlias
                           .class);


            phm.addHandler("AbortDevice",
                           com.example.xmlrpcdevice.handlers.Abort
                           .class);

            phm.addHandler("RevertDevice",
                           com.example.xmlrpcdevice.handlers.RevertDevice
                           .class);


            XmlRpcServerConfigImpl serverConfig =
                (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
            serverConfig.setEnabledForExtensions(true);
            xmlRpcServer.setHandlerMapping(phm);
        }catch(XmlRpcException e){
            LOGGER.error("XML RPC ERROR ", e);
        }
    }

    public void start(){
        try{
            webServer.start();
            startDeviceServer();

            LOGGER.warn("XML RPC Server " + name + " Started on " +
                        address + " port " + port);
        }catch(IOException e){
            LOGGER.error("IOException occured ", e);
        }
    }

    public abstract void startDeviceServer() ;


}
