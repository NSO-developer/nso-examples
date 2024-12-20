package com.example.maapi.actions;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.tailf.maapi.MaapiSchemas.CSNode;
import com.tailf.maapi.MaapiSchemas.CSSchema;

import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfNoExists;
import com.tailf.conf.ConfUInt16;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfBool;

import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamLeaf;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;

import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.maapi.Maapi;

import com.tailf.cdb.Cdb;

import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;

import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;

import com.tailf.ncs.ns.Ncs;

import com.example.maapi.namespaces.AddDevicesAction;

/**
 * Example of how to add device to the device tree through the
 * Maapi API. This class is responsible for the action-point
 * "add-devices-and-sync" which is modeled as an action with the
 * same name. The data model that the action-point is defined
 * is located in packages/maapi/src/yang/Add-devices-action.yang.
 *
 * The class has a list of the Device information which it
 * uses to create the device instances  to the device tree.
 *
 * The device information is stored in this class and it represents
 * the NSC netsim simulated
 * network which consist of three devices named ex0-2.
 *
 * An instance of this class is called by NCS when issue from the
 * CLI in configuration mode.
 *
 * %> request example add-devices-and-sync
 *
 * device-info {
 *    dev-name ex0
 *  dev-state exists
 *  sync-info in-sync
 *  info Performed sync!
 * }
 * device-info {
 *    dev-name ex1
 *    dev-state exists
 *    sync-info in-sync
 *    info
 * }
 * device-info {
 *    dev-name ex2
 *    dev-state exists
 *    sync-info in-sync
 *    info
 * }
 * [ok][2013-06-12 11:10:07]
 *
 *
 *
 *
 *
 */
public class AddAndSyncDevices {
    // We ask the ResourceManager to inject a Maapi instance
    // for us.
    // An alternative way is to create the Maapi instance
    // explicitly:
    //
    //  String ncsHost = NcsMain.getInstance().getNcsHost();
    //  int ncsPort = NcsMain.getInstance().getNcsPort();
    //  Maapi maapi = new Maapi ( new Socket ( ncsHost , ncsPort ) );
    //
    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    private Maapi maapi;

    private List<ConfXMLParam> resultParams = null;
    private int NO_LEAF_PER_ENTRY = 6; //cols

    private static final Logger log = LogManager.getLogger(
            AddAndSyncDevices.class);

    private static final String devStatePath = "/lc:example/dev-state";

    List<Device> configuredDevices =
        Arrays.<Device>asList(new Device("ex0", "127.0.0.1", 12022),
                new Device("ex1", "127.0.0.1", 12023),
                new Device("ex2", "127.0.0.1", 12024));


    // The device information about the simulated devices
    // we have 3 nodes which we want to managed , ex0-ex2
    class Device {

        private String name;
        private String ip;
        private int port;
        private ConfEnumeration addedStatus;

        public Device(String name, String ip, int port) {
            this.name = name;
            this.ip = ip;
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public String getIpAddress() {
            return ip;
        }
        public int getPort() {
            return port;
        }

        public void setAddedDeviceStatus(ConfEnumeration status) {
            this.addedStatus = status;
        }

        public ConfEnumeration getAddedDeviceStatus() {
            return this.addedStatus;
        }
    }

    @ActionCallback(callPoint="add-devices-and-sync",callType=ActionCBType.INIT)
    public void init ( DpActionTrans trans ) throws DpCallbackException {
        log.info(" init (th=" + trans.getTransaction() + ") => ok");
        resultParams = new ArrayList<ConfXMLParam>();
    }

