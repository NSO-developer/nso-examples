/*    -*- Java -*-
 *
 *  $Id$
 *
 */
package com.tailf.manualha;

import java.util.ArrayList;
import java.util.Date;
import java.io.IOException;

import com.tailf.conf.*;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.ha.*;
import com.tailf.maapi.Maapi;
import com.tailf.ncs.NcsMain;
import java.net.InetAddress;
import java.net.Socket;
import com.tailf.manualha.namespaces.*;
import com.tailf.ncs.OsEnv;
import com.tailf.ncs.NcsMain;
import java.util.Properties;
import com.tailf.cdb.Cdb;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class HaActionCb {

    static boolean n1, n2;
    final NcsMain main;;
    static ConfValue n1val, n2val, n1addr, n2addr;
    static String clusterId;
    static Properties props = null;
    private static Logger LOGGER  = LogManager.getLogger(HaActionCb.class);

    public HaActionCb(NcsMain main) throws Exception {
        this.main = main;

        if (HaActionCb.props == null) {
            HaActionCb.props = OsEnv.get();
            String nodeName = HaActionCb.props.getProperty("NCS_HA_NODE");
            if (nodeName == null) {
                LOGGER.error("env NCS_HA_NODE not set");
                throw new DpCallbackException("env NCS_HA_NODE not set");
            }
            try (Maapi maapi = new Maapi(main.getAddress())) {
                maapi.startUserSession("admin", "system");
                int th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
                try {
                    NavuContainer n = new NavuContainer(
                         maapi, th, new ha().hash());
                    HaActionCb.n1val =
                        n.container("ha-config").container("nodes").
                        leaf("n1-name").value();
                    HaActionCb.n2val =
                        n.container("ha-config").container("nodes").
                        leaf("n2-name").value();
                    HaActionCb.n1addr =
                        n.container("ha-config").container("nodes").
                        leaf("n1-address").value();
                    HaActionCb.n2addr =
                        n.container("ha-config").container("nodes").
                        leaf("n2-address").value();
                    HaActionCb.clusterId =  n.container("ha-config").
                        leaf("cluster-id").value().toString();
                } finally {
                    maapi.finishTrans(th);
                    maapi.endUserSession();
                }
            }

            if (HaActionCb.n1val.equals(new ConfBuf(nodeName))) {
                // I am n1
                HaActionCb.n1 = true;
                HaActionCb.n2 = false;
            }
            else if (HaActionCb.n2val.equals(new ConfBuf(nodeName))) {
                // I am n2
                HaActionCb.n2 = true;
                HaActionCb.n1 = false;
            }
            else {
                LOGGER.error("bad ha config, name do not match cdb/env");
                HaActionCb.props = null;
                throw new DpCallbackException("bad ha config");
            }
        }
    }


    @ActionCallback(callPoint="ha-point", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }


    @ActionCallback(callPoint="ha-point", callType=ActionCBType.ACTION)
    public ConfXMLParam[] action(DpActionTrans trans, ConfTag name,
                                 ConfObject[] kp, ConfXMLParam[] params)
        throws DpCallbackException {

        ConfXMLParam[] result = null;
        Date date = new Date();

        try {

            ConfHaNode primary;

            /* check which action we should invoke */
            switch (name.getTagHash()) {
            case ha._be_primary: {
                Socket sock = SocketFactory.getSocket(this, main.getAddress());
                Ha h = new Ha(sock, HaActionCb.clusterId);
                if (HaActionCb.n1) {
                    primary = new ConfHaNode(HaActionCb.n1val,
                                             HaActionCb.n1addr);
                }
                else {
                    primary = new ConfHaNode(HaActionCb.n2val,
                                             HaActionCb.n2addr);
                }
                h.bePrimary(primary.getNodeId());
                sock.close();
                return null;
            }
            case ha._be_secondary: {
                ConfValue myNode;
                if (HaActionCb.n1) {
                    // n2 shall be primary
                    primary = new ConfHaNode(HaActionCb.n2val,
                                             HaActionCb.n2addr);
                    myNode = HaActionCb.n1val;
                }
                else {
                    primary = new ConfHaNode(HaActionCb.n1val,
                                             HaActionCb.n1addr);
                    myNode = HaActionCb.n2val;
                }
                Socket sock = SocketFactory.getSocket(this, main.getAddress());
                Ha h = new Ha(sock, HaActionCb.clusterId);
                h.beSecondary(myNode, primary, true);
                sock.close();
                return null;
            }
            case ha._be_none: {
                Socket sock = SocketFactory.getSocket(this, main.getAddress());
                Ha h = new Ha(sock, HaActionCb.clusterId);
                h.beNone();
                sock.close();
                return null;
            }
            case ha._status: {
                Socket sock = SocketFactory.getSocket(this, main.getAddress());
                Ha h = new Ha(sock, HaActionCb.clusterId);
                HaStatus stat = h.status();
                sock.close();
                result = new ConfXMLParam[] {
                    new ConfXMLParamValue(
                        new ha(), ha._status_,
                        new ConfBuf( stat.getHaState().toString() ))};
                return result;
            }
            default:
                return null;
            }
        } catch (Exception e) {
            throw new DpCallbackException("ActionCb failed");
        }
    }
}
