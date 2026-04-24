package com.example.commitparamsjava;

import com.example.commitparamsjava.namespaces.*;
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
import java.net.InetAddress;
import com.tailf.maapi.*;
import com.tailf.navu.*;
import com.tailf.conf.*;
import com.tailf.maapi.DryRunResult;
import com.tailf.maapi.DryRunResult.DryRunEntry;
import java.util.Iterator;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class commitparamsjavaRFS {
    private static Logger log = LogManager.getLogger(commitparamsjavaRFS.class);
    private static final ConfNamespace CUSTOM_NS = new commitParamsJava();
    private static final String AUDIT_CONTEXT = "audit-context";
    private static final String TICKET_ID = "ticket-id";
    private static final String ACTION_LABEL = "java-action-demo";
    private static final String ACTION_TICKET_ID = "JAVA-ACTION-001";

    private static String getCustomTicketId(CommitParams cp) {
        boolean insideAuditContext = false;

        for (ConfXMLParam param : cp.getConfXMLParam()) {
            if (!param.getNSHash().equals(CUSTOM_NS.hash())) {
                continue;
            }

            if (param instanceof ConfXMLParamStart
                    && AUDIT_CONTEXT.equals(param.getTag())) {
                insideAuditContext = true;
            } else if (param instanceof ConfXMLParamStop
                    && AUDIT_CONTEXT.equals(param.getTag())) {
                insideAuditContext = false;
            } else if (insideAuditContext
                    && param instanceof ConfXMLParamValue
                    && TICKET_ID.equals(param.getTag())) {
                return param.getValue().toString();
            }
        }

        return null;
    }

    private static CommitParams setCustomTicketId(CommitParams cp,
                                                  String ticketId) {
        List<ConfXMLParam> params = new ArrayList<>(cp.getConfXMLParam());
        params.add(new ConfXMLParamStart(CUSTOM_NS, AUDIT_CONTEXT));
        params.add(new ConfXMLParamValue(CUSTOM_NS, TICKET_ID,
                                         new ConfBuf(ticketId)));
        params.add(new ConfXMLParamStop(CUSTOM_NS, AUDIT_CONTEXT));

        ConfResponse response = new ConfResponse();
        response.term = ConfXMLParam.encodeHKP(
            params.toArray(new ConfXMLParam[0]));
        return new CommitParams(response);
    }

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

    @ServiceCallback(servicePoint="commit-params-java-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws ConfException {

        Template myTemplate = new Template(context,
                                    "commit-params-java-template");
        TemplateVariables myVars = new TemplateVariables();

        try {

            // Detect transaction commit parameters
            NavuContext ctx = service.context();
            Maapi maapi = ctx.getMaapi();
            int th = ctx.getMaapiHandle();
            CommitParams cp = maapi.getTransParams(th);
            log.info("Commit parameters: " + cp);

            if (cp.isDryRun()) {
                log.info("Dry run detected!");
            }
            if (cp.getLabel() != null) {
                log.info("Commit label detected: " + cp.getLabel());
            }
            String ticketId = getCustomTicketId(cp);
            if (ticketId != null) {
                log.info("Custom commit param ticket-id=" + ticketId);
            }

            myTemplate.apply(service, myVars);

        } catch (Exception e) {
            throw new DpCallbackException(e.getMessage(), e);
        }
        return opaque;
    }

    /**
     * Showcase action
     */
    @ActionCallback(callPoint="showcase-java", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            String nsPrefix = "commit-params-java";
            String str = ((ConfKey)kp[0]).toString();

            Maapi maapi = new Maapi(trans.getSocket().getRemoteSocketAddress());
            maapi.startUserSession("admin", "system", new String[]{"admin"});

            // Start a MAAPI transaction
            int th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ_WRITE);

            // Perform some configuration changes on the device
            String intfc = "GigabitEthernet3";
            String device = "ex0";
            String ifPath =
                "/devices/device{%x}/config/r:sys/interfaces/interface{%x}";
            maapi.safeCreate(th,
                            ifPath,
                            new ConfBuf(device),
                            new ConfBuf(intfc));
            ConfPath confPath = new ConfPath(ifPath, device, intfc);
            maapi.safeCreate(th, confPath + "/enabled");
            ConfEnumeration enum_speed =
                ConfEnumeration.getEnumByLabel(confPath + "/speed",
                                                "hundred");
            maapi.setElem(th, enum_speed, confPath + "/speed");

            // Init and set commit parameters
            log.info("Apply commit parameter label, dry-run, and " +
                     "audit-context/ticket-id with a Java action");
            CommitParams cp = maapi.getTransParams(th);
            cp.setLabel(ACTION_LABEL);
            cp.setDryRunNative();
            cp = setCustomTicketId(cp, ACTION_TICKET_ID);

            // Detect transaction commit parameters
            log.info("Commit parameters: " + cp);
            if (cp.getLabel() != null) {
                log.info("Commit label detected: " + cp.getLabel());
            }
            String ticketId = getCustomTicketId(cp);
            if (ticketId != null) {
                log.info("Custom commit param ticket-id=" + ticketId);
            }

            // Apply the transaction and print out the dry-run results
            ApplyResult result = maapi.applyTransParams(th, true, cp);
            log.info("Dry run output: ");
            if (result instanceof DryRunResult) {
                    DryRunResult dryrunResult = (DryRunResult) result;
                    Iterator<DryRunEntry> itr = dryrunResult.iterator();
                    while (itr.hasNext()) {
                            DryRunEntry element = itr.next();
                            log.info(element.getData());
                    }
            }

            maapi.finishTrans(th);
            maapi.endUserSession();
            maapi.close();

          return null;
        } catch (Exception e) {
            throw new DpCallbackException("showcase failed", e);
        }
    }
}
