package com.example.dp;

import com.tailf.conf.*;
import com.example.dp.namespaces.*;
import com.tailf.dp.*;
import com.tailf.maapi.*;
import com.tailf.ncs.NcsMain;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.dp.proto.TransCBType;
import com.tailf.dp.proto.ActionCBType;
import java.util.ArrayList;
import java.util.Iterator;
import java.net.Socket;

public class dpDp   {

    private final NcsMain main;
    private static Maapi m = null;
    private static ArrayList<String> fakeData;

    public dpDp(NcsMain main) {
        this.main = main;
    }

    @DataCallback(callPoint="test-stats-cp",
                  callType=DataCBType.ITERATOR)
    public Iterator<? extends Object> iterator(DpTrans trans,
                                     ConfObject[] keyPath)
        throws DpCallbackException {

        return fakeData.iterator();
    }

    @DataCallback(callPoint="test-stats-cp",
                  callType=DataCBType.GET_NEXT)
    public ConfKey getKey(DpTrans trans, ConfObject[] keyPath,
                          Object obj) {

        ConfBuf b = new ConfBuf((String)obj);
        return new ConfKey( new ConfObject[] { b });
    }


    @DataCallback(callPoint="test-stats-cp",
                  callType=DataCBType.GET_ELEM)
    public ConfValue getElem(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {

        System.out.println("kp[1] = " + keyPath[1]);
        ConfKey k = (ConfKey)keyPath[1];
        if (k.elementAt(0).toString().equals("k1")) {
            try {
                System.out.println("GetElem callback sleeping 6 secs");
                Thread.sleep(6000);
            }
            catch (Exception e) {
            }
        }
        return new ConfInt32(44);
    }

    @DataCallback(callPoint="test-stats-cp",
                  callType=DataCBType.NUM_INSTANCES)
    public int numInstances(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {
        return fakeData.size();
    }


    @TransCallback(callType=TransCBType.INIT)
    public void init(DpTrans trans) throws DpCallbackException {
        try {
            if (dpDp.m == null) {
                // Need a Maapi socket so that we can attach
                dpDp.m = new Maapi(main.getAddress());
                dpDp.fakeData = new ArrayList<String>();
                dpDp.fakeData.add("k1");
                dpDp.fakeData.add("k2");
            }

            dpDp.m.attach(trans.getTransaction(), 0,
                           trans.getUserInfo().getUserId());

            return;
        }
        catch (Exception e) {
            throw new DpCallbackException("Failed to attach", e);
        }
    }


    @TransCallback(callType=TransCBType.FINISH)
    public void finish(DpTrans trans) throws DpCallbackException {
        try {
            m.detach(trans.getTransaction());
        }
        catch (Exception e) {
            ;
        }
    }

    @ActionCallback(callPoint="test-action", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    @ActionCallback(callPoint="test-action", callType=ActionCBType.ACTION)
    public ConfXMLParam[] a1(DpActionTrans trans, ConfTag name,
                             ConfObject[] kp, ConfXMLParam[] params)
        throws DpCallbackException {
        try {
            int sleep = ((ConfInt32)params[0].getValue()).intValue();
            System.out.println("Action callback sleeping " + sleep + " secs");
            Thread.sleep(sleep*1000);
            return null;
        } catch (Exception e) {
            throw new DpCallbackException("sleep failed", e);
        }
    }
}
