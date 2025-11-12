package com.cisco.vmmanager;

import java.io.IOException;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.cisco.vmmanager.namespaces.vmManager;
import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbSession;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfObjectRef;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ErrorCode;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.NanoServiceCallback;
import com.tailf.dp.proto.NanoServiceCBType;
import com.tailf.dp.services.NanoServiceContext;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiCursor;
import com.tailf.maapi.MaapiException;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;

public class escstart {

    private static Logger LOGGER = LogManager.getLogger(escstart.class);

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    private Maapi maapi;


    @NanoServiceCallback(servicePoint="vm-servicepoint",
                         componentType="ncs:self", state="ncs:init",
                         callType=NanoServiceCBType.CREATE)
    public Properties selfInitCreate(NanoServiceContext context,
                                     NavuNode vm,
                                     NavuNode ncsRoot,
                                     Properties opaque,
                                     Properties componentProperties)
                                         throws DpCallbackException {
        LOGGER.info("reached create " + context.getComponentName() +
                           ":" + context.getState());
        try {
            if (opaque == null) {
                opaque = new Properties();
                opaque.setProperty("dumb-not-null", "true");
            }
            String deploymentName = vm.leaf("deployment-name").valueAsString();
            String vmDevice       = vm.leaf("vm-device").valueAsString();
            String tenant         = vm.leaf("tenant").valueAsString();
            String vmGroup        = vm.leaf("vm-group").valueAsString();

            String devName = makeDevName(tenant, deploymentName,
                                         vmGroup, vmDevice);
            opaque.setProperty("DEVNAME", devName);
            opaque.setProperty("DEVNAME_EXPR", "'" + devName + "'");

            TemplateVariables variables = new TemplateVariables();
            Template template = new Template(context,
                                             "vm-esc-tenant-notif-template");
            variables.putQuoted("ESC", vmDevice);
            template.apply(vm, variables);
            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("Exception in self:init", e);
        }
        return opaque;
    }


    @NanoServiceCallback(servicePoint="vm-servicepoint",
                         componentType="ncs:self", state="vmm:init-vm",
                         callType=NanoServiceCBType.CREATE)
    public Properties initVmCreate(NanoServiceContext context,
                                   NavuNode vm,
                                   NavuNode ncsRoot,
                                   Properties opaque,
                                   Properties componentProperties)
                                       throws DpCallbackException {
        LOGGER.info("reached create " + context.getComponentName() +
                           ":" + context.getState());
        try {
            String name           = vm.leaf("name").valueAsString();
            String deploymentName = vm.leaf("deployment-name").valueAsString();
            String vmDevice       = vm.leaf("vm-device").valueAsString();
            String tenant         = vm.leaf("tenant").valueAsString();
            String serviceName    = vm.leaf("service-name").valueAsString();
            String serviceVersion = vm.leaf("service-version").valueAsString();
            String vmType         = vm.leaf("vm-type").valueAsString();
            String vmGroup        = vm.leaf("vm-group").valueAsString();
            String day0Url        = vm.leaf("day0-url").valueAsString();
            String scalingMin     = vm.leaf("scaling-min").valueAsString();
            String scalingMax     = vm.leaf("scaling-max").valueAsString();

            //updateVmProgress(name, "defined");

            TemplateVariables variables = new TemplateVariables();
            Template template = new Template(context,
                "vm-esc-tenant-template");
            variables.putQuoted("ESC", vmDevice);
            variables.putQuoted("TENANT", tenant);
            variables.putQuoted("DEPNAME", deploymentName);
            variables.putQuoted("SERVICE_NAME", serviceName);
            variables.putQuoted("SERVICE_VERSION", serviceVersion);
            variables.putQuoted("VM_GROUP", vmGroup);
            variables.putQuoted("DAY0_URL", day0Url);
            variables.putQuoted("SCALING_MIN", scalingMin);
            variables.putQuoted("SCALING_MAX", scalingMax);

            if ("csr".equals(vmType)) {
              variables.putQuoted("DAY0_FILENAME", "iosxe_config.txt");
            } else {
                LOGGER.error("unknown device type: "+vmType);
            }

            template.apply(vm, variables);

            template = new Template(context, "vm-esc-scaling-template");

            for(NavuContainer pool:  vm.list("scaling-pool")) {
                variables.putQuoted("SCALING_POOL",
                                pool.leaf("name").valueAsString());

                for(NavuContainer address: pool.list("address")) {
                    variables.putQuoted("IP_ADDRESS",
                                    address.leaf("ip").valueAsString());
                    template.apply(vm, variables);
                }
            }

            template =
                new Template(context, "vm-esc-interface-template");

            for(NavuContainer iface: vm.list("interface")) {
                variables.putQuoted("INTERFACE_ID",
                                iface.leaf("id").valueAsString());
                variables.putQuoted("NETWORK",
                                iface.leaf("name").valueAsString());
                variables.putQuoted("INTERFACE_IP_ADDRESS",
                                iface.leaf("ip").valueAsString());
                template.apply(vm, variables);
            }

            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("Exception in self:init-vm", e);
        }
        return opaque;
    }


