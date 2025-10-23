package com.example.l3vpn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.cisco.escned.namespaces.esc;
import com.cisco.vmmanager.VmManager;
import com.example.l3vpn.namespaces.l3vpn;
import com.tailf.cdb.Cdb;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIPPrefix;
import com.tailf.conf.ConfIPv4Prefix;
import com.tailf.conf.ConfIPv4AndPrefixLen;
import com.tailf.conf.ConfList;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfUInt32;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.NanoServiceCallback;
import com.tailf.dp.proto.NanoServiceCBType;
import com.tailf.dp.services.NanoServiceContext;
import com.tailf.dp.services.ServiceContext;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;

public class l3vpnRFS {
    private static Logger LOGGER = LogManager.getLogger(l3vpnRFS.class);

    @Resource(type = ResourceType.CDB, scope = Scope.INSTANCE,
        qualifier = "l3vpn-cdb")
    private Cdb cdb;

    @NanoServiceCallback(servicePoint = "l3vpn-servicepoint",
                         componentType="l3vpn:l3vpn-init",
                         state="ncs:init",
                         callType = NanoServiceCBType.CREATE)
    public Properties initCreate(NanoServiceContext context,
                                        NavuNode service,
                                        NavuNode ncsRoot,
                                        Properties opaque,
                                        Properties componentProperties)
                                            throws ConfException {
        LOGGER.info("reached create " + context.getComponentName() +
                           ":" + context.getState());
        opaque.setProperty("dumb-not-null", "true");
        String name = service.leaf(l3vpn._name_).valueAsString();
        String deploymentName = "vpn";
        String virtualPEName =
            Helper.makeDevName(name, deploymentName, "CSR", "esc0");
        opaque.setProperty("DEPNAME", deploymentName);
        opaque.setProperty("DEVNAME", virtualPEName);
        opaque.setProperty("DEVNAME_EXPR", "'" + virtualPEName + "'");
        String vmName = name+"_"+deploymentName+"_"+"CSR";
        opaque.setProperty("VMNAME", vmName);
        opaque.setProperty("VMNAME_EXPR", "'" + vmName + "'");
        context.setReached();
        return opaque;
    }

    @NanoServiceCallback(servicePoint = "l3vpn-servicepoint",
    componentType="l3vpn:l3vpn-virtual", state="ncs:init",
    callType = NanoServiceCBType.CREATE)
    public Properties virtualReadyDelete(NanoServiceContext context,
                                         NavuNode service,
                                         NavuNode ncsRoot,
                                         Properties opaque,
                                         Properties componentProperties)
                                            throws ConfException, IOException {
        String endpointName = context.getComponentName();
        LOGGER.info("reached create " + endpointName +
                        ":" + context.getState());
        String name = service.leaf(l3vpn._name_).valueAsString();
        try {
            NavuList notifList = ncsRoot.container("devices").list("device")
                .elem("esc0").container("notifications")
                .container("received-notifications").list("notification");
            for (NavuNode childEntry : notifList) {
                String tenant = childEntry.container("data")
                    .namespace(esc.id).container("escEvent")
                    .leaf("tenant").valueAsString();
                if (tenant.equals(name)) {
                    String eventTime = childEntry.leaf("event-time")
                                                 .valueAsString();
                    String seqNo = childEntry.leaf("sequence-no")
                                             .valueAsString();
                    String[] keyArr = {eventTime, seqNo};
                    notifList.delete(keyArr);
                }
            }
            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("error in " + endpointName +
                                        ":init",e);
        }
        return opaque;
    }

