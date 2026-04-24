/*    -*- Java -*-
 *
 *  $Id$
 *
 */
package com.tailf.manualha;

import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfHaNode;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.SocketFactory;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.ha.Ha;
import com.tailf.ha.HaStatus;
import com.tailf.maapi.Maapi;
import com.tailf.manualha.namespaces.ha;
import com.tailf.navu.NavuContainer;
import com.tailf.ncs.NcsMain;


public class HaActionCb {

    private static boolean initialized;

    static boolean n1;
    static boolean n2;
    final NcsMain main;
    static ConfValue n1val;
    static ConfValue n2val;
    static ConfValue n1addr;
    static ConfValue n2addr;
    static String clusterId;
    private static final Logger LOGGER
            = LogManager.getLogger(HaActionCb.class);

    public HaActionCb(NcsMain main) throws Exception {
        this.main = main;
        initialize(main);
    }

    private static synchronized void initialize(NcsMain main) throws Exception {
        if (!initialized) {
            String nodeName = System.getenv("NCS_HA_NODE");
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
                    n1val = n.container(ha._ha_config_).container(ha._nodes_)
                        .leaf(ha._n1_name_).value();
                    n2val = n.container(ha._ha_config_).container(ha._nodes_)
                        .leaf(ha._n2_name_).value();
                    n1addr = n.container(ha._ha_config_).container(ha._nodes_)
                        .leaf(ha._n1_address_).value();
                    n2addr = n.container(ha._ha_config_).container(ha._nodes_)
                        .leaf(ha._n2_address_).value();
                    clusterId = n.container(ha._ha_config_)
                        .leaf(ha._cluster_id_).value().toString();
                } finally {
                    maapi.finishTrans(th);
                    maapi.endUserSession();
                }
            }

            if (n1val.equals(new ConfBuf(nodeName))) {
                // I am n1
                n1 = true;
                n2 = false;
            } else if (n2val.equals(new ConfBuf(nodeName))) {
                // I am n2
                n2 = true;
                n1 = false;
            } else {
                LOGGER.error("bad ha config, name do not match cdb/env");
                throw new DpCallbackException("bad ha config");
            }

            initialized = true;
        }
    }


    @ActionCallback(callPoint = "ha-point", callType = ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }


    @ActionCallback(callPoint = "ha-point", callType = ActionCBType.ACTION)
    public ConfXMLParam[] action(DpActionTrans trans, ConfTag name,
                                 ConfObject[] kp, ConfXMLParam[] params)
            throws DpCallbackException {

        try (Socket sock = SocketFactory.getSocket(main.getAddress());
                Ha h = new Ha(sock, clusterId)) {

            /* check which action we should invoke */
            switch (name.getTagHash()) {
                case ha._be_primary: {
                    ConfHaNode primary = n1
                            ? new ConfHaNode(n1val, n1addr)
                            : new ConfHaNode(n2val, n2addr);
                    h.bePrimary(primary.getNodeId());
                    return new ConfXMLParamValue[] {};
                }
                case ha._be_secondary: {
                    ConfHaNode primary;
                    ConfValue myNode;
                    if (n1) {
                        // n2 shall be primary
                        primary = new ConfHaNode(n2val, n2addr);
                        myNode = n1val;
                    } else {
                        primary = new ConfHaNode(n1val, n1addr);
                        myNode = n2val;
                    }
                    h.beSecondary(myNode, primary, true);
                    return new ConfXMLParamValue[] {};
                }
                case ha._be_none: {
                    h.beNone();
                    return new ConfXMLParamValue[] {};
                }
                case ha._status: {
                    HaStatus stat = h.status();
                    return new ConfXMLParam[] {new ConfXMLParamValue(
                            new ha(), ha._status_,
                            new ConfBuf(stat.getHaState().toString())
                    )};
                }
                default:
                    return new ConfXMLParamValue[] {};
            }
        } catch (Exception e) {
            throw new DpCallbackException("ActionCb failed", e);
        }
    }
}
