package com.example.xmlrpcdevice.servers;

import com.example.xmlrpcdevice.model.*;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.xmlrpc.XmlRpcException;
import java.net.*;

public class Client1{
    private static Logger LOGGER = LogManager.getLogger(Client1.class);
    private XmlRpcClientConfigImpl config;
    private XmlRpcClient client;
    private String host;
    private String port;

    public Client1(String host,String port){
        this.host = host;
        this.port = port;

        try{
            config = new XmlRpcClientConfigImpl();

            config.setServerURL(new URL("http://" + this.host + ":" +
                this.port  +   "/xmlrpc"));
            LOGGER.info("http://" + this.host + ":" +
                this.port  +   "/xmlrpc");
            config.setEnabledForExtensions(true);
            client = new XmlRpcClient();
            client.setConfig(config);

        }catch(MalformedURLException e){
            LOGGER.error("Errro:", e);
        }
    }

    public static void main(String[] arg){
        String h = "localhost";
        String p = "8045";

        if(arg.length >0)
            h = arg[0];
        if(arg.length > 1)
            p = arg[1];

        new Client1(h,p).execute();

    }

    public void execute(){
        try{

            Object[] params =
                new Object[]{};

            LOGGER.info("client:" + client);
            Object o = client.execute("Connect.newConnection",params);
            LOGGER.info("o:" + o);
            DeviceModel devmodel =
                (DeviceModel) client.execute("GetModel.get", params);

            LOGGER.info(devmodel);


        }catch(XmlRpcException e){
            LOGGER.error("Error:", e);
        }
    }


}