    @NanoServiceCallback(servicePoint = "l3vpn-servicepoint",
        componentType="l3vpn:l3vpn-virtual", state="l3vpn:pe-created",
        callType = NanoServiceCBType.CREATE)
    public Properties virtPECreatedCreate(NanoServiceContext context,
                                          NavuNode service,
                                          NavuNode ncsRoot,
                                          Properties opaque,
                                          Properties componentProperties)
                                              throws ConfException {
        String endpointName = context.getComponentName();
        LOGGER.info("reached create " + endpointName +
                           ":" + context.getState());
        String deploymentName = opaque.getProperty("DEPNAME");
        String virtualPEName = opaque.getProperty("DEVNAME");
        String vmName = opaque.getProperty("VMNAME");
        try {
            String name = service.leaf(l3vpn._name_).valueAsString();
            NavuList endpoints = service.list(l3vpn._endpoint);
            NavuContainer endpoint = endpoints.elem(endpointName);
            String ceName = endpoint.leaf(l3vpn._ce_device_).valueAsString();
            createVirtualPE(context, service, endpoint, ncsRoot,
                            ceName, name, deploymentName, virtualPEName,
                            vmName);

            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("error in " + endpointName +
                                          ":peCreated",e);
        }
        return opaque;
    }

    @NanoServiceCallback(servicePoint = "l3vpn-servicepoint",
        componentType="l3vpn:l3vpn-virtual", state="l3vpn:ce-vpe-topo-added",
        callType = NanoServiceCBType.CREATE)
    public Properties virtCeVpeCreate(NanoServiceContext context,
                                      NavuNode service,
                                      NavuNode ncsRoot,
                                      Properties opaque,
                                      Properties componentProperties)
                                          throws ConfException {
        String endpointName = context.getComponentName();
        LOGGER.info("reached create " + endpointName +
                           ":" + context.getState());
        try {
            String name = service.leaf(l3vpn._name_).valueAsString();
            NavuList endpoints = service.list(l3vpn._endpoint);
            NavuContainer endpoint = endpoints.elem(endpointName);
            String ceName = endpoint.leaf(l3vpn._ce_device_).valueAsString();
            String deploymentName = opaque.getProperty("DEPNAME");
            String virtualPEName = opaque.getProperty("DEVNAME");
            NavuContainer topology = ncsRoot.getParent().
                container(l3vpn.uri).container(l3vpn._topology);

            if (!VmManager.deviceReady(cdb, virtualPEName)) {
                LOGGER.info("->DEVICE NOT READY");
                context.setNotReached();
            } else {
                LOGGER.info("->DEVICE READY");
                addToTopologyRole(ncsRoot, virtualPEName, "pe");

                NavuContainer conn = getConnection(topology,
                       endpoint.leaf(l3vpn._ce_device).valueAsString(),
                       "pe");
                if (!addToTopology(service, ncsRoot, conn,
                                   ceName, "GigabitEthernet0/8",
                                   virtualPEName, "GigabitEthernet2",
                                   ceName, Settings.ipPoolPE_CE)) {
                    LOGGER.info("->ADDTOPO NOT READY");

                    context.setNotReached();
                } else {
                    context.setReached();
                }
            }
        } catch (Throwable e) {
            throw new DpCallbackException("error in " + endpointName +
                                          ":ce-vpe-topo-added",e);
        }
        return opaque;
    }

    @NanoServiceCallback(servicePoint = "l3vpn-servicepoint",
                         componentType="l3vpn:l3vpn-virtual",
                         state="l3vpn:vpe-p0-topo-added",
                         callType = NanoServiceCBType.CREATE)
    public Properties virtVpeP0Create(NanoServiceContext context,
                                      NavuNode service,
                                      NavuNode ncsRoot,
                                      Properties opaque,
                                      Properties componentProperties)
                                          throws ConfException {
        String endpointName = context.getComponentName();
        LOGGER.info("reached create " + endpointName +
                           ":" + context.getState());
        try {
            String name = service.leaf(l3vpn._name_).valueAsString();
            String deploymentName = opaque.getProperty("DEPNAME");
            String virtualPEName = opaque.getProperty("DEVNAME");

            if (!addToTopology(service, ncsRoot, null,
                               "p0", "GigabitEthernet0/8",
                               virtualPEName, "GigabitEthernet1",
                               "p0" + "_" + virtualPEName,
                               Settings.ipPoolP_PE)) {

                context.setNotReached();
            } else {
                context.setReached();
            }
        } catch (Throwable e) {
            throw new DpCallbackException("error in " + endpointName +
                                          ":vpe-p0-topo-added",e);
        }
        return opaque;
    }

