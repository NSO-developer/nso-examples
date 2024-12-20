package com.example.cdb;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDiffIterate;
import com.tailf.cdb.CdbSubscription;
import com.tailf.cdb.CdbSubscriptionSyncType;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfObject;
import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.DiffIterateResultFlag;
import com.tailf.conf.ErrorCode;
import com.tailf.ncs.ApplicationComponent;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;


// This low level cdb subscriber subscribes to changes under the path
// /devices/device{ex0}/config
// Whenever a change occurs there, the code iterates through the
// change and prints the values. Thus to trigger this subscription code
// go into the ncs_cli and commit any change under the subscription
// path. For example:

// # ncs_cli -u admin
// admin connected from 127.0.0.1 using console on iron.local
// admin@iron> configure
// dmin@iron% set devices device ex0 config sys syslog server 4.5.6.7 enabled
// [ok][2012-07-05 12:57:59]

// [edit]
// admin@iron% commit
// Commit complete.


// will trigger the subscription code, the code logs and the data will end up
// in ./logs/ncs-java-vm.log (relative to where the ncs daemon executes)


// The code runs in an 'application' component, it implements
// the ApplicationComponent interface, this includes the run() method
// so the code will run in a Thread.


public class PlainCdbSub implements ApplicationComponent  {
    private static final Logger LOGGER
            = LogManager.getLogger(PlainCdbSub.class);

    @Resource(type = ResourceType.CDB, scope = Scope.INSTANCE,
              qualifier = "plain")
    private Cdb cdb;

    private CdbSubscription sub;
    private int subId;
    private boolean requestStop;

    public PlainCdbSub() {
    }

    public void init() {
        try {
            LOGGER.info(" init cdb subscriber ");
            sub = new CdbSubscription(cdb);
            String str = "/devices/device{ex0}/config";
            subId = sub.subscribe(1, new Ncs(), str);
            sub.subscribeDone();
            LOGGER.info("subscribeDone");
            requestStop = false;
        } catch (Exception e) {
            throw new RuntimeException("FAIL in init", e);
        }
    }

    public void run() {
        try {
            while (!requestStop) {
                try {
                    sub.read();
                    sub.diffIterate(subId, new Iter());
                } finally {
                    sub.sync(CdbSubscriptionSyncType.DONE_SOCKET);
                }
            }
        } catch (ConfException e) {
            if (e.getErrorCode() == ErrorCode.ERR_EOF) {
                // Triggered by finish method
                // if we throw further NCS JVM will try to restart
                // the package
                LOGGER.warn(" Socket Closed!");
            } else {
                throw new RuntimeException("FAIL in run", e);
            }
        } catch (Exception e) {
            LOGGER.warn("Exception:" + e.getMessage());
            throw new RuntimeException("FAIL in run", e);
        } finally {
            requestStop = false;
            LOGGER.warn(" run end ");
        }
    }

    public void finish() {
        requestStop = true;
        LOGGER.warn(" PlainSub in finish () =>");
        try {
            // ResourceManager will close the resource (cdb) used by this
            // instance that triggers ConfException with ErrorCode.ERR_EOF
            // in run method
            ResourceManager.unregisterResources(this);
        } catch (Exception e) {
            throw new RuntimeException("FAIL in finish", e);
        }
        LOGGER.warn(" PlainSub in finish () => ok");
    }

    private class Iter implements CdbDiffIterate  {
        public DiffIterateResultFlag iterate(ConfObject[] kp,
                                             DiffIterateOperFlag op,
                                             ConfObject oldValue,
                                             ConfObject newValue,
                                             Object state) {
            try {
                String kpString = Conf.kpToString(kp);
                LOGGER.info("diffIterate: kp= " + kpString + ", OP=" + op
                            + ", old_value=" + oldValue + ", new_value="
                            + newValue);
                return DiffIterateResultFlag.ITER_RECURSE;
            } catch (Exception e) {
                return DiffIterateResultFlag.ITER_CONTINUE;
            }
        }
    }
}

