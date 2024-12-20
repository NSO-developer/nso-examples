package com.example.qinq;


import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tailf.conf.ConfException;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;


public class QinqJavaTemplate {
    /* Each service should have a name like xxxx_nnn
       where nnn is the vlan id for the service xxxx
    */
    Pattern vlanIdRe =
        Pattern.compile(".*_([0-9]+)\\}$");
    /**
     *  Create callback method.
     *
     *  @param service - The read/write NavuContainer reference
     *                   representing the service node.
     *  @param ncs     - The NavuContainer reference to the ncs root.
     *
     *  @param opaque -  Parameter contains a serializable object.
     *                   This object may be used to transfer
     *                   additional information between consecutive
     *                   calls to the create callback.  It is always
     *                   null in the first call. I.e. when the service
     *                   is first created.
     */

     @ServiceCallback(servicePoint="sp-qinq-java-template",
                      callType=ServiceCBType.CREATE)
     public Properties create(ServiceContext aServiceContext,
                              NavuNode service,
                              NavuNode ncs,
                              Properties opaque)
                              throws DpCallbackException {
         String aVlanId;

         /* The vlan id gets computed in some algorithmic way
            and is used in the template through the XPATH
            variable VLAN_ID.

            This is an example to illustrate the principle.

            We use a naming convention where each service should have
            a suffix of _nnn where nnn is the vlan id
         */
         String theServiceName = service.getName();
         Matcher m = vlanIdRe.matcher(service.getName());

         if (!m.find()) {
             throw
                 new DpCallbackException
                 ("Improper service name, "+
                  "should be xxxx_nnn, where nnn is the vlan id");
         }
         aVlanId = theServiceName.substring(m.start(1), m.end(1));

         try {
             TemplateVariables variables = new TemplateVariables();
             Template template = new Template(aServiceContext,
                                              "qinq-java-template");

             variables.putQuoted("VLAN_ID", aVlanId);
             template.apply(service, variables);

         } catch (ConfException e) {
             throw new DpCallbackException(e.getMessage(), e);
         }
         return opaque;
     }

}
