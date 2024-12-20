package com.example.actions;

import java.io.IOException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.actions.namespaces.actionTest;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;

public class AbortableVerifyAction {

    private static Logger LOGGER =
            LogManager.getLogger(AbortableVerifyAction.class);

    @ActionCallback(callPoint="verify-point", callType=ActionCBType.ACTION)
        public ConfXMLParam[] action(DpActionTrans trans, ConfTag name,
                                     ConfObject[] kp, ConfXMLParam[] params)
        throws DpCallbackException {

        // Set the current thread as opaque value for this transaction.
        // The reason for this is that the abort() if called will execute
        // in another thread and will need to retrieve this thread to
        // be able to terminate execution.
        trans.setTransactionUserOpaque(Thread.currentThread());

        try {
            // make sure we don't time out longrunning action.
            trans.actionSetTimeout(15*1000);
        } catch (IOException e) {
            LOGGER.error(e);
            throw new DpCallbackException(e.getMessage());
        }

        try {
            // Simulate long running action
            Thread.sleep(10*1000);
        } catch (InterruptedException e) {
            LOGGER.info("Verify action was aborted");
            // Just terminate, we dont need to return anything useful
            // an alternative would be to throw an DpCallbackException with
            // some meaningful message, which then would end up in the
            // java vm log.
            return null;
        }

        ConfNamespace ns = new actionTest();
        ConfXMLParam[] result =
            new ConfXMLParam[] {
               new ConfXMLParamValue(ns,"consistent", new ConfBool(true))};

        return result;
    }

    @ActionCallback(callPoint="verify-point", callType=ActionCBType.ABORT)
    public void abort(DpActionTrans trans) throws DpCallbackException {

        // The action() callback is in this implementation expected
        // to set the running thread in the transactions opaque
        Thread running_thread = (Thread) trans.getTransactionUserOpaque();

        if (running_thread != null) {
            running_thread.interrupt();
        } else {
            throw new DpCallbackException("Abort of verify action failed");
        }
    }

}
