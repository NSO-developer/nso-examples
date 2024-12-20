package com.example.iface;

import com.example.iface.namespaces.*;
import java.util.List;
import java.util.Properties;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;

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

            if (opaque == null) {
                opaque = new Properties();
            }
            opaque.setProperty("DEVICE",
                               service.leaf("device").valueAsString());
            opaque.setProperty("INTERFACE",
                               service.leaf("interface").valueAsString());
        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
    }


    // The preModification() and postModification() callbacks are optional,
    // and are invoked outside FASTMAP. preModification() is invoked before
    // create, update, or delete of the service, as indicated by the enum
    // ServiceOperationType operation parameter. Conversely
    // postModification() is invoked after create, update, or delete
    // of the service. These functions can be useful e.g. for
    // allocations that should be stored and existing also when the
    // service instance is removed.
    @ServiceCallback(servicePoint = "iface-servicepoint",
                     callType = ServiceCBType.POST_MODIFICATION)
    public Properties postModification(ServiceContext context,
                                       ServiceOperationType operation,
                                       ConfPath path,
                                       Properties opaque)
                                       throws DpCallbackException {
        try {
            // On deprovision
            if (operation == ServiceOperationType.DELETE) {
                NavuContext ctx = context.getRootNode().context();
                Template myTemplate = new Template(ctx, "iface-deactivate");

                // Service no longer exists, use vars instead
                TemplateVariables myVars = new TemplateVariables();
                myVars.putQuoted("DEVICE", opaque.getProperty("DEVICE"));
                myVars.putQuoted("INTERFACE", opaque.getProperty("INTERFACE"));

                NavuNode services = context.getRootNode().container("services");
                myTemplate.apply(services, myVars);
            }
        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
    }


    /**
     * Init method for selftest action
     */
    @ActionCallback(callPoint="iface-self-test", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    /**
     * Selftest action implementation for service
     */
    @ActionCallback(callPoint="iface-self-test", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            // Refer to the service yang model prefix
            String nsPrefix = "iface";
            // Get the service instance key
            String str = ((ConfKey)kp[0]).toString();

          return new ConfXMLParam[] {
              new ConfXMLParamValue(nsPrefix, "success", new ConfBool(true)),
              new ConfXMLParamValue(nsPrefix, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("self-test failed", e);
        }
    }
}
