package com.example.cdb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.cdb.namespaces.test;
import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbLockType;
import com.tailf.cdb.CdbSession;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuList;

public class CreateOperData   {
    private static final Logger LOGGER
            = LogManager.getLogger(CreateOperData.class);

    /**
     * Create entry in:"/t:test/stats-item{%x}" where %x is replaced
     * by ConfBuf(key)
     */
    public static void createEntry(String key)
            throws  IOException, ConfException {

        Maapi maapi = new Maapi(getAddress());
        maapi.startUserSession("system", "system");
        NavuContext operContext = new NavuContext(maapi);
        int th = operContext.startOperationalTrans(Conf.MODE_READ_WRITE);
        NavuContainer mroot = new NavuContainer(operContext);
        LOGGER.debug("ROOT --> " + mroot);

        ConfNamespace ns = new test();
        NavuContainer testModule = mroot.container(ns.hash());
        NavuList list =  testModule.container("test").list("stats-item");
        LOGGER.debug("LIST: --> " + list);

        List<ConfXMLParam> param = new ArrayList<>();
        param.add(new ConfXMLParamValue(ns, "skey", new ConfBuf(key)));
        param.add(new ConfXMLParamValue(ns, "i",
                new ConfInt32(key.hashCode())));
        param.add(new ConfXMLParamStart(ns, "inner"));
        param.add(new ConfXMLParamValue(ns, "l",
                                        new ConfBuf("boogaloo " + key)));
        param.add(new ConfXMLParamStop(ns, "inner"));
        list.setValues(param.toArray(new ConfXMLParam[0]));
        maapi.applyTrans(th, false);
        maapi.finishTrans(th);
        maapi.endUserSession();
        maapi.close();
    }

    private static SocketAddress getAddress() {
        String path = System.getenv("NCS_IPC_PATH");
        if (path == null) {
            return new InetSocketAddress("127.0.0.1", Conf.NCS_PORT);
        } else {
            return UnixDomainSocketAddress.of(path);
        }
    }

    /**
     * Delete entry in:"/t:test/stats-item{%x}" where %x is replaced
     * by ConfBuf(key)
     */
    public static void deleteEntry(String key)
            throws IOException, ConfException {
        Cdb c = new Cdb("writer", getAddress());

        CdbSession sess = c.startSession(CdbDBType.CDB_OPERATIONAL,
                                         EnumSet.of(CdbLockType.LOCK_REQUEST,
                                                    CdbLockType.LOCK_WAIT));
        ConfPath path = new ConfPath("/t:test/stats-item{%x}",
                                     new ConfKey(new ConfBuf(key)));
        sess.delete(path);
        sess.endSession();
        c.close();
    }

    public static void main(String[] arg) throws Exception {
        if (arg.length != 2) {
            err();
        } else {
            if (arg[0].equals("CREATE")) {
                createEntry(arg[1]);
            } else if (arg[0].equals("DELETE")) {
                deleteEntry(arg[1]);
            } else {
                err();
            }
        }
    }

    static void err() {
        System.err.println("ant stats -Dop=(CREATE|DELETE) -Dkey=(key)");
        System.exit(1);
    }
}
