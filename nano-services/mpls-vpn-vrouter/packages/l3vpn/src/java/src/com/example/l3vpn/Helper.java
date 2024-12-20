package com.example.l3vpn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.cdb.Cdb;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;
import com.tailf.ncs.ns.Ncs;

final class Helper {
  private static Logger LOGGER = LogManager.getLogger(Helper.class);

  public static void safeclose(Cdb s) {
    try {
      s.close();
    } catch (Exception ignore) {
    }
  }

  public static void safeclose(Maapi maapi) {
    try {
      maapi.getSocket().close();
    } catch (Exception ignore) {
    }
  }

  public static String makeDevName(String tenant, String depname,
                            String vmgroup, String num) {
    return
            tenant + "_" +
                    depname + "_" +
                    vmgroup + "_" +
                    num;
  }

  public static InetAddress increment(InetAddress address)
      throws UnknownHostException {
    byte[] addr = address.getAddress();
    int i = addr.length - 1;
    while (i >= 0 && addr[i] == (byte) 0xff) {
      addr[i] = 0;
      i--;
    }

    addr[i]++;
    return InetAddress.getByAddress(addr);
  }
}
