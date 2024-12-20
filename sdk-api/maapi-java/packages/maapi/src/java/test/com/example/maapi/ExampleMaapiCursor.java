package com.example.maapi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.*;
import com.tailf.maapi.*;

import com.tailf.examples.router.namespaces.router;

import com.tailf.ncs.ns.Ncs;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXMLParamLeaf;
import com.tailf.conf.ConfValue;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class ExampleMaapiCursor {
    private static Logger log = LogManager.getLogger(ExampleMaapiCursor.class);

    public static void main(String[] args) {
        SocketAddress address;
        if (args.length == 1) {
            address = UnixDomainSocketAddress.of(args[0]);
        } else if (args.length == 2) {
            address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        }

        new ExampleMaapiCursor(address);
    }

    public ExampleMaapiCursor(SocketAddress address) {
        try (Maapi maapi = new Maapi(address)) {
            maapi.startUserSession("admin", "maapi", new String[] { "admin" });
            int th = maapi.startTrans(Conf.DB_RUNNING,
                                      Conf.MODE_READ_WRITE);


            createSomeData(maapi, th);

            iterate(maapi, th);
            whatHaveWeDone(maapi, th);
            maapi.applyTrans(th, false);
        } catch(Exception e){
            log.error("", e);
            System.exit(1);
        }
    }
    private void createSomeData(Maapi maapi,int th) throws Exception {

        String devPath = "/ncs:devices/device";

        MaapiCursor deviceCursor = maapi.newCursor(th, devPath);

        ConfKey device = null;
        while((device = maapi.getNext(deviceCursor)) != null) {

            // Interface path
            String ifPath = devPath + "{%x}" +
                "/config/r:sys/interfaces/interface{%x}";

            // Use Confpath to format path with keys
            maapi.safeCreate(th, ifPath, device, "eth0");
            createListEntries(maapi, th,
                              new ConfPath(ifPath, device, "eth0").toString());

            maapi.safeCreate(th, ifPath, device, "eth1");
            createListEntries(maapi, th,
                              new ConfPath(ifPath, device, "eth1").toString());
        }
    }



    private void createListEntries(Maapi maapi, int th, String ifPath)
        throws Exception{

        maapi.setElem(th,new ConfBuf("Ip Interface on the server"),
                      ifPath+"/description");

        maapi.safeCreate(th, ifPath+"/enabled");

        // Get the enum label ten hundred thousand, see:
        // packages/router/src/yang/router-interfaces.yang
        ConfEnumeration enum_speed =
            ConfEnumeration.getEnumByLabel(ifPath+"/speed", "hundred");
        maapi.setElem(th, enum_speed, ifPath+"/speed");

        //Create and set the enumeration
        ConfEnumeration enum_duplex =
            ConfEnumeration.getEnumByLabel(ifPath+"/duplex", "full");
        maapi.setElem(th, enum_duplex, ifPath+"/duplex");

        //Set the mtu
        maapi.setElem(th, new ConfInt16(1100), ifPath+"/mtu");

        // Set mac address
        maapi.setElem(th, new ConfHexList("4a:17:54:ff:06:ff"), ifPath+"/mac");
    }

    private void iterate(Maapi maapi, int th) throws Exception {
        String devPath = "/ncs:devices/device";
        MaapiCursor deviceCursor = maapi.newCursor(th,new ConfPath(devPath));

        ConfNamespace routerNS = new router();
        ConfNamespace ncsNS = new Ncs();

        List<ConfXMLParam> requestParam = new ArrayList<ConfXMLParam>();
        ConfKey device = null;
        while((device = maapi.getNext(deviceCursor)) != null) {
            requestParam.add(new ConfXMLParamStart(ncsNS, "device"));
            requestParam.add(new ConfXMLParamValue(ncsNS, "name",
                                                   device.elementAt(0)));
            requestParam.add(new ConfXMLParamStart(ncsNS, "config"));
            requestParam.add(new ConfXMLParamStart(routerNS, "sys"));
            requestParam.add(new ConfXMLParamStart(routerNS, "interfaces"));

            String ifPath = devPath + "{%x}" +
                "/config/r:sys/interfaces/interface";
            MaapiCursor ifCursor = maapi.newCursor(th, ifPath, device);

            ConfKey ifKey = null;
            while((ifKey = maapi.getNext(ifCursor)) != null){
                requestParam.add(new ConfXMLParamStart(routerNS, "interface"));
                requestParam.add(new ConfXMLParamValue(routerNS, "name",
                                                       ifKey.elementAt(0)));
                requestParam.add(new ConfXMLParamLeaf(routerNS, "description"));
                requestParam.add(new ConfXMLParamLeaf(routerNS, "enabled"));
                requestParam.add(new ConfXMLParamLeaf(routerNS, "speed"));
                requestParam.add(new ConfXMLParamLeaf(routerNS, "duplex"));
                requestParam.add(new ConfXMLParamLeaf(routerNS, "mtu"));
                requestParam.add(new ConfXMLParamLeaf(routerNS, "mac"));
                requestParam.add(new ConfXMLParamStop(routerNS, "interface"));
            }

            requestParam.add(new ConfXMLParamStop(routerNS, "interfaces"));
            requestParam.add(new ConfXMLParamStop(routerNS, "sys"));
            requestParam.add(new ConfXMLParamStop(ncsNS, "config"));
            requestParam.add(new ConfXMLParamStop(ncsNS, "device"));
        }
        log.info(requestParam);

        log.info("Query \n" +
                 ConfXMLParam.toXML(requestParam.toArray(new ConfXMLParam[0]),
                                    "devices", ncsNS.uri()));

        ConfXMLParam[] fetchedValues =
            maapi.getValues(th,requestParam.toArray(new ConfXMLParam[0]),
                            "/ncs:devices");

        log.info("Retrived values: \n" +
                 ConfXMLParam.toXML(fetchedValues, "devices", ncsNS.uri()));
    }

    private void whatHaveWeDone(Maapi maapi, int th) throws Exception {
        log.info("so what have we done in transaction th:" + th);

        maapi.diffIterate(th,new MaapiDiffIterate() {
                public DiffIterateResultFlag iterate(ConfObject[] kp,
                                                     DiffIterateOperFlag op,
                                                     ConfObject oldValue,
                                                     ConfObject newValue,
                                                     Object initstate){
                    // Path to the changed element
                    ConfPath p = new ConfPath(kp);
                    if (op == DiffIterateOperFlag.MOP_VALUE_SET) {
                        // NOTE: oldValue is always null
                        log.info(p + ", value set: " + " --> " + newValue);
                    }
                    return DiffIterateResultFlag.ITER_RECURSE;
                }
            });
    }
}