    @NanoServiceCallback(servicePoint = "l3vpn-servicepoint",
                         componentType="*", state="l3vpn:dev-setup",
                         callType = NanoServiceCBType.CREATE)
    public Properties starDevSetupCreate(NanoServiceContext context,
                                         NavuNode service,
                                         NavuNode ncsRoot,
                                         Properties opaque,
                                         Properties componentProperties)
                                             throws ConfException {
        String endpointName = context.getComponentName();
        LOGGER.info("reached create " + endpointName +
                           ":" + context.getState());
        try {
            NavuList endpoints = service.list(l3vpn._endpoint);
            NavuContainer endpoint = endpoints.elem(endpointName);
            String ceName = endpoint.leaf(l3vpn._ce_device_).valueAsString();
            NavuContainer topology = ncsRoot.getParent().
                container(l3vpn.uri).container(l3vpn._topology);

            Template peTemplate = new Template(context, "pe-template");
            Template ceTemplate = new Template(context, "ce-template");

            NavuContainer conn = getConnection(topology,
                     endpoint.leaf(l3vpn._ce_device).valueAsString(),
                     "pe");

            // Get the PE connection for this endpoint router
            NavuContainer peEndpoint = getConnectedEndpoint(conn, ceName);
            NavuContainer ceEndpoint = getMyEndpoint(conn, ceName);

            NavuLeaf vlan = conn.leaf("link-vlan");
            TemplateVariables vpnVar = new TemplateVariables();
            vpnVar.putQuoted("PE",
                             peEndpoint.leaf("device").valueAsString());
            vpnVar.putQuoted("CE",
                             endpoint.leaf("ce-device").valueAsString());
            vpnVar.putQuoted("VLAN_ID", vlan.valueAsString());
            vpnVar.putQuoted("LINK_PE_ADR", getIPAddress(
                             peEndpoint.leaf("ip-address").valueAsString()));
            vpnVar.putQuoted("LINK_CE_ADR",
                 getIPAddress(ceEndpoint.leaf("ip-address").valueAsString()));
            vpnVar.putQuoted("LINK_MASK",
                 getNetMask(ceEndpoint.leaf("ip-address").valueAsString()));
            vpnVar.putQuoted("LINK_PREFIX",
                 getIPPrefix(ceEndpoint.leaf("ip-address").valueAsString()));
            vpnVar.putQuoted("PE_INT_NAME",
                             peEndpoint.leaf("interface").valueAsString());
            vpnVar.putQuoted("CE_INT_NAME",
                             ceEndpoint.leaf("interface").valueAsString());
            vpnVar.putQuoted("CE_LOCAL_INT_NAME",
                             endpoint.leaf("ce-interface").valueAsString());
            vpnVar.putQuoted("LOCAL_CE_ADR",
                 getIPAddress(getNextIPV4Address(
                              endpoint.leaf("ip-network").valueAsString())));
            vpnVar.putQuoted("LOCAL_CE_NET",
                 getIPAddress(endpoint.leaf("ip-network").valueAsString()));
            vpnVar.putQuoted("CE_MASK",
                 getNetMask(endpoint.leaf("ip-network").valueAsString()));
            vpnVar.putQuoted("BW", endpoint.leaf("bandwidth").valueAsString());

            peTemplate.apply(service, vpnVar);
            ceTemplate.apply(service, vpnVar);

            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("error in " + endpointName +
                                          ":dev-setup",e);
        }
        return opaque;
    }


