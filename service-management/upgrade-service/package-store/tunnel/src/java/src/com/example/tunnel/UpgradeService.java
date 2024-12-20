package com.example.tunnel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import com.example.vlan.namespaces.vlanService;
import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbLockType;
import com.tailf.cdb.CdbSession;
import com.tailf.cdb.CdbUpgradeSession;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfCdbUpgradePath;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamLeaf;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;

public class UpgradeService {

    public UpgradeService() {
    }

    public static void main(String[] args) throws Exception {
        SocketAddress address;

        String path = System.getenv("NCS_IPC_PATH");
        if (path == null) {
            String host = System.getenv("NCS_IPC_ADDR");
            if (host == null) {
                host = "localhost";
            }
            int port = Optional.ofNullable(System.getenv("NCS_IPC_PORT"))
                    .map(Integer::valueOf).orElse(Conf.PORT);
            address = new InetSocketAddress(host, port);
        } else {
            address = UnixDomainSocketAddress.of(path);
        }

        ArrayList<ConfNamespace> nsList = new ArrayList<ConfNamespace>();
        nsList.add(new vlanService());
        Cdb cdb = new Cdb("cdb-upgrade-sock", address);
        cdb.setUseForCdbUpgrade(nsList);
        CdbUpgradeSession cdbsess =
            cdb.startUpgradeSession(
                    CdbDBType.CDB_RUNNING,
                    EnumSet.of(CdbLockType.LOCK_SESSION,
                               CdbLockType.LOCK_WAIT));


        Maapi maapi = new Maapi(address);
        int th = maapi.attachInit();

        int no = cdbsess.getNumberOfInstances("/services/vlan");
        for(int i = 0; i < no; i++) {
            ConfBuf name =(ConfBuf)cdbsess.getElem("/services/vlan[%d]/name",
                                                   Integer.valueOf(i));
            String nameStr = name.toString();
            System.out.println("SERVICENAME = " + nameStr);

            ConfCdbUpgradePath oldPath =
                new ConfCdbUpgradePath("/ncs:services/vl:vlan{%s}",
                                       name.toString());
            ConfPath newPath = new ConfPath("/services/tunnel{%x}", name);
            maapi.create(th, newPath);

            ConfXMLParam[] oldparams = new ConfXMLParam[] {
                new ConfXMLParamLeaf("vl", "global-id"),
                new ConfXMLParamLeaf("vl", "iface"),
                new ConfXMLParamLeaf("vl", "unit"),
                new ConfXMLParamLeaf("vl", "vid"),
                new ConfXMLParamLeaf("vl", "description"),
            };
            ConfXMLParam[] data =
                cdbsess.getValues(oldparams, oldPath);

            ConfXMLParam[] newparams = new ConfXMLParam[] {
                new ConfXMLParamValue("tl", "gid",       data[0].getValue()),
                new ConfXMLParamValue("tl", "interface", data[1].getValue()),
                new ConfXMLParamValue("tl", "assembly",  data[2].getValue()),
                new ConfXMLParamValue("tl", "tunnel-id", data[3].getValue()),
                new ConfXMLParamValue("tl", "descr",     data[4].getValue()),
            };
            maapi.setValues(th, newparams, newPath);

        }

        cdb.close();
        maapi.close();
    }
}