    @NanoServiceCallback(servicePoint="vm-servicepoint",
                         componentType="ncs:self", state="vmm:vm-initialized",
                         callType=NanoServiceCBType.CREATE)
    public Properties vmInitCreate(NanoServiceContext context,
                                   NavuNode vm,
                                   NavuNode ncsRoot,
                                   Properties opaque,
                                   Properties componentProperties)
                                       throws DpCallbackException {
        LOGGER.info("reached create " + context.getComponentName() +
                           ":" + context.getState());
        try {
            String name           = vm.leaf("name").valueAsString();
            String deploymentName = vm.leaf("deployment-name").valueAsString();
            String vmDevice       = vm.leaf("vm-device").valueAsString();
            String tenant         = vm.leaf("tenant").valueAsString();
            String vmGroup        = vm.leaf("vm-group").valueAsString();
            String devName = makeDevName(tenant, deploymentName,
                                         vmGroup, vmDevice);
            if(!checkDevConfigured()) {
                context.setNotReached();
            } else {
                vmAlive(ncsRoot, name, devName);

                opaque.setProperty("vm-initialized", "DONE");
                context.setReached();
            }
        } catch (Throwable e) {
            throw new DpCallbackException("Exception in self:vm-initialized",
                                          e);
        }
        return opaque;
    }

    @NanoServiceCallback(servicePoint="vm-servicepoint",
                         componentType="ncs:self", state="vmm:device-created",
                         callType=NanoServiceCBType.CREATE)
    public Properties devCreate(NanoServiceContext context,
                                NavuNode vm,
                                NavuNode ncsRoot,
                                Properties opaque,
                                Properties componentProperties)
                                    throws DpCallbackException {
        LOGGER.info("reached create " + context.getComponentName() +
                           ":" + context.getState());
        try {
            String name           = vm.leaf("name").valueAsString();
            String deploymentName = vm.leaf("deployment-name").valueAsString();
            String vmDevice       = vm.leaf("vm-device").valueAsString();
            String tenant         = vm.leaf("tenant").valueAsString();
            String vmGroup        = vm.leaf("vm-group").valueAsString();
            String devName = makeDevName(tenant, deploymentName,
                                         vmGroup, vmDevice);
            LOGGER.info("Added device to list " + devName);
            addDeviceToList(
                    maapi.getSocket().getRemoteSocketAddress(), name, devName);

            LOGGER.info("create device ready flag " + devName);
            initMaapi(maapi);
            createDeviceReady(maapi, devName);

            opaque.setProperty("device-created", "DONE");
            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("Exception in self:device-created",
                                          e);
        }
        return opaque;
    }