    @NanoServiceCallback(servicePoint = "l3vpn-servicepoint",
                         componentType="*", state="l3vpn:qos-configured",
                         callType = NanoServiceCBType.CREATE)
    public Properties starQoSCreate(NanoServiceContext context,
                                    NavuNode service,
                                    NavuNode ncsRoot,
                                    Properties opaque,
                                    Properties componentProperties)
                                        throws ConfException, IOException {
        String endpointName = context.getComponentName();
        LOGGER.info("reached create " + endpointName +
                           ":" + context.getState());
        try {
            NavuList endpoints = service.list(l3vpn._endpoint);
            NavuContainer endpoint = endpoints.elem(endpointName);
            String ceName = endpoint.leaf(l3vpn._ce_device_).valueAsString();
            NavuContainer topology = ncsRoot.getParent().
                container(l3vpn.uri).container(l3vpn._topology);

            // Get the PE connection for this endpoint router
            NavuContainer conn = getConnection(topology,
                endpoint.leaf(l3vpn._ce_device).valueAsString(),
                "pe");
            NavuContainer peEndpoint =
                getConnectedEndpoint(conn, ceName);
            NavuContainer ceEndpoint = getMyEndpoint(conn, ceName);
            NavuLeaf vlan = conn.leaf("link-vlan");

            configureQoS(context, service, endpoint,
                         ceEndpoint, peEndpoint, vlan);

            LOGGER.info("done");

            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("error in " + endpointName +
                                          ":qos-configured",e);
        }
        return opaque;
    }

    private NavuContainer getConnectedEndpoint(NavuContainer conn, String dev)
        throws NavuException {
        if (dev.equals(conn.container("endpoint-1").
                       leaf("device").valueAsString())) {
            return conn.container("endpoint-2");
        } else {
            return conn.container("endpoint-1");
        }
    }

    private NavuContainer getMyEndpoint(NavuContainer conn, String dev)
        throws NavuException {
        if (dev.equals(conn.container("endpoint-1").
                       leaf("device").valueAsString())) {
            return conn.container("endpoint-1");
        } else {
            return conn.container("endpoint-2");
        }
    }

    private NavuContainer getConnection(NavuContainer topology,
                                        String deviceName,
                                        String remoteDeviceRole)
        throws NavuException {
        NavuList connections = topology.list(l3vpn._connection);

        for (NavuContainer conn : connections.elements()) {
            String remoteName;

            if (deviceName.equals(conn.container(l3vpn._endpoint_1).
                                  leaf(l3vpn._device).valueAsString())) {
                remoteName = conn.container(l3vpn._endpoint_2).
                    leaf(l3vpn._device).valueAsString();
            } else if (deviceName.equals(conn.container("endpoint-2").
                                         leaf("device").valueAsString())) {
                remoteName = conn.container(l3vpn._endpoint_1).
                    leaf(l3vpn._device).valueAsString();
            } else {
                continue;
            }
            ConfList devicesWithRole = (ConfList) topology.list(l3vpn._role).
                elem(remoteDeviceRole).leaf(l3vpn._device).value();

            if (devicesWithRole.isMember(new ConfBuf(remoteName))) {
                return conn;
            }
        }
        return null;
    }

    private String getIPAddress(String prefix) {
        String[] parts = prefix.split("/");
        return parts[0];
    }

    private String getIPPrefix(String prefix) {
        String[] parts = prefix.split("/");
        return parts[1];
    }