    /**
     *  This method is annotated with the @ActionCallback
     *  with information of the callpoint ant call type.
     *
     *  It is required that the method signature is:
     *
     *  public ConfXMLParam[] <method-name>(DpActionTrans,ConfTag,
     *                                     ConfObject[],ConfXMLParam[] )
     *       throws DpCallbackException
     *
     *
     * Where the <method-name> could be a any method name.
     *
     * This action will for each device check it exists
     * already. If the instance does not exist it will
     * set the leafs needed to configure a device.
     *
     * <p>Further the method  will invoke the built in check-sync action
     * to check whether the configuration in the device is in sync with
     * what NCS have in CDB , if not it will call the sync method which
     * will extract the configuration from the device and populate
     * it in the NCS CDB.
     *
     * @param trans The transaction context which is supplied
     * by the NCS-Jvm. It represents the transaction that
     * is started by the CLI when this action is invoked in configuration
     * mode.
     *
     * @param name The ConfTag which represents this action.
     * A toString() will show the string "lc:add-devices-and-sync".
     *
     * @param kp The input parameters that is supplied to the action
     * In this example the input parameter does not exists.
     * The input and output parameters is modeled in the model
     * where the action is defined, in this case in the
     * Add-devices-action.yang.
     *
     * @return The output parameters as specified in the output section
     * in the Add-devices-action.yang.
     *
     * @throws DpCallbackException with the message printed
     * in the CLI.
     */
    @ActionCallback(callPoint="add-devices-and-sync",
                    callType=ActionCBType.ACTION)
    public ConfXMLParam[] action(DpActionTrans trans,
                                 ConfTag name,
                                 ConfObject[] kp,
                                 ConfXMLParam[] params)
    throws DpCallbackException {
        // The log statement will print the string in the
        //  logs/ncs-java-vm.log.
        log.info(" action (thandle=" + trans.getTransaction() + ","  + name +
                 "," + new ConfPath(kp)  + ") =>");

        log.info ("\n" + Arrays.toString(params));

        int th = -1;

        try {
            // The information is need to retrieve the ConfEnumeration
            final ConfPath devStatePath =
                new ConfPath("/lc:example/add-devices-and-sync/" +
                             "device-info/dev-state");

             final ConfPath syncInfoPath =
                new ConfPath("/lc:example/add-devices-and-sync/" +
                             "device-info/sync-info");

            int usid = trans.getUserInfo().getUserId();

            // We already have a user session when we logged in
            // the CLI set the Maapi instance to that user session.

            maapi.setUserSession(trans.getUserInfo().getUserId());

            // Start a transaction explicitly here.
            th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ_WRITE);

            for (Device device : configuredDevices) {
                // ConfXMLParamStart represents the start of the list instance.
                resultParams.add(
                         new ConfXMLParamStart(AddDevicesAction.hash,
                                               AddDevicesAction._device_info));
                //
                resultParams.add(
                         new ConfXMLParamValue(AddDevicesAction.hash,
                                               AddDevicesAction._dev_name,
                                               new ConfBuf(device.getName())));

                // This is the value of the
                ConfEnumeration addedResult = null;

                try {
                    addedResult = ConfEnumeration.getEnumByLabel(devStatePath,
                                                                 "exists");

                    if (createDeviceIfAbsent(device, maapi, th, usid)) {
                        addedResult =
                            ConfEnumeration.getEnumByLabel(devStatePath,
                                                           "added" );
                    }
                } catch (ConfException err) {
                    addedResult =
                        ConfEnumeration.getEnumByLabel(devStatePath, "error");


                } finally {
                    device.setAddedDeviceStatus(addedResult);
                    resultParams.add(
                        new ConfXMLParamValue(AddDevicesAction.hash,
                                              AddDevicesAction._dev_state,
                                              addedResult));
                }

                // defer this actual value to the next loop which we sync
                // if needed.
                resultParams.add (
                        new ConfXMLParamValue(AddDevicesAction.hash,
                                              AddDevicesAction._sync_info,
                                              new ConfNoExists()));

                // defer this actual value to the next loop which we sync
                // if needed.
                resultParams.add (
                        new ConfXMLParamValue(AddDevicesAction.hash,
                                              AddDevicesAction._info,
                                              new ConfBuf("")));


                 // ConfXMLParamStop represents the end of the
                 // list instance.
                resultParams.add(
                        new ConfXMLParamStop(AddDevicesAction.hash,
                                             AddDevicesAction._device_info));

            }
            // we need to apply the transaction before sync from our added
            // devices the action is always towards the running.
            maapi.applyTrans(th, false);
            maapi.finishTrans(th);

            // The sync-info offset in our structure
            int syncInfoOffset = 3;
            // The info offset in our structure
            int infoOffset     = 4;
            // The current list entry index
            int index = 0;
            for (Device device : configuredDevices) {

                // Add to response xml structure
                // Sync the devices if not in sync.
                ConfEnumeration syncResult = null;

                try {
                    syncResult = ConfEnumeration.getEnumByLabel(syncInfoPath,
                                                                "in-sync");

                    // The syncFromDevice() method returns true if it did a sync
                    if (syncFromDevice(device, maapi)) {

                        // Update the value with a info message
                        // of that it performed a sync.
                        ConfXMLParamValue info =
                            (ConfXMLParamValue) resultParams.get(index +
                                                                 infoOffset);
                        info.setValue(new ConfBuf("Performed sync!"));
                    }

                } catch (SyncException e) {
                    // We got an error when we tried to sync the device update
                    // then syncResult to the info message "error"
                    log.error("", e);
                    syncResult = ConfEnumeration.getEnumByLabel(syncInfoPath,
                                                                "error");
                    ConfXMLParamValue info =
                        (ConfXMLParamValue)resultParams.get(index +
                                                            infoOffset);
                    info.setValue(new ConfBuf (e.getMessage()));
                }

                ConfXMLParamValue value =
                    (ConfXMLParamValue) resultParams.get(index +
                                                         syncInfoOffset);

                // Set the syncResult to the xml result
                value.setValue(syncResult);

                // update index to next list entry
                index += NO_LEAF_PER_ENTRY;
            } // for

            // Convert the List<ConfXMLParam> to the array
            // ConfXMLParam[] which is the result structure
            // that is required as return value of this method
            ConfXMLParam[] results =
                resultParams.<ConfXMLParam>toArray(new ConfXMLParam[0]);

            return results;

        } catch (Exception e) {
            throw lounderAndPrint(e);
        }
    }

