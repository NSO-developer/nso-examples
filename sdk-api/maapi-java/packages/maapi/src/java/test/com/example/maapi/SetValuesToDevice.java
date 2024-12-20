package com.example.maapi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;

import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.*;
import com.tailf.maapi.*;

import com.example.maapi.namespaces.MaapiTypes;
import com.tailf.examples.router.namespaces.router;

import com.tailf.ncs.ns.Ncs;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuListEntry;
import com.tailf.navu.PreparedXMLStatement;
import com.tailf.navu.NavuNode;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class SetValuesToDevice {
    private static Logger log = LogManager.getLogger(SetElem.class);
    private PreparedXMLStatement myPreparedCall;

    private String xmlParams =
        "<r:interfaces xmlns:r=\"http://example.com/router\">" +
        "<r:interface>" +
        "<r:name>?</r:name>" +
        "<r:description>?</r:description>" +
        "<r:enabled></r:enabled>" +
        "<r:speed>?</r:speed>" +
        "<r:duplex>?</r:duplex>" +
        "<r:mtu>?</r:mtu>" +
        "<r:mac>?</r:mac>" +
        "<r:unit>" +
        "<r:name>?</r:name>" +
        "<r:enabled>?</r:enabled>" +
        "<r:description>?</r:description>" +
        "<r:vlan-id>?</r:vlan-id>" +
        "<r:family>" +
        "<r:inet>"+
        "<r:address>" +
        "<r:name>?</r:name>" +
        "<r:prefix-length>?</r:prefix-length>" +
        "<r:broadcast>?</r:broadcast>" +
        "</r:address>" +
        "<r:address>" +
        "<r:name>?</r:name>" +
        "<r:prefix-length>?</r:prefix-length>" +
        "<r:broadcast>?</r:broadcast>" +
        "</r:address>" +
        "</r:inet>"  +
        "</r:family>" +
        "</r:unit>" +
        "</r:interface>" +
        "</r:interfaces>";


    public static void main(String[] args) {
        SocketAddress address;
        if (args.length == 1) {
            address = UnixDomainSocketAddress.of(args[0]);
        } else if (args.length == 2) {
            address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        }

        new SetValuesToDevice(address);
    }

    public SetValuesToDevice(SocketAddress address) {
        try (Maapi maapi = new Maapi(address)) {
            maapi.startUserSession("admin", "maapi", new String[] { "admin" });
            int th   = maapi.startTrans(Conf.DB_RUNNING,
                                        Conf.MODE_READ_WRITE);
            NavuContext ctx = new NavuContext(maapi,th);
            NavuList _devices =
                new NavuContainer(ctx)
                .container(new Ncs().hash())
                .container(Ncs._devices).list(Ncs._device);

            for(NavuNode device: _devices.elements()){
                createDeviceInterface(device);
            }

            createSomeData(ctx);
            whatHaveWeDone(maapi, th);
            maapi.applyTrans(th,false);
        } catch(Exception e) {
            log.error("",e);
            System.exit(1);
        }
    }

    private void createDeviceInterface(NavuNode device) throws Exception {
        NavuListEntry _currDevice = (NavuListEntry)device;
        NavuContainer _routerSys =
            _currDevice.container(Ncs._config).container(router._sys);

        if(myPreparedCall == null)
            myPreparedCall = _routerSys.prepareXMLCall(xmlParams);


        myPreparedCall.put(0,"eth0");
        myPreparedCall.put(1,"First Interface on the server");

        //Create and set the enumeration
        ConfPath path =
            new ConfPath("/ncs:devices/device{%x}/config" +
                         "/r:sys/interfaces/interface/speed",
                         _currDevice.getKey());
        ConfEnumeration _enum_thousand =
            ConfEnumeration.getEnumByLabel(path,"thousand");
        myPreparedCall.put(2,_enum_thousand);

        //Create and set the enumeration
        path = new ConfPath("/ncs:devices/device{%x}/config" +
                            "/r:sys/interfaces/interface/duplex",
                            _currDevice.getKey());

        ConfEnumeration _enum_full =
            ConfEnumeration.getEnumByLabel(path,"full");

        myPreparedCall.put(3,_enum_full);
        myPreparedCall.put(4,new ConfInt16(1000));
        myPreparedCall.put(5,new ConfHexList("3c:07:54:ff:06:ff"));

        myPreparedCall.put(6,new ConfInt32(10));
        myPreparedCall.put(7,new ConfBool(true));
        myPreparedCall.put(8,new ConfBuf("First Unit"));
        myPreparedCall.put(9,new ConfUInt16(2));

        myPreparedCall.put(10,new ConfIPv4("192.168.0.2"));
        myPreparedCall.put(11,new ConfInt32(2));
        myPreparedCall.put(12,new ConfIPv4("192.168.0.12"));

        myPreparedCall.put(13,new ConfIPv4("192.168.0.3"));
        myPreparedCall.put(14,new ConfInt32(3));
        myPreparedCall.put(15,new ConfIPv4("192.168.0.13"));
        myPreparedCall.setValues(_routerSys);
        myPreparedCall.reset();
    }


    private void createSomeData(NavuContext ctx) throws Exception{
        NavuContainer _navuRoot = new NavuContainer(ctx);
        NavuContainer _maapiTypes =
            _navuRoot.container(new MaapiTypes().hash())
            .container(MaapiTypes._maapi_types);

        String xmlPrep =
            "<mt:mtypes xmlns:mt=\"http://examples.com/maapi\">" +
            "<mt:ilist>?</mt:ilist>" +
            "<mt:ilist>?</mt:ilist>" +
            "<mt:ilist>?</mt:ilist>" +

            "<mt:slist>?</mt:slist>" +
            "<mt:slist>?</mt:slist>" +
            "<mt:slist>?</mt:slist>" +
            "</mt:mtypes>";

        PreparedXMLStatement prepCall =
            _maapiTypes.prepareXMLCall(xmlPrep);

        prepCall.put(0,new ConfUInt32(1));
        prepCall.put(1,new ConfUInt32(2));
        prepCall.put(2,new ConfUInt32(3));

        prepCall.put(3, new ConfBuf("one"));
        prepCall.put(4, new ConfBuf("two"));
        prepCall.put(5, new ConfBuf("tree"));
        prepCall.setValues();
    }



    private void whatHaveWeDone(Maapi maapi, int th) throws Exception{
        log.info("so what have we done in transaction th:" + th);
        maapi.diffIterate(th,new MaapiDiffIterate(){
                public DiffIterateResultFlag iterate(ConfObject[] kp,
                                                     DiffIterateOperFlag op,
                                                     ConfObject oldValue,
                                                     ConfObject newValue,
                                                     Object initstate){
                    log.info(new ConfPath(kp).toString()+", " + op +
                             " " + "--> " + newValue);
                    return DiffIterateResultFlag.ITER_CONTINUE;
                }
            });


    }


}
