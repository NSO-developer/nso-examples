package com.example.maapi;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.List;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;

import com.tailf.examples.router.namespaces.router;

import com.tailf.ncs.ns.Ncs;
import com.tailf.conf.ConfXMLParam;

import com.tailf.conf.Conf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuNode;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class EncodeSubtree{
    private static Logger log = LogManager.getLogger(SetElem.class);

    public static void main(String[] args) {
        SocketAddress address;
        if (args.length == 1) {
            address = UnixDomainSocketAddress.of(args[0]);
        } else if (args.length == 2) {
            address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        }
        new EncodeSubtree(address);
    }

    public EncodeSubtree(SocketAddress address){
        try (Maapi maapi = new Maapi(address)) {
            maapi.startUserSession("admin", "maapi", new String[] { "admin" });

            int th   = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
            NavuContext ctx = new NavuContext(maapi, th);
            NavuList devices =
                new NavuContainer(ctx)
                .container(new Ncs().hash())
                .container(Ncs._devices).list(Ncs._device);

            for(NavuNode device: devices.elements()){
                encodeDeviceTree(device);
            }
        }catch(Exception e){
            log.info("",e);
        }
    }


    private void encodeDeviceTree(NavuNode deviceNode) throws Exception{

        NavuContainer deviceEntry = (NavuContainer) deviceNode;

        NavuContainer routerSys =
            deviceEntry.container(Ncs._config).container(router._sys);

        List<ConfXMLParam> deviceXMLGetParams = routerSys.encodeXML();

        ConfXMLParam[] deviceData = routerSys.getParent()
            .getValues(deviceXMLGetParams.toArray(new ConfXMLParam[0]));

        String XMLResult =
            ConfXMLParam.toXML(deviceData,Ncs._config_, new Ncs().uri());

        log.info("XML PRINT FOR PATH:" + deviceNode.getKeyPath() +
                 "\n" + XMLResult);
    }

}
