package com.cisco.vmmanager;

import com.cisco.vmmanager.namespaces.vmManager;
import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbSession;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfObjectRef;
import com.tailf.conf.ConfPath;
import com.tailf.dp.services.ServiceContext;
import com.tailf.maapi.Maapi;
import com.tailf.navu.NavuNode;

public class VmManager {

    public static void registerStartRequest(
        ServiceContext context,
        NavuNode service,
        NavuNode root,
        String name) throws ConfException {

        root.getParent().container(vmManager.hash).
            container("vm-manager").
            list("start").
            elem(name).list("allocators").
            sharedCreate(new ConfObjectRef(new ConfPath(service.getKeyPath())));

    }

    public static boolean deviceReady(Cdb cdb, String devName) {
        CdbSession sess = null;
        try {
            sess = cdb.startSession(CdbDBType.CDB_OPERATIONAL);
            boolean b = sess.exists("/devices/device{%s}/vmm:ready", devName);
            if (b)
                b = ((ConfBool) sess.getElem("/devices/device{%s}/vmm:ready",
                                             devName)).booleanValue();
            return b;
        }
        catch (Exception e) {
            return false;
        }
        finally {
            try {
                sess.endSession();
            }
            catch (Exception e) {
                ;
            }
        }
    }

    public static boolean deviceReady(Maapi maapi, String devName) {

        int th = -1;
        try {
            th = maapi.startTrans(Conf.DB_OPERATIONAL, Conf.MODE_READ);
            boolean b = maapi.exists(th, "/devices/device{%s}/vmm:ready",
                                     devName);
            if (b)
                b = ((ConfBool) maapi.getElem(th,
                                "/ncs:devices/device{%s}/vmm:ready",
                                devName)).booleanValue();
            return b;
        }
        catch (Exception e) {
            return false;
        }
        finally {
            try {
                maapi.finishTrans(th);
            }
            catch (Exception e) {
                ;
            }
        }
    }
}