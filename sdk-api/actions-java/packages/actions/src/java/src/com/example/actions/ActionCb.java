package com.example.actions;

import java.io.IOException;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;
import com.example.actions.namespaces.*;

public class ActionCb {

    private static Logger LOGGER  = LogManager.getLogger(ActionCb.class);

    @ActionCallback(callPoint="reboot-point", callType=ActionCBType.INIT)
        public void init(DpActionTrans trans) throws DpCallbackException {
        LOGGER.info("userinfo= "+ trans.getUserInfo());
    }


    @ActionCallback(callPoint="reboot-point", callType=ActionCBType.ACTION)
        public ConfXMLParam[] action(DpActionTrans trans, ConfTag name,
                                     ConfObject[] kp, ConfXMLParam[] params)
        throws DpCallbackException {

        try {
            // a primitive check that timeoutsetting calls works.
            trans.actionSetTimeout(10*1000);
        } catch (IOException e) {
            LOGGER.error(e);
            throw new DpCallbackException(e.getMessage());
        }

        ConfXMLParam[] result = null;
        Date date = new Date();
        ConfNamespace ns = new actionTest();

        LOGGER.info("action(uinfo="+trans.getUserInfo()+", name="+name+")");
        /* check which action we should invoke */

        switch (name.getTagHash()) {

        case actionTest._reboot:
            LOGGER.info("action: reboot");
            /* no return value for this action */
            return null;
        case actionTest._restart:
            String mode = ((ConfBuf)params[0].getValue()).toString();
            LOGGER.info("action: restarting mode="+mode);
            result= new ConfXMLParam[] {
                new ConfXMLParamValue(ns,"time", new ConfBuf(date.toString()))};
            break;
        case actionTest._reset:
            String when = ((ConfBuf)params[0].getValue()).toString();
            LOGGER.info("action: reset when="+when);
            result= new ConfXMLParam[] {
                new ConfXMLParamValue(ns, "time",new ConfBuf(date.toString()))};
            break;
        default:
            /* this happens only if we forget to update this code when the
               datamodel has changed. */
            throw new DpCallbackException("got bad operation: "+name);
        }
        return result;
    }

}
