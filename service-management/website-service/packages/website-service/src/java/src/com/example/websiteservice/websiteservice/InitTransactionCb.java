package com.example.websiteservice.websiteservice;

import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.proto.TransCBType;
import com.tailf.maapi.Maapi;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;

public class InitTransactionCb {

    @Resource(type=ResourceType.MAAPI, scope=Scope.CONTEXT,
              qualifier="TransMaapi")
    public Maapi maapi;
    private DpTrans trans;

    public InitTransactionCb() {
    }

    public String toString(){
        return this.getClass().getSimpleName()+"["+maapi +","+trans+"]";
    }

    @TransCallback(callType = { TransCBType.INIT })
    public void init(DpTrans trans) throws DpCallbackException {
        try {
            this.trans=trans;
            maapi.attach(trans.getTransaction(), new Ncs().hash(),
                         trans.getUserInfo().getUserId() );
        }
        catch (Exception e) {
            throw new DpCallbackException(e.toString());
        }
    }

    @TransCallback(callType = { TransCBType.FINISH })
    public void finish(DpTrans trans) throws DpCallbackException {
        return;
    }

}