    @NanoServiceCallback(servicePoint="vm-servicepoint",
                         componentType="ncs:self", state="vmm:device-ready",
                         callType=NanoServiceCBType.CREATE)
    public Properties devReadyCreate(NanoServiceContext context,
                                     NavuNode vm,
                                     NavuNode ncsRoot,
                                     Properties opaque,
                                     Properties componentProperties)
                                         throws DpCallbackException {
        LOGGER.info("reached create " + context.getComponentName() +
                           ":" + context.getState());
        try {
            String deploymentName = vm.leaf("deployment-name").valueAsString();
            String vmDevice       = vm.leaf("vm-device").valueAsString();
            String tenant         = vm.leaf("tenant").valueAsString();
            String serviceName    = vm.leaf("service-name").valueAsString();
            String vmGroup        = vm.leaf("vm-group").valueAsString();
            String devName = makeDevName(tenant, deploymentName,
                                         vmGroup, vmDevice);

            opaque.setProperty("device-ready", "DONE");
            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("Exception in self:device-ready", e);
        }
        return opaque;
    }



    @NanoServiceCallback(servicePoint="vm-servicepoint",
        componentType="ncs:self", state="vmm:device-synced",
        callType=NanoServiceCBType.CREATE)
    public Properties devSyncedCreate(NanoServiceContext context,
                                      NavuNode vm,
                                      NavuNode ncsRoot,
                                      Properties opaque,
                                      Properties componentProperties)
                                          throws DpCallbackException {
        System.out.println("reached create " + context.getComponentName() +
                           ":" + context.getState());
        try (Maapi maapi2
                = new Maapi(maapi.getSocket().getRemoteSocketAddress())) {
            String deploymentName = vm.leaf("deployment-name").valueAsString();
            String vmDevice       = vm.leaf("vm-device").valueAsString();
            String tenant         = vm.leaf("tenant").valueAsString();
            String vmGroup        = vm.leaf("vm-group").valueAsString();
            String devName = makeDevName(tenant, deploymentName,
                                         vmGroup, vmDevice);

            maapi2.startUserSession("admin", "admin");

            setDeviceReadyFlag(maapi2, devName, true);
            context.setReached();
        } catch (Throwable e) {
            throw new DpCallbackException("Exception in self:device-synced", e);
        }
        return opaque;
    }

    private void initMaapi(Maapi maapi) throws DpCallbackException {
        try {
            maapi.getMyUserSession();
        } catch (MaapiException e) {
            if (ErrorCode.ERR_NOEXISTS.equals(e.getErrorCode())) {
                try {
                    maapi.startUserSession("admin", "admin");
                } catch (Throwable e1) {
                    e1.printStackTrace();
                    throw new DpCallbackException("could not init maapi", e1);
                }
            } else {
                throw new DpCallbackException("could not init maapi", e);
            }
        } catch (Throwable e1) {
            LOGGER.error("could not init maapi", e1);
            throw new DpCallbackException("could not init maapi", e1);
        }
    }

    private void vmAlive(NavuNode ncsRoot, String name,
                         String devName) throws Exception {

        // It might take 10 secs for oper data to arrive in oper tree
        // at the ESC side (buggy)

        boolean isNetsim = true;
        LOGGER.info("Device is netsim: " + isNetsim);


        NavuList d = ncsRoot.container("devices").list("device");

        if (d.elem(devName) != null) {
            LOGGER.info("Device "+ devName +
                        " already in tree, replaying?? or we're" +
                        "getting both VM_ALIVE and VM_RECOVERY_COMPLETE");
        }

        String mgmtIp;
        mgmtIp = null;

        if (isNetsim) {
            // netsim device, mount locally
            mgmtIp = "127.0.0.1";
        }

        // create device in device tree

        LOGGER.info("Create the device");
        NavuContainer vm = null;
        try {
            vm = d.sharedCreate(devName);
        }
        catch (Exception e) {
            vm = d.elem(devName);
        }


        if (isNetsim) {
            vm.leaf("address").sharedSet(mgmtIp);
            vm.leaf("port").sharedSet("10029");

            vm.leaf("authgroup").sharedSet("default");
            vm.container("ssh").leaf("host-key-verification").sharedSet("none");
            String nedId = "cisco-ios-netsim-cli-1.0";

            NavuContainer cli = vm.container("device-type").
                container("cli");

            cli.leaf("ned-id").sharedSet(nedId);

            vm.container("state").
                leaf("admin-state").sharedSet("unlocked");

        }
      LOGGER.info("FINISH CREATING the device");
    }