    private void configureQoS(ServiceContext context, NavuNode service,
                              NavuContainer endpoint, NavuContainer ceEndpoint,
                              NavuContainer peEndpoint, NavuLeaf vlan)
        throws ConfException, UnknownHostException {
        LOGGER.info("configuring QoS");

        if (service.container("qos").leaf("qos-policy").exists()) {
            Map<String, List<String>> qosClassMap =
                new HashMap<String, List<String>>();

            TemplateVariables qosVar = new TemplateVariables();
            qosVar.putQuoted("POLICY_NAME", service.container("qos").
                             leaf("qos-policy").valueAsString());
            qosVar.putQuoted("CE_INT_NAME",
                             ceEndpoint.leaf("interface").valueAsString());
            qosVar.putQuoted("PE_INT_NAME",
                             peEndpoint.leaf("interface").valueAsString());
            qosVar.putQuoted("VLAN_ID", vlan.valueAsString());
            qosVar.putQuoted("PE", peEndpoint.leaf("device").valueAsString());
            qosVar.putQuoted("CE", endpoint.leaf("ce-device").valueAsString());

            // Find the globally defined QOS policy our service is
            // referring to.
            NavuNode n =
                service.container("qos").leaf("qos-policy").deref().get(0);
            NavuContainer qosPolicy =
                (NavuContainer) ((NavuLeaf) n).getParent();
            NavuList policyClass = qosPolicy.list("class");

            // Iterate over all classes for this policy and its settings.
            Template aclTemplate = new Template(context, "acl-template");
            int classCounter = 0;
            for (NavuContainer c : policyClass.elements()) {
                NavuNode qosClass = c.leaf("qos-class").deref().get(0);
                qosClassMap.put(c.leaf("qos-class").valueAsString(),
                                new ArrayList<String>());
                NavuContainer cl =
                    (NavuContainer) ((NavuLeaf) qosClass).getParent();

                if (cl.leaf("dscp-value").exists()) {
                    qosVar.putQuoted("CLASS_DSCP",
                                     cl.leaf("dscp-value").valueAsString());
                    if (cl.leaf("dscp-value").valueAsString().equals("ef") ||
                        cl.leaf("dscp-value").valueAsString().equals("af31")) {
                        qosVar.putQuoted("CLASS_PRIORITY","high");
                    } else {
                        qosVar.putQuoted("CLASS_PRIORITY","low");
                    }
                } else {
                    qosVar.putQuoted("CLASS_PRIORITY","low");
                    qosVar.putQuoted("CLASS_DSCP", "");
                }

                qosVar.putQuoted("CLASS_NAME",
                                 c.leaf("qos-class").valueAsString());
                qosVar.putQuoted("CLASS_BW",
                               c.leaf("bandwidth-percentage").valueAsString());
                qosVar.putQuoted("CLASS_COUNTER",String.valueOf(classCounter));

                if (c.leaf("priority").exists()) {
                    Template qosPrioTemplate =
                        new Template(context, "qos-prio-template");
                    qosPrioTemplate.apply(service, qosVar);
                    Template qosPePrioTemplate =
                        new Template(context, "qos-pe-prio-template");
                    qosPePrioTemplate.apply(service, qosVar);
                } else {
                    Template qosTemplate =
                        new Template(context, "qos-template");
                    qosTemplate.apply(service, qosVar);
                    Template qosPeTemplate =
                        new Template(context, "qos-pe-template");
                    qosPeTemplate.apply(service, qosVar);
                }
                Template peClassTemplate =
                    new Template(context, "qos-pe-class-template");
                peClassTemplate.apply(service, qosVar);

                // Also list all the globally defined traffic match
                // statements for this class and add them to a arraylist
                // to use for processing.
                for (NavuContainer match :
                    cl.list("match-traffic").elements()) {
                    qosClassMap.get(c.leaf("qos-class").valueAsString()).
                           add("GLOBAL-" + match.leaf("name").valueAsString());

                    TemplateVariables aclVar = setAclVars(match, "GLOBAL");
                    aclVar.putQuoted("CE",
                                   endpoint.leaf("ce-device").valueAsString());
                    aclTemplate.apply(service, aclVar);
                }

                classCounter++;
            }

            // Create ACL entries for all service specific match rules

            NavuList matchRules =
                service.container("qos").list("custom-qos-match");

            for (NavuContainer match : matchRules.elements()) {
                String namePrefix = service.leaf("name").valueAsString();
                if (qosClassMap.
                    containsKey(match.leaf("qos-class").valueAsString())) {
                    qosClassMap.get(match.leaf("qos-class").valueAsString()).
                    add(namePrefix + "-" + match.leaf("name").valueAsString());
                }
                TemplateVariables aclVar = setAclVars(match, namePrefix);

                aclVar.putQuoted("CE",
                                 endpoint.leaf("ce-device").valueAsString());
                aclTemplate.apply(service, aclVar);
            }

            for (Map.Entry<String, List<String>> entry :
                                        qosClassMap.entrySet()) {
                for (String matchEntry : entry.getValue()) {
                    TemplateVariables classVar = new TemplateVariables();
                    classVar.putQuoted("CLASS_NAME", entry.getKey());
                    classVar.putQuoted("MATCH_ENTRY", matchEntry);
                    classVar.putQuoted("CE",
                                   endpoint.leaf("ce-device").valueAsString());
                    Template classTemplate =
                        new Template(context, "qos-class-template");
                    classTemplate.apply(service, classVar);
                }
            }
        }
    }

