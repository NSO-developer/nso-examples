package com.example.maapi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;

import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.*;
import com.tailf.maapi.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class SetElem {
    private Maapi  maapi;
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
        new SetElem(address).setTypesWithMaapi();
    }

    public SetElem(SocketAddress address) {
        try {
            maapi = new Maapi(address);
        } catch(Exception e) {
            log.error("",e);
        }
    }

    public void setTypesWithMaapi() {
        try {
            maapi.startUserSession("admin", "maapi", new String[] { "admin" });

            int th   = maapi.startTrans(Conf.DB_RUNNING,Conf.MODE_READ_WRITE);
            ConfInt8 _int8 = new ConfInt8(77);
            maapi.setElem(th, _int8,"/mt:maapi-types/mtypes/types/c_int8");

            ConfUInt8 _uint8 = new ConfUInt8(88);
            maapi.setElem(th, _uint8,"/mt:maapi-types/mtypes/types/c_uint8");
            /* c_int16  */
            ConfInt16 _int16 = new ConfInt16(88);
            maapi.setElem(th, _int16,"/mt:maapi-types/mtypes/types/c_int16");

            ConfUInt16 _uint16 = new ConfUInt16(99);
            maapi.setElem(th, _uint16,"/mt:maapi-types/mtypes/types/c_uint16");

            ConfInt32 _int32 = new ConfInt32(1010199);
            maapi.setElem(th, _int32,"/mt:maapi-types/mtypes/types/c_int32");
            /* c_int64  */
            ConfInt64 _int64 = new ConfInt64(88888);
            maapi.setElem(th, _int64,"/mt:maapi-types/mtypes/types/c_int64");

            ConfBool _bool = new ConfBool(true);
            maapi.setElem(th, _bool,"/mt:maapi-types/mtypes/types/b");
            ConfDouble _double = new ConfDouble(4.66);

            maapi.setElem(th, _double,"/mt:maapi-types/mtypes/types/f");
            ConfIPv4 _ipv4 = new ConfIPv4(new int[] {1,2,3,4});

            maapi.setElem(th, _ipv4,"/mt:maapi-types/mtypes/types/c_ipv4");
            ConfIPv6 _ipv6 = new ConfIPv6("fe80::");
            maapi.setElem(th, _ipv6,"/mt:maapi-types/mtypes/types/c_ipv6");

            ConfDatetime _datetime =
                new ConfDatetime("2012-10-16T21:32:52+02:00");
            maapi.setElem(th, _datetime,
                          "/mt:maapi-types/mtypes/types/datetime");

            ConfDate _date = new ConfDate("2012-08-30");
            maapi.setElem(th, _date,"/mt:maapi-types/mtypes/types/date");

            ConfDuration _duration = new ConfDuration("P1Y");
            maapi.setElem(th, _duration,
                          "/mt:maapi-types/mtypes/types/duration");

            ConfEnumeration _enum =
                ConfEnumeration
                .getEnumByLabel("/mt:maapi-types/mtypes/types/enum","15-mins");
            maapi.setElem(th, _enum,"/mt:maapi-types/mtypes/types/enum");

            ConfBit32 _bit32 =  new ConfBit32(2);
            maapi.setElem(th, _bit32,"/mt:maapi-types/mtypes/types/bits");

            ConfObjectRef _objectref =
                new ConfObjectRef("/mt:maapi-types/mtypes/servers" +
                                  "/server[srv-name='smtp']/ip");
            maapi.setElem(th, _objectref,
                          "/mt:maapi-types/mtypes/types/objectref");

            ConfIPv4Prefix _ipv4prefix = new ConfIPv4Prefix("192.168.0.0/16");
            maapi.setElem(th, _ipv4prefix,
                          "/mt:maapi-types/mtypes/types/ipv4Prefix");

            ConfIPv6Prefix _ipv6prefix =
                new ConfIPv6Prefix("2001:DB8::1428:57A8/125");
            maapi.setElem(th, _ipv6prefix,
                          "/mt:maapi-types/mtypes/types/ipv6Prefix");

            ConfHexList _hexList = new ConfHexList("3c:07:54:ff:ff:06");
            maapi.setElem(th,_hexList,
                          "/mt:maapi-types/mtypes/types/mac");

            ConfList _ilist = new ConfList();
            _ilist.addElem(new ConfUInt32(10));
            _ilist.addElem(new ConfUInt32(11));
            _ilist.addElem(new ConfUInt32(12));
            _ilist.addElem(new ConfUInt32(13));
            maapi.setElem(th,_ilist,"/mt:maapi-types/mtypes/ilist");

            whatHaveWeDone(th);
            maapi.applyTrans(th,true);
            maapi.close();
        }catch(Exception e){
            log.error("",e);
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