    private String makeDevName(String tenant, String depname,
                              String vmgroup, String num) {
        return tenant + "_" + depname + "_" + vmgroup + "_" + num;
    }


    private static void createDeviceReady(Maapi maapi, String devName)
        throws ConfException, IOException {
        if (!VmManager.deviceReady(maapi, devName)) {
            //either false or not yet created
            setDeviceReadyFlag(
                    maapi.getSocket().getRemoteSocketAddress(), devName, false);
        }
    }

    private static void setDeviceReadyFlag(Maapi m, String devName, boolean val)
        throws ConfException, IOException {
        NavuContext operCtx = new NavuContext(m);
        operCtx.startOperationalTrans(Conf.MODE_READ_WRITE);
        NavuContainer base = new NavuContainer(operCtx);
        NavuContainer root = base.container(Ncs.hash);
        try {
            root.container("devices").list("device").elem(devName)
                .namespace(vmManager.id).leaf("ready").set(new ConfBool(val));
        } catch (Exception ex) {
            LOGGER.error("escstart setDeviceReadyFlag", ex);
        }
        finally {
            operCtx.applyClearTrans();
        }
    }

    private static void setDeviceReadyFlag(
            SocketAddress address, String devName, boolean val)
            throws ConfException, IOException {
        try (Cdb cdb = new Cdb("x1-cdb", address)) {
            CdbSession sess = cdb.startSession(CdbDBType.CDB_OPERATIONAL);
            sess.setElem(new ConfBool(val),
                         "/ncs:devices/device{%s}/ready", devName);
        } catch (Exception ex) {
            LOGGER.error("escstart setDeviceReadyFlag: ", ex);
        }
    }

    private void addDeviceToList(
            SocketAddress address, String serviceName, String devName)
            throws ConfException, IOException {
        try (Cdb cdb = new Cdb("x2-cdb", address)) {
            CdbSession sess = cdb.startSession(CdbDBType.CDB_OPERATIONAL);
            if (!sess.exists("/vmm:vm-manager/start{%s}/device{%x}",
                             serviceName, devName)) {
                sess.create("/vmm:vm-manager/start{%s}/device{%x}",
                            serviceName, devName);
            }
        } catch (Exception ex) {
            LOGGER.error("escstart addDeviceToList: ", ex);
        }
    }

    private boolean checkDeviceExists(String devName)
        throws ConfException, IOException {
        boolean ret = false;
        initMaapi(maapi);
        NavuContext operCtx = new NavuContext(maapi);
        operCtx.startRunningTrans(Conf.MODE_READ);
        NavuContainer base = new NavuContainer(operCtx);
        NavuContainer root = base.container(Ncs.hash);
        try {
            NavuContainer dev =
                root.container("devices").list("device").elem(devName);
            if (dev != null) {
                ret = true;
            }
        } catch (Exception ex) {
            LOGGER.error("escstart checkDeviceExists: ", ex);
        }
        finally {
            operCtx.finishClearTrans();
        }
        return ret;
    }

    private boolean checkDevConfigured() throws Exception {
        boolean ret = false;
        initMaapi(maapi);
        int th = maapi.startTrans(Conf.DB_OPERATIONAL, Conf.MODE_READ);
        String path = "/ncs:devices/device{esc0}/" +
            "notifications/received-notifications/notification";
        MaapiCursor cur = maapi.newCursor(th, path);

        ConfKey key = maapi.getNext(cur);
        while (key != null) {
            ConfEnumeration bval =
                (ConfEnumeration) maapi.getElem(th,
                                   path+"{%x}/data/escEvent/event/type", key);
            if (bval.getOrdinalValue() == 9) {
                return true;
            } else {
                System.out.println("ORDINAL VALUE = " + bval.getOrdinalValue());
            }
            key = maapi.getNext(cur);
        }
        return ret;
    }

}