    private TemplateVariables setAclVars(NavuContainer match, String namePrefix)
        throws NavuException, UnknownHostException {
        TemplateVariables aclVar = new TemplateVariables();

        aclVar.putQuoted("ACL_NAME", namePrefix + "-" +
                         match.leaf("name").valueAsString());
        aclVar.putQuoted("PROTOCOL", match.leaf("protocol").valueAsString());
        aclVar.putQuoted("SOURCE_IP", match.leaf("source-ip").valueAsString());

        if ("any".equals(match.leaf("source-ip").valueAsString())) {
            aclVar.putQuoted("SOURCE_IP_ADR", "any");
            aclVar.putQuoted("SOURCE_WMASK", " ");
        } else {
            aclVar.putQuoted("SOURCE_IP_ADR",
                        getIPAddress(match.leaf("source-ip").valueAsString()));
            aclVar.putQuoted("SOURCE_WMASK",
                 prefixToWildcardMask(getIPPrefix(
                                    match.leaf("source-ip").valueAsString())));
        }

        if ("any".equals(match.leaf("destination-ip").valueAsString())) {
            aclVar.putQuoted("DEST_IP_ADR", "any");
            aclVar.putQuoted("DEST_WMASK", " ");
        } else {
            aclVar.putQuoted("DEST_IP_ADR",
                  getIPAddress(match.leaf("destination-ip").valueAsString()));
            aclVar.putQuoted("DEST_WMASK",
                             prefixToWildcardMask(getIPPrefix(
                             match.leaf("destination-ip").valueAsString())));
        }

        aclVar.putQuoted("PORT_START",
                         match.leaf("port-start").valueAsString());
        aclVar.putQuoted("PORT_END", match.leaf("port-end").valueAsString());
        return aclVar;
    }

    private String prefixToWildcardMask(String pre)
        throws UnknownHostException {
        int prefix = Integer.parseInt(pre);
        int mask = 0xffffffff << (32 - prefix);
        int value = mask;
        byte[] bytes = new byte[] { (byte) (~(value >>> 24) & 0xFF),
                                    (byte) (~(value >> 16 & 0xff) & 0xFF),
                                    (byte) (~(value >> 8 & 0xff) & 0xFF),
                                    (byte) (~(value & 0xff) & 0xFF) };

        InetAddress netAddr = InetAddress.getByAddress(bytes);
        return netAddr.getHostAddress();
    }

    public boolean isEndpointsReady(NavuNode root,
                                    ArrayList<NavuContainer> epsList)
        throws NavuException {
        //FIXME should not be necessary to read other service plan.
        NavuContext ctx2 = new NavuContext(root.context().getMaapi());
        ctx2.startOperationalTrans(Conf.MODE_READ);
        NavuContainer base2 = new NavuContainer(ctx2);
        boolean endpointsReady = true;
        for (NavuContainer eps : epsList) {
            try {
                if (eps.getKey().elementAt(0).
                    toString().equals("branch-office")) {
                    // FIXME does not work for the moment, just avoid
                    continue;
                }
                NavuNode oeps = base2.getNavuNode(eps.getConfPath());
                NavuList plancomps =
                    oeps.container(l3vpn._plan_).list(l3vpn._component_);
                NavuList states = plancomps.elem("self").list("state");
                String readyVal =
                    states.elem("ready").leaf("status").valueAsString();
                if (readyVal == null || !readyVal.equals("reached")) {
                    endpointsReady = false;
                }
            } catch (Exception e) {endpointsReady=false;}
        }
        ctx2.finishClearTrans();
        return endpointsReady;
    }