    /**
     *  Configure a device in the /ncs:devices/device tree
     *  and from the information obtained from the device
     *  structure. Using the maapi instance to write to
     *  transaction which is opened against running associated
     *  with the given transaction handle th.
     *
     *  @param device The name,ip,address of the device
     *
     *  @param maapi The Maapi instance to use to write to
     *
     *  @param th The transaction handle which is used to write to
     *   the transaction
     *
     *  @return true weather it has added the device information
     *  false if it exists already.
     *
     *  @throws ConfException if some maapi calls did fail.
     *   The ConfException extends Exception and it is
     *   the high level exception of the hierarchy. Usually
     *   a MaapiException is thrown
     *
     */
    boolean createDeviceIfAbsent(Device device, Maapi maapi, int th,int usid)
    throws Exception {

        ConfPath ex0Path = new ConfPath("/ncs:devices/device{%s}",
                                        device.getName());

        // we check the existence of the path we want to create
        // this is to prevent the MaapiException to occurred
        // when we try to create a instance that is already presence.
        if (!maapi.exists(th, ex0Path)) {
            maapi.create(th, ex0Path);

            // The ConfBuf corresponds the yang type
            // string in the yang.
            ConfBuf ipAddr = new ConfBuf(device.getIpAddress());

            maapi.setElem(th, ipAddr, ex0Path.copyAppend("address"));

            // The ConfUInt16 corresponds to the uint16 yang type
            ConfUInt16 port = new ConfUInt16(device.getPort());
            maapi.setElem(th, port, ex0Path.copyAppend ("port"));

            ConfBuf authGroupName = new ConfBuf ("default");
            maapi.setElem(th, authGroupName, ex0Path.copyAppend("authgroup"));

            ConfEnumeration unlockedEnum =
                ConfEnumeration.getEnumByLabel(
                         "/ncs:devices/device/state/admin-state", "unlocked");
            maapi.setElem(th, "router-nc-1.0:router-nc-1.0",
                          ex0Path.copyAppend("device-type/netconf/ned-id"));

            maapi.setElem(th, unlockedEnum,
                          ex0Path.copyAppend("state/admin-state"));
            return true;

        }
        return false;
    }

