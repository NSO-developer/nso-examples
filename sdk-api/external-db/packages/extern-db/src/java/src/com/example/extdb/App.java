package com.example.extdb;


import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.io.IOException;


import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.conf.ConfException;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfDatetime;
import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfNamespace;
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

    public App() {
        MyDb db = new MyDb();
        db.initDb();
    }



    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.ITERATOR)
    public Iterator<Object> iterator(DpTrans trans,
                                     ConfObject[] keyPath)
        throws DpCallbackException {
        return MyDb.iterator();
    }

    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.GET_NEXT)
    public ConfKey getKey(DpTrans trans, ConfObject[] keyPath,
                          Object obj)
        throws DpCallbackException {
        Item i = (Item) obj;
        return new ConfKey( new ConfObject[] { new ConfInt32(i.key) });
    }


    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.GET_ELEM)
    public ConfValue getElem(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {

        ConfInt32 kv = (ConfInt32) ((ConfKey) keyPath[1]).elementAt(0);
        Item i = MyDb.findItem( kv.intValue() );
        if (i == null) return null; // not found

        // switch on xml elem tag
        ConfTag leaf = (ConfTag) keyPath[0];
        switch (leaf.getTagHash()) {
        case work._key:
            return new ConfInt32(i.key);
        case work._title:
            return new ConfBuf(i.title);
        case work._responsible:
            return new ConfBuf(i.responsible);
        case work._comment:
            return new ConfBuf(i.comment);
        default:
            throw new DpCallbackException("xml tag not handled");
        }
    }

    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.SET_ELEM)
    public int setElem(DpTrans trans, ConfObject[] keyPath,
                       ConfValue newval)
        throws DpCallbackException {
        return Conf.REPLY_ACCUMULATE;
    }

    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.CREATE)
    public int create(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {
        return Conf.REPLY_ACCUMULATE;
    }

    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.REMOVE)
    public int remove(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {
        return Conf.REPLY_ACCUMULATE;
    }

    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.NUM_INSTANCES)
    public int numInstances(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {
        return MyDb.numItems();
    }


    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.GET_OBJECT)
    public ConfValue[] getObject(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {
        ConfInt32 kv = (ConfInt32) ((ConfKey) keyPath[0]).elementAt(0);
        Item i = MyDb.findItem( kv.intValue() );
        if (i == null) return null; // not found
        return getObject(trans, keyPath, i);
    }

    @DataCallback(callPoint=work.callpoint_workPoint,
                  callType=DataCBType.GET_NEXT_OBJECT)
    public ConfValue[] getObject(DpTrans trans, ConfObject[] keyPath,
                                 Object obj)
        throws DpCallbackException {
        Item i = (Item) obj;
        return new ConfValue[] {
            new ConfInt32(i.key),
            new ConfBuf(i.title),
            new ConfBuf(i.responsible),
            new ConfBuf(i.comment)
        };
    }

    @TransCallback(callType=TransCBType.INIT)
    public void init(DpTrans trans) throws DpCallbackException {
        return;
    }

    @TransCallback(callType=TransCBType.TRANS_LOCK)
    public void transLock(DpTrans trans) throws DpCallbackException {
        MyDb.lock();
    }

    @TransCallback(callType=TransCBType.TRANS_UNLOCK)
    public void transUnlock(DpTrans trans) throws DpCallbackException {
        MyDb.unlock();
    }

    @TransCallback(callType=TransCBType.PREPARE)
    public void prepare(DpTrans trans) throws DpCallbackException {
        Item i;
        ConfInt32 kv;
        for (Iterator<DpAccumulate> it = trans.accumulated();
             it.hasNext(); ) {
            DpAccumulate ack= it.next();
            // check op
            switch (ack.getOperation()) {
            case DpAccumulate.SET_ELEM:
                kv = (ConfInt32)  ((ConfKey) ack.getKP()[1]).elementAt(0);
                if ((i = MyDb.findItem( kv.intValue())) == null)
                    break;
                // check leaf tag
                ConfTag leaf = (ConfTag) ack.getKP()[0];
                switch (leaf.getTagHash()) {
                case work._title:
                    i.title = ack.getValue().toString();
                    break;
                case work._responsible:
                    i.responsible = ack.getValue().toString();
                    break;
                case work._comment:
                    i.comment = ack.getValue().toString();
                    break;
                }
                break;
            case DpAccumulate.CREATE:
                kv = (ConfInt32)  ((ConfKey) ack.getKP()[0]).elementAt(0);
                MyDb.newItem(new Item(kv.intValue()));
                break;
            case DpAccumulate.REMOVE:
                kv = (ConfInt32)  ((ConfKey) ack.getKP()[0]).elementAt(0);
                MyDb.removeItem(kv.intValue());
                break;
            }
        }
        try {
            MyDb.save("running.prep");
        } catch (Exception e) {
            throw
              new DpCallbackException("failed to save file: running.prep",
                                      e);
        }
    }

    @TransCallback(callType=TransCBType.ABORT)
    public void abort(DpTrans trans) throws DpCallbackException {
        MyDb.restore("running.DB");
        MyDb.unlink("running.prep");
    }

    @TransCallback(callType=TransCBType.COMMIT)
    public void commit(DpTrans trans) throws DpCallbackException {
        try {
            MyDb.rename("running.prep","running.DB");
        } catch (DpCallbackException e) {
            throw new DpCallbackException("commit failed");
        }
    }

    @TransCallback(callType=TransCBType.FINISH)
    public void finish(DpTrans trans) throws DpCallbackException {
        ;
    }
}