    ///////
    ///////
    ///////
    /// endpoint stuff
    ///////
    ///////
    ///////
    ///////
    public static List<String> getCEonVPE(NavuNode ncsRoot)
        throws NavuException {
        ArrayList<String> CEonVPE = new ArrayList<String>();
        ConfList onvpe = (ConfList) ncsRoot.getParent().
            container(l3vpn.uri).leaf(l3vpn._onvpe).value();

        for (ConfObject o : onvpe.elements()) {
            CEonVPE.add(o.toString());
        }
        return CEonVPE;
    }

    private boolean createVirtualPE(ServiceContext context, NavuNode service,
                                    NavuNode endpoint,
                                    NavuNode ncsRoot, String ceName,
                                    String tenant, String deploymentName,
                                    String virtualPEName, String vmName)
        throws UnknownHostException, IOException, ConfException {
        LOGGER.info(ceName + " should be on the virtual PE");
        try (Maapi m = new Maapi(cdb.getSocket().getRemoteSocketAddress())) {
            m.startUserSession("admin", "system", new String[] { "admin" });

            Template escTenantTemplate =
                new Template(context, "vm-manager-template");
            ESCParameters vmParams = getEscVM(endpoint);
            TemplateVariables vars = new TemplateVariables();
            vars.putQuoted("NAME", vmName);
            vars.putQuoted("ESC", "esc0");
            vars.putQuoted("TENANT", tenant);
            vars.putQuoted("DEPNAME", deploymentName);
            vars.putQuoted("SERVICE_NAME", vmParams.service_name);
            vars.putQuoted("SERVICE_VERSION", vmParams.service_version);
            vars.putQuoted("VM_GROUP", vmParams.vm_group);

            escTenantTemplate.apply(service, vars);

            VmManager.registerStartRequest(context, service, ncsRoot, vmName);

            return VmManager.deviceReady(cdb, virtualPEName);
        }
    }