    /**
     *  Calls the built in action sync-from a device if is not in sync already.
     *  calls the check-sync built in action to check if it is in sync.
     *
     *  @param device The device that is performed the check-sync/sync-from
     *
     *
     *  @param maapi The Maapi instance to use to use to call the
     *  built in action check-sync/sync-from.
     *
     *  @return true whether it did a sync-from which means
     *  that the it was out of sync before. False whether
     *  it was in sync already.
     *
     *  @throws Exception if some maapi calls did fail.
     *   The ConfException extends Exception and it is
     *   the high level exception of the hierarchy. Usually
     *   a MaapiException is thrown.
     *
     *  @throws SyncException if the sync-from operation
     *  failed. For example if the device could not be connected
     *  to. This exception is a user defined exception and is
     *  NOT part of the NCS Java API.
     *
     */
    boolean syncFromDevice(Device device, Maapi maapi) throws Exception {

        ConfXMLParam[] isSyncParams =
            maapi.requestAction(new ConfXMLParam[0],
                                "/ncs:devices/device{%s}/check-sync",
                                device.getName());

        ConfEnumeration resultValue =
            (ConfEnumeration)((ConfXMLParamValue)isSyncParams[0]).getValue();

        if (resultValue.getOrdinalValue() != Ncs._in_sync_result)  {
            ConfXMLParam[] syncParamsOut =
                maapi.requestAction(new ConfXMLParam[0],
                                    "/ncs:devices/device{%s}/sync-from",
                                    device.getName());

            ConfXMLParamValue syncResult = (ConfXMLParamValue) syncParamsOut[0];

            ConfBool status = (ConfBool) syncResult.getValue();

            if (!status.booleanValue()) {
                throw new SyncException(
                  ((ConfXMLParamValue) syncParamsOut[1]).getValue().toString());
            }
            return true;

        }
        return false;
    }

    /**
     * Convenience method to filter the stacktrace
     * for showing the relevant stack calls done by this action
     * If an error occurred the stacktracce will show up in the
     * CLI.
     *
     * @param The exception we will be laundered
     *
     * @return A DpCallbackException which the getMessage() will show
     * up in the CLI.
     */
    DpCallbackException lounderAndPrint(Exception e) {
        StackTraceElement[] stElems = e.getStackTrace();

        int i = 0;
        for (; i < stElems.length; i++) {
            if (stElems[i].getClassName().equals(getClass().getName())) {
                break;
            }
        }

        StackTraceElement[] subElems =
            Arrays.<StackTraceElement>copyOfRange(stElems, 0, i+1);
        e.setStackTrace (subElems);
        ByteArrayOutputStream bo = new ByteArrayOutputStream ();


        PrintWriter pw = new PrintWriter(bo);
        e.printStackTrace(pw);
        pw.flush();

        StringBuffer buf = new StringBuffer();
        buf.append ("Error Message => \"" + e.getMessage() + "\"");
        buf.append ("\n");
        buf.append (" XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX \n");
        buf.append (" XXXX  StackTrace from Action \n");
        buf.append (" XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX \n");
        buf.append (" \n ");
        buf.append ( bo.toString () );
        buf.append (" \n ");

        return new DpCallbackException(buf.toString());
    }
}
