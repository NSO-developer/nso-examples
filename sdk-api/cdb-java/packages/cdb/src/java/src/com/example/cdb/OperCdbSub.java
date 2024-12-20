package com.example.cdb;

import java.util.Arrays;
import java.util.EnumSet;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.cdb.namespaces.test;
import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbDiffIterate;
import com.tailf.cdb.CdbSession;
import com.tailf.cdb.CdbSubscription;
import com.tailf.cdb.CdbSubscriptionSyncType;
import com.tailf.cdb.CdbSubscriptionType;
import com.tailf.conf.ConfObject;
import com.tailf.conf.DiffIterateFlags;
import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.DiffIterateResultFlag;
import com.tailf.ncs.ApplicationComponent;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;

// This Navu cdb subscriber subscribes to changes under the path
// /t:test/stats-item
// Whenever a change occurs there, the code iterates through the
// change and prints the values. The other examples dealing with
// configuration changes are triggered by the user adding/changing
// some configuration data. This cannot be done here, there is no
// way to manipulate operational data from the CLI. We must add
// that data using some other process.
// For CDB operational data, we can write a Java program which writes
// into the CDB operational store. The code in CreateOperData.java
// does that. Thus to invoke the main() in CreateOperData, we do
// # ant stats -Dop=CREATE -Dkey=k4
// in the directory above where the build.xml file resides.

// This will trigger the operational data subscriber below

// The code runs in an 'application' component, it implements
// the ApplicationComponent interface, this includes the run() method
// so the code will run in a Thread.

public class OperCdbSub implements ApplicationComponent, CdbDiffIterate {
    private static final Logger LOGGER = LogManager.getLogger(OperCdbSub.class);

    // let our ResourceManager inject Cdb sockets to us
    // no explicit creation of creating and opening sockets needed
    @Resource(type = ResourceType.CDB, scope = Scope.INSTANCE,
              qualifier = "sub-sock")
    private Cdb cdbSub;
    @Resource(type = ResourceType.CDB, scope = Scope.INSTANCE,
              qualifier = "data-sock")
    private Cdb cdbData;

    private boolean requestStop;
    private int point;
    private CdbSubscription cdbSubscription;

    public OperCdbSub() {
    }

    public void init() {
        LOGGER.info(" init oper subscriber ");
        try {
            cdbSubscription = cdbSub.newSubscription();
            String path = "/t:test/stats-item";
            point = cdbSubscription.subscribe(
                    CdbSubscriptionType.SUB_OPERATIONAL,
                    1, test.hash, path);
            cdbSubscription.subscribeDone();
            LOGGER.info("subscribeDone");
            requestStop = false;
        } catch (Exception e) {
            LOGGER.error("Fail in init", e);
        }
    }

    public void run() {
        try {
            while (!requestStop) {
                try {
                    int[] points = cdbSubscription.read();
                    CdbSession cdbSession
                            = cdbData.startSession(CdbDBType.CDB_OPERATIONAL);
                    EnumSet<DiffIterateFlags> diffFlags
                            = EnumSet.of(DiffIterateFlags.ITER_WANT_PREV);
                    cdbSubscription.diffIterate(points[0], this, diffFlags,
                                                cdbSession);
                    cdbSession.endSession();
                } finally {
                    cdbSubscription.sync(
                                    CdbSubscriptionSyncType.DONE_OPERATIONAL);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Fail in run shouldrun", e);
        }
        requestStop = false;
    }

    public void finish() {
        requestStop = true;
        try {
            ResourceManager.unregisterResources(this);
        } catch (Exception e) {
            LOGGER.error("Fail in finish", e);
        }
    }

    @Override
    public DiffIterateResultFlag iterate(ConfObject[] kp,
                                         DiffIterateOperFlag op,
                                         ConfObject oldValue,
                                         ConfObject newValue,
                                         Object initstate) {
        LOGGER.info(op + " " + Arrays.toString(kp) + " value: " + newValue);
        switch (op) {
            case MOP_DELETED:
                break;
            case MOP_CREATED:
            case MOP_MODIFIED: {
                break;
            }
            default:
                break;
        }
        return DiffIterateResultFlag.ITER_RECURSE;
    }
}
