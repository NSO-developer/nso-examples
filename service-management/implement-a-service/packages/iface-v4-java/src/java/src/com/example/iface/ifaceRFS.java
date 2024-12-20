package com.example.iface;

import com.example.iface.namespaces.*;
import java.util.List;
import java.util.Properties;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.maapi.*;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;
import java.net.*;

public class ifaceRFS {


    /**
     * Create callback method.
     * This method is called when a service instance committed due to a create
     * or update event.
     *
     * This method returns a opaque as a Properties object that can be null.
     * If not null it is stored persistently by Ncs.
     * This object is then delivered as argument to new calls of the create
     * method for this service (fastmap algorithm).
     * This way the user can store and later modify persistent data outside
     * the service model that might be needed.
     *
     * @param context - The current ServiceContext object
     * @param service - The NavuNode references the service node.
     * @param ncsRoot - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws ConfException
     */

    @ServiceCallback(servicePoint="iface-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws ConfException {

        try {
            String cidr_mask_str = service.leaf("cidr-netmask").valueAsString();
            int cidr_mask = Integer.parseInt(cidr_mask_str);

            long tmp_mask = 0xffffffffL << (32 - cidr_mask);
            String quad_mask = ((tmp_mask >> 24) & 0xff) +
                "." + ((tmp_mask >> 16) & 0xff) +
                "." + ((tmp_mask >> 8) & 0xff) +
                "." + ((tmp_mask) & 0xff);

            Template myTemplate = new Template(context, "iface-template");
            TemplateVariables myVars = new TemplateVariables();
            myVars.putQuoted("NETMASK", quad_mask);
            myTemplate.apply(service, myVars);
        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
    }

    @ActionCallback(callPoint="iface-test-enabled",
                    callType=ActionCBType.ACTION)
    public ConfXMLParam[] test_enabled(DpActionTrans trans, ConfTag name,
                                       ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        // Ensure socket gets closed on errors, also ending any ongoing
        // session and transaction
        try (Maapi maapi
                = new Maapi(trans.getSocket().getRemoteSocketAddress())) {
            maapi.startUserSession("admin", "system");

            NavuContext context = new NavuContext(maapi);
            context.startRunningTrans(Conf.MODE_READ);

            NavuContainer root = new NavuContainer(context);
            NavuContainer service =
                (NavuContainer)KeyPath2NavuNode.getNode(kp, context);

            String status = "unknown";    // Replace with your own code that
                                          // checks e.g. operational status of
                                          // the interface

            String nsPrefix = name.getPrefix();
            return new ConfXMLParam[] {
                new ConfXMLParamValue(nsPrefix, "status", new ConfBuf(status)),
            };
        } catch (Exception e) {
            throw new DpCallbackException(name.toString() + " action failed",
                e);
        }
    }
}
