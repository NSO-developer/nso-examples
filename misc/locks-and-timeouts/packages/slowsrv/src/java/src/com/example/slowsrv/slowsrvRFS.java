package com.example.slowsrv;

import com.example.slowsrv.namespaces.*;
import java.util.List;
import java.util.Properties;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;


public class slowsrvRFS {


    @ServiceCallback(servicePoint="slowsrv-servicepoint",
                     callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
        throws DpCallbackException {

        String servicePath = null;
        try {
            servicePath = service.getKeyPath();
            ConfInt32 v = (ConfInt32) service.leaf("sleep-secs").value();
            int sleep = v.intValue();
            System.out.println("Sleeping  " + sleep + " secs");
            Thread.sleep(sleep * 1000);
        } catch (Exception e) {
            throw new DpCallbackException("Cannot create service " +
                                          servicePath, e);
        }
        return opaque;
    }

}