    public static ESCParameters getEscVM(NavuNode service) {
        try {
            NavuContainer esc_service = service.container(l3vpn._esc_service);
            if (esc_service.exists()) {
                return new ESCParameters(
                           esc_service.leaf(l3vpn._name).valueAsString(),
                           esc_service.leaf(l3vpn._version).valueAsString(),
                           esc_service.leaf(l3vpn._vm_group).valueAsString());

            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        LOGGER.info("Using default VM settings");
        return new ESCParameters();
    }


    /**
     * Adds the device to the /topology/role{roleName}/device list
     *
     * @param root
     *          // NCS root
     * @param deviceName
     *          // e.g. volvo_vpn_CSR0
     * @param roleName
     *          // e.g. pe
     * @throws NavuException
     */
    private void addToTopologyRole(NavuNode root, String deviceName,
                                 String roleName)
        throws NavuException {
        NavuContainer topology =
            root.getParent().container(l3vpn.uri).container(l3vpn._topology);
        NavuLeaf devicesLeaf =
            topology.list(l3vpn._role).elem(roleName).leaf(l3vpn._device);
        ConfList devices = (ConfList) devicesLeaf.value();
        devices.addElem(new ConfBuf(deviceName));
        devicesLeaf.sharedSet(devices);
    }

    private boolean addToTopology(NavuNode service, NavuNode root,
                                  NavuContainer connection,
                                  String endPoint1, String ep1If,
                                  String endPoint2, String ep2If,
                                  String connectionName,
                                  String ipPoolName)
        throws Exception {
        // We need an IP address for the devices
        final int prefixLength = 30;

        try {
            NavuContainer topology =
              root.getParent().container(l3vpn.uri).container(l3vpn._topology);
            NavuList connections = topology.list(l3vpn._connection);

            if (null == connection) {
                LOGGER.info("Creating new topology connection");
                connection = connections.sharedCreate(connectionName);
                NavuLeaf link_vlan = connection.leaf(l3vpn._link_vlan);

                // Dummy VLAN ID resource allocator
                String vlanIDName = Settings.idPoolName + connectionName;
                ConfUInt32 vlanID = new ConfUInt32((long) (Math.abs(
                                                vlanIDName.hashCode() % 4096)
                                                + 1));
                LOGGER.info("vlanID=" + vlanID + " idPoolName=" +
                            Settings.idPoolName + " connectionName=" +
                            connectionName);

                link_vlan.sharedSet(vlanID);
            } else
                LOGGER.info("Reusing existing topology connection");

            NavuContainer ep1 = connection.container(l3vpn._endpoint_1);

            // Dummy IP subnet resource allocator
            String ipPoolNum = String.valueOf(
                                    Math.abs(ipPoolName.hashCode() % 256));
            String connectionNum = String.valueOf(
                                    Math.abs(connectionName.hashCode() % 256));
            ConfIPPrefix net = (ConfIPPrefix) new ConfIPv4Prefix("10." +
                                    ipPoolNum + "." + connectionNum + ".0/24");
            LOGGER.info("net=" + net + " ipPoolName=" + ipPoolName +
                        " connectionName=" + connectionName);

            InetAddress ep1_addr = Helper.increment(net.getAddress());
            configureEndpoint(ep1, endPoint1, ep1If, ep1_addr, 24);

            NavuContainer ep2 = connection.container(l3vpn._endpoint_2);
            InetAddress ep2_addr = Helper.increment(ep1_addr);
            configureEndpoint(ep2, endPoint2, ep2If, ep2_addr, 24);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
        return true;
    }

    private void configureEndpoint(NavuContainer ep, String device,
                                   String networkInterface, InetAddress addr,
                                   int prefixLen)
        throws NavuException {
        ep.leaf(l3vpn._device).sharedSet(new ConfBuf(device));
        ep.leaf(l3vpn._interface).sharedSet(new ConfBuf(networkInterface));
        ep.leaf(l3vpn._ip_address).
            sharedSet(new ConfIPv4AndPrefixLen(addr, prefixLen));
    }

    private String getNetMask(String addr) throws UnknownHostException {
        String[] parts = addr.split("/");
        int prefix;

        if (parts.length < 2) {
            prefix = 0;
        } else {
            prefix = Integer.parseInt(parts[1]);
        }
        int mask = 0xffffffff << (32 - prefix);

        int value = mask;
        byte[] bytes = new byte[] { (byte) (value >>> 24),
                                    (byte) (value >> 16 & 0xff),
                                    (byte) (value >> 8 & 0xff),
                                    (byte) (value & 0xff) };

        InetAddress netAddr = InetAddress.getByAddress(bytes);
        return netAddr.getHostAddress();
    }

    private String getNextIPV4Address(String ip) {
        String ipAddr = ip.split("/")[0];
        String mask = ip.split("/")[1];

        String[] nums = ipAddr.split("\\.");
        int i = (Integer.parseInt(nums[0]) << 24 |
                 Integer.parseInt(nums[2]) << 8 |
                 Integer.parseInt(nums[1]) << 16 |
                 Integer.parseInt(nums[3])) + 1;

        // If you wish to skip over .255 addresses.
        if ((byte) i == -1) {
            i++;
        }

        return String.format("%d.%d.%d.%d",
                             i >>> 24 & 0xFF,
                             i >> 16 & 0xFF,
                             i >> 8 & 0xFF,
                             i >> 0 & 0xFF) + "/" + mask;
    }

}
