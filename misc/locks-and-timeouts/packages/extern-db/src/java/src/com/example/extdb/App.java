package com.example.extdb;


import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.io.IOException;


import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.conf.*;
import com.tailf.dp.Dp;
import com.tailf.dp.DpAccumulate;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.dp.proto.TransCBType;

import com.example.extdb.namespaces.*;


public class App  {
    private static Logger LOGGER = LogManager.getLogger(App.class);


    static int proposedData = 0;
    static int storedData = 0;


    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.GET_ELEM)
    public ConfValue getElem(DpTrans trans, ConfObject[] keyPath) {
        return new ConfInt32(storedData);
    }

    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.SET_ELEM)
    public int setElem(DpTrans trans, ConfObject[] keyPath,
                       ConfValue newval)
        throws DpCallbackException {
        proposedData = ((ConfInt32)newval).intValue();
        return Conf.REPLY_OK;
    }

    @TransCallback(callType=TransCBType.INIT)
    public void init(DpTrans trans) throws DpCallbackException {
        return;
    }

    @TransCallback(callType=TransCBType.TRANS_LOCK)
    public void transLock(DpTrans trans) throws DpCallbackException {
    }

    @TransCallback(callType=TransCBType.TRANS_UNLOCK)
    public void transUnlock(DpTrans trans) throws DpCallbackException {
    }

    @TransCallback(callType=TransCBType.PREPARE)
    public void prepare(DpTrans trans) throws DpCallbackException {
    }

    @TransCallback(callType=TransCBType.ABORT)
    public void abort(DpTrans trans) throws DpCallbackException {
    }

    @TransCallback(callType=TransCBType.COMMIT)
    public void commit(DpTrans trans) throws DpCallbackException {
        try {
            Thread.sleep(proposedData * 1000);
        }
        catch (Exception e ) {
        }
        storedData = proposedData;
        proposedData = 0;
    }

    @TransCallback(callType=TransCBType.FINISH)
    public void finish(DpTrans trans) throws DpCallbackException {
        ;
    }
}
