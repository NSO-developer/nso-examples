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
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.PreparedXMLStatement;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class PrepSetTest {
    private static Logger log = LogManager.getLogger(PrepSetTest.class);

    public static void main(String[] args) {
        SocketAddress address;
        if (args.length == 1) {
            address = UnixDomainSocketAddress.of(args[0]);
        } else if (args.length == 2) {
            address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        }

        new PrepSetTest(address);
    }

    public PrepSetTest(SocketAddress address) {
        try (Maapi maapi = new Maapi(address)) {
            maapi.startUserSession("admin", "maapi", new String[] { "admin" });
            int th   = maapi.startTrans(Conf.DB_RUNNING,
                                        Conf.MODE_READ_WRITE);

            NavuContext ctx = new NavuContext(maapi,th);
            createSomeData(ctx);
            whatHaveWeDone(maapi, th);
            maapi.applyTrans(th,false);
        } catch(Exception e) {
            log.error("",e);
            System.exit(1);
        }
    }


    private void createSomeData(NavuContext ctx) throws Exception {
        NavuContainer _navuRoot = new NavuContainer(ctx);
        NavuContainer _maapiTypes =
            _navuRoot.container(new MaapiTypes().hash())
            .container(MaapiTypes._maapi_types);

        String xmlPrep =
            "<mt:mtypes xmlns:mt=\"http://examples.com/maapi\">" +
            "<mt:servers>" +
            "<mt:server>" +
            "<mt:srv-name>?</mt:srv-name>" +
            "<mt:ip>?</mt:ip>" +
            "<mt:port>?</mt:port>" +
            "<mt:foo>" +

            "<mt:foo-bar>" +
            "<mt:bar>?</mt:bar>" +
            "<mt:baz>?</mt:baz>" +

            "<mt:bas>?</mt:bas>" +
            "<mt:bas>?</mt:bas>" +
            "<mt:bas>?</mt:bas>" +

            "<mt:bak>?</mt:bak>" +
            "</mt:foo-bar>" +
            "</mt:foo>" +

            "<mt:interfaces>" +
            "<mt:interface>" +
            "<mt:if-name>?</mt:if-name>" +
            "<mt:mtu>?</mt:mtu>" +
            "<mt:UP/>" +
            "<mt:NOARP/>" +
            "</mt:interface>" +
            "<mt:interface>" +
            "<mt:if-name>?</mt:if-name>" +
            "<mt:UP/>" +
            "<mt:LOOPBACK/>" +
            "<mt:mtu>?</mt:mtu>" +
            "</mt:interface>" +
            "</mt:interfaces>" +

            "</mt:server>" +
            "</mt:servers>" +
            "</mt:mtypes>";

        PreparedXMLStatement prepCall =
            _maapiTypes.prepareXMLCall(xmlPrep);

        prepCall.put(0,new ConfBuf("www2")); //srv-name
        prepCall.put(1,new ConfIPv4("192.168.0.11")); // ip
        prepCall.put(2, new ConfUInt16(8080)); // port
        prepCall.put(3,new ConfInt64(1) ); // bar
        prepCall.put(4,new ConfInt64(2) ); //baz

        prepCall.put(5, new ConfBuf("one")); //bas
        prepCall.put(6, new ConfBuf("two")); //bas
        prepCall.put(7, new ConfBuf("tree")); //bas
        prepCall.put(8, new ConfBuf("bakes")); //bak
        prepCall.put(9, new ConfBuf("eth0")); //
        prepCall.put(10, new ConfInt64(1600));
        prepCall.put(11, new ConfBuf("lo"));
        prepCall.put(12, new ConfInt64(1500));
        prepCall.setValues();
    }



    private void whatHaveWeDone(Maapi maapi, int th) throws Exception {
        log.info("so what have we done in transaction th:" + th);
        maapi.diffIterate(th, new MaapiDiffIterate() {
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
