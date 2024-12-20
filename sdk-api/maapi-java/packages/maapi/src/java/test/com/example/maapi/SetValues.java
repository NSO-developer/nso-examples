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

import com.example.maapi.namespaces.MaapiTypes;

import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class SetValues {
    private Maapi  maapi;
    private static Logger log = LogManager.getLogger(SetValues.class);

    public static void main(String[] args) {
        SocketAddress address;
        if (args.length == 1) {
            address = UnixDomainSocketAddress.of(args[0]);
        } else if (args.length == 2) {
            address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        }

        new SetValues(address).setTypesWithMaapi();
    }

    public SetValues(SocketAddress address) {
        try {
            maapi = new Maapi(address);
        } catch(Exception e) {
            log.error("",e);
            System.exit(1);
        }
    }
    public void setTypesWithMaapi() {
        try{
            maapi.startUserSession("admin", "maapi", new String[] { "admin" });
            int th   = maapi.startTrans(Conf.DB_RUNNING,
                                        Conf.MODE_READ_WRITE);
            List<ConfXMLParam> params = new ArrayList<ConfXMLParam>();
            final int h = new MaapiTypes().hash();
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_int8,
                                             new ConfInt8(88)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_uint8,
                                             new ConfUInt8(99)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_int16,
                                             new ConfInt16(88)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_uint16,
                                            new ConfUInt16(199)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_int32,
                                            new ConfInt32(1010199)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_int64,
                                            new ConfInt64(88888)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._b,
                                           new ConfBool(true)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._f,
                                             new ConfDouble(4.66)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_ipv4,
                                             new ConfIPv4(new int[]{1,2,3,4})));
            params.add(new ConfXMLParamValue(h,MaapiTypes._c_ipv6,
                                             new ConfIPv6("fe80::")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._datetime,
                                             ConfDatetime.getConfDatetime()));
            params.add(new ConfXMLParamValue(h,MaapiTypes._date,
                                             new ConfDate("2012-08-30")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._duration,
                                             new ConfDuration("P1Y")));
            ConfEnumeration _enum_30_min =
                ConfEnumeration
                .getEnumByLabel("/mt:maapi-types/mtypes/types/enum","30-mins");

            params.add(new ConfXMLParamValue(h,MaapiTypes._enum,
                                             _enum_30_min));

            params.add(new ConfXMLParamValue(h,MaapiTypes._bits,
                                             new ConfBit32(2)));
            ConfObjectRef _objectref =
                new ConfObjectRef("/mt:maapi-types/mtypes/servers" +
                                  "/server[srv-name='smtp']/ip");
            params.add(new ConfXMLParamValue(h,MaapiTypes._objectref,
                                             _objectref));
            ConfIPv4Prefix _ipv4prefix = new ConfIPv4Prefix("192.168.0.0/16");
            params.add(new ConfXMLParamValue(h,
                                             MaapiTypes._ipv4Prefix,
                                             _ipv4prefix));
            ConfIPv6Prefix _ipv6prefix =
                new ConfIPv6Prefix("2001:DB8::1428:57A8/125");
            params.add(new ConfXMLParamValue(h,
                                             MaapiTypes._ipv6Prefix,
                                             _ipv6prefix));
            ConfXMLParam[] setParams = params.toArray(new ConfXMLParam[0]);
            //Create the XML representation of the ConfXMLParam
            String setParamsXML = ConfXMLParam.toXML(setParams,
                                                     MaapiTypes._types_,
                                                     new MaapiTypes().uri());
            log.info("\n" + setParamsXML);
            params.clear();
            // /mt:maapi-types/mtypes/servers
            maapi.setValues(th,
                            setParams,
                            "/mt:maapi-types/mtypes/types");
            params.add(new ConfXMLParamStart(h,MaapiTypes._mtypes));
            params.add(new ConfXMLParamStart(h,MaapiTypes._servers));
            //Create one server www3 in the list
            ///mt:maapi-types/mtypes/servers/server
            params.add(new ConfXMLParamStart(h,MaapiTypes._server));
            params.add(new ConfXMLParamValue(h,MaapiTypes._srv_name,
                                             new ConfBuf("www3")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._ip,
                                             new ConfBuf("192.168.0.1")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._port,
                                             new ConfUInt16(8080)));
            params.add(new ConfXMLParamStart(h,MaapiTypes._foo));
            //Default
            //params.add(new ConfXMLParamValue(h,MaapiTypes._bar));
            //params.add(new ConfXMLParamLeaf(h,MaapiTypes._baz));
            params.add(new ConfXMLParamStop(h,MaapiTypes._foo));
            //Create First Entry in the list with key "eth0"
            // e.g /mt:maapi-types/mtypes/servers/server{www3}/interface{eth0}

            params.add(new ConfXMLParamStart(h,MaapiTypes._interfaces));
            params.add(new ConfXMLParamStart(h,MaapiTypes._interface));
            params.add(new ConfXMLParamValue(h,MaapiTypes._if_name,
                                             new ConfBuf("eth0")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._mtu,
                                             new ConfInt64(1600)));
            params.add(new ConfXMLParamStop(h,MaapiTypes._interface));
            //Create second entry in the list with key "eth1"
            // e.g /mt:maapi-types/mtypes/servers/server{www3/interface{eth1}
            params.add(new ConfXMLParamStart(h,MaapiTypes._interface));
            params.add(new ConfXMLParamValue(h,MaapiTypes._if_name,
                                             new ConfBuf("eth1")));
            /*
             * Default value is set if we omit a value
             * it requires ofcourse that "default" statement is presence
             * uncomment below if need explicit value!
             *
             */
            //params.add(new ConfXMLParamValue(h,MaapiTypes._mtu,
            //new ConfInt64(1700) );
            params.add(new ConfXMLParamStop(h,MaapiTypes._interface));

            params.add(new ConfXMLParamStop(h,MaapiTypes._interfaces));
            params.add(new ConfXMLParamStop(h,MaapiTypes._server));




            //Create one server www4 in the list with key "www4"
            /// e.g mt:maapi-types/mtypes/servers/server{www4}
            params.add(new ConfXMLParamStart(h,MaapiTypes._server));
            params.add(new ConfXMLParamValue(h,MaapiTypes._srv_name,
                                             new ConfBuf("www4")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._ip,
                                             new ConfBuf("192.168.0.2")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._port,
                                            new ConfUInt16(8080)));




            params.add(new ConfXMLParamStart(h,MaapiTypes._foo));
            params.add(new ConfXMLParamStart(h,MaapiTypes._foo_bar));
            params.add(new ConfXMLParamValue(h,MaapiTypes._bar,
                                             new ConfInt64(11)));
            params.add(new ConfXMLParamValue(h,MaapiTypes._baz,
                                             new ConfInt64(12)));
            params.add(new ConfXMLParamStop(h,MaapiTypes._foo_bar));
            params.add(new ConfXMLParamStop(h,MaapiTypes._foo));
            //Create First Entry in the list with key "eth0"
            params.add(new ConfXMLParamStart(h,MaapiTypes._interfaces));
            params.add(new ConfXMLParamStart(h,MaapiTypes._interface));
            params.add(new ConfXMLParamValue(h,MaapiTypes._if_name,
                                             new ConfBuf("eth0")));
            params.add(new ConfXMLParamValue(h,MaapiTypes._mtu,
                                             new ConfInt64(1600)));



            params.add(new ConfXMLParamStop(h,MaapiTypes._interface));
            //Create second entry in the list with key "eth1"



            params.add(new ConfXMLParamStart(h,MaapiTypes._interface));
            params.add(new ConfXMLParamValue(h,MaapiTypes._if_name,
                                             new ConfBuf("eth1")));
            //Default value
            //params.add(new ConfXMLParamValue(h,MaapiTypes._mtu,
            //new ConfInt64(1500));
            params.add(new ConfXMLParamStop(h,MaapiTypes._interface));



            params.add(new ConfXMLParamStop(h,MaapiTypes._interfaces));


            params.add(new ConfXMLParamStop(h,MaapiTypes._server));




            params.add(new ConfXMLParamStop(h,MaapiTypes._servers));
            params.add(new ConfXMLParamStop(h,MaapiTypes._mtypes));

            setParams = params.toArray(new ConfXMLParam[0]);
            setParamsXML = ConfXMLParam.toXML(setParams,
                                              MaapiTypes._maapi_types_,
                                              new MaapiTypes().uri());
            log.info("\n" + setParamsXML);
            maapi.setValues(th,setParams,
                            "/mt:maapi-types");

            whatHaveWeDone(th);
            maapi.applyTrans(th,true);
            maapi.close();
        } catch(Exception e) {
            log.error("",e);
            System.exit(1);
        }
    }

    private void whatHaveWeDone(int th) throws Exception {
        log.info("so what have we done in transaction th:" + th);
        maapi.diffIterate(th,new MaapiDiffIterate(){
                public DiffIterateResultFlag iterate(ConfObject[] kp,
                                                     DiffIterateOperFlag op,
                                                     ConfObject oldValue,
                                                     ConfObject newValue,
                                                     Object initstate){
                    log.info(new ConfPath(kp).toString()+", " + op +
                             " " + "--> " + newValue);
                    return DiffIterateResultFlag.ITER_RECURSE;
                }
            });
    }
}
