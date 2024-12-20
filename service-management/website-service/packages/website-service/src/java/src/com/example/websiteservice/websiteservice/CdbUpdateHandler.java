package com.example.websiteservice.websiteservice;

import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDiffIterate;
import com.tailf.cdb.CdbSubscription;
import com.tailf.cdb.CdbSubscriptionSyncType;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfObject;
import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.DiffIterateResultFlag;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.navu.KeyPath2NavuNode;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.ApplicationComponent;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
// generated symbols from the device models


/**
 * This class handles CDB update events occuring in the
 * /services/properties/web-site
 *
 */
public class CdbUpdateHandler implements ApplicationComponent, CdbDiffIterate {
    private static Logger LOGGER = LogManager.getLogger(CdbUpdateHandler.class);
    private static Logger PROPLOG = LogManager.getLogger("PROPLOG");

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE)
    private Cdb cdb;
    private CdbSubscription cdbsubscr;
    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    private Maapi maapi;
    private NavuContext ncontext;
    private boolean requestStop = false;

    @Override
    public void init() throws Exception {
        try {
            maapi.startUserSession("system", "system");
            ncontext = new NavuContext(maapi);
            cdbsubscr = cdb.newSubscription();
            cdbsubscr.subscribe(1, 0, "/ncs:services/properties/wsp:web-site");
            cdbsubscr.subscribeDone();
            requestStop = false;
        } catch (Exception e) {
            throw new RuntimeException("Fail in init", e);
        }
    }


    @Override
    public void run() {
        LOGGER.info("subscribed: ready");
        try {
            while (!requestStop) {
                try {
                    int[] spoints = cdbsubscr.read();
                    ncontext.startRunningTrans(Conf.MODE_READ);
                    cdbsubscr.diffIterate(spoints[0], this);
                    ncontext.finishClearTrans();
                } finally {
                    cdbsubscr.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
                }
            }
        } catch (Throwable e) {
            LOGGER.error("Error in subscription:", e);
        }
        requestStop = false;
    }


    @Override
    public void finish() throws Exception {
        requestStop = true;
        try {
            maapi.endUserSession();
        } catch (Throwable ignore) {}
        try {
            ResourceManager.unregisterResources(this);
        } catch (Throwable ignore) {}
    }

    public DiffIterateResultFlag iterate(ConfObject[] kp,
                                         DiffIterateOperFlag op,
                                         ConfObject oldValue,
                                         ConfObject newValue,
                                         Object initstate) {
        try {
            NavuNode node = KeyPath2NavuNode.getNode(kp, ncontext);

            switch (op) {
            case MOP_CREATED:
                PROPLOG.info("CREATED : " + node.getKeyPath());
                break;
            case MOP_VALUE_SET:
                PROPLOG.info("SET : " + node.getKeyPath());
                PROPLOG.info("VALUE : " + newValue);
                break;
            case MOP_MODIFIED:
                PROPLOG.info("MODIFIED : " + node.getKeyPath());
                break;
            case MOP_DELETED:
                PROPLOG.info("DELETED : " + node.getKeyPath());
                break;
            }
        } catch (Exception e) {
            LOGGER.error("",e);
        }
        return DiffIterateResultFlag.ITER_CONTINUE;
    }
}
