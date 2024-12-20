package com.example.cdb;

import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbDiffIterate;
import com.tailf.cdb.CdbException;
import com.tailf.cdb.CdbSession;
import com.tailf.cdb.CdbSubscription;
import com.tailf.cdb.CdbSubscriptionSyncType;
import com.tailf.conf.ConfBinary;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfInt16;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt16;
import com.tailf.conf.ConfValue;
import com.tailf.conf.DiffIterateFlags;
import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.DiffIterateResultFlag;
import com.tailf.conf.ErrorCode;
import com.tailf.examples.router.namespaces.router;
import com.tailf.ncs.ApplicationComponent;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// This low level cdb subscriber subscribes to changes under the path
// /devices/device/config/sys/interfaces/interface.
//
// Whenever a change occurs in the subscription path, the code iterates
// through the change and populate the elements in instance variable
// devices which is a java.util.List.
//
// The devices instance variable is a structure that correspond
// to the yang model in the device model.
//
// In the cdb-example package there is 5 components that all are
// subscribers and prints various information to the ncs-java-log.
// In this example we are focus on what this subscriber prints in
// the ncs-java-log so we need to filter out all the other information
// that other subscriber are printing thus we issue this in CLI:
//
// %> set java-vm java-logger com.example.cdb level level-off
// %> set java-vm java-logger com.example.cdb.ConfigCdbSub level level-info
// %> set java-vm java-logger com.example.cdb.ConfigCdbSub$DiffIterateImpl
// %> commit
//
// Now we sync NCS with our devices
//
// %> request devices direction from device
//
//
// Thus to trigger this subscription code
// go into the ncs_cli and commit any change under the subscription
//     path. For example:
//
// # ncs_cli -u admin
// admin connected from 127.0.0.1 using console on iron.local
// admin@iron> configure
// admin@iron% set devices device ex0 config sys interfaces interface en0
// mac 3c:07:54:71:13:09 mtu 1500 duplex half unit 0 family inet
// address 192.168.1.115 broadcast 192.168.1.255 prefix-length 32
// [ok][2012-07-05 12:57:59]

// [edit]
// admin@iron% commit
// Commit complete.
//
// will trigger the subscription code, the code logs and the data will end up
// in ./logs/ncs-java-vm.log (relative to where the ncs daemon executes)
//
// The code runs in an 'application' component, it implements
// the ApplicationComponent interface, this includes the run() method
// so the code will run in a separate thread started by NCS JVM.
public class ConfigCdbSub implements ApplicationComponent {
    private static final Logger LOGGER
            = LogManager.getLogger(ConfigCdbSub.class);

    // let our ResourceManager inject Cdb sockets to us
    // no explicit creation of creating and opening sockets needed
    @Resource(type = ResourceType.CDB, scope = Scope.INSTANCE,
              qualifier = "sub-sock")
    private Cdb cdbSub;

    @Resource(type = ResourceType.CDB, scope = Scope.INSTANCE,
              qualifier = "data-sock")
    private Cdb cdbData;

    private boolean requestStop;
    private CdbSubscription cdbSubscription;

    // we have 3 devices (ex0,ex1,ex2) but for simplicity we
    // make it a List of n Device instances
    private final List<Device> devices = new ArrayList<>();

    // some data structures which corresponds to the yang model
    // under "/ncs:devices/device/config/sys/interfaces/interface"
    // that the subscriber will use to fill values.
    private static final class Device {
        //one device could have many router interfaces
        private final List<RouterInterface> routerInterfaces
                = new ArrayList<>();
        private final ConfKey key;
        private final int ordinal;

        private Device(ConfKey key, int ordinal) {
            this.key = key;
            this.ordinal = ordinal;
        }

        public int getOrdinal() {
            return ordinal;
        }

        public ConfKey getKey() {
            return this.key;
        }

        public String toString() {
            return key + "[" + routerInterfaces + "]";
        }

        public List<RouterInterface> getInterfaces() {
            return routerInterfaces;
        }
    }

    protected static class RouterInterface {
        protected ConfKey         key;
        protected ConfBuf         description;
        protected ConfEnumeration speed;
        protected ConfEnumeration duplex;
        protected ConfInt16       mtu;
        protected ConfBinary      mac;
        protected List<Unit>      units = new ArrayList<>();

        public String toString() {
            return String.format("[%s \"%s\" %s %s %s]",
                    key, description, speed, duplex, mtu);
        }
    }

    protected static class Unit {
        protected ConfKey key;
        protected ConfBuf description;
        protected ConfUInt16 vlanId;
        protected List<AddressFamily> family = new ArrayList<>();
    }

    protected static class AddressFamily {
        protected ConfKey key;
        protected ConfValue prefixLength;
        protected ConfValue broadCast;
    }


    public void init() {
        LOGGER.info(" init cdb subscriber ");

        try {
            cdbSubscription = cdbSub.newSubscription();
            String path = "/ncs:devices/device/config"
                    + "/r:sys/interfaces/interface";
            cdbSubscription.subscribe(1, router.hash, path);
            cdbSubscription.subscribeDone();
            LOGGER.info("subscribeDone");
            requestStop = false;
        } catch (Exception e) {
            LOGGER.error("Fail in init", e);
        }
    }

    public void finish() {
        requestStop = true;
        try {
            ResourceManager.unregisterResources(this);
        } catch (Exception e) {
            LOGGER.error("Fail in finish", e);
        }
    }

    public void run() {
        readDb(cdbData);
        dumpDb();

        try {
            while (!requestStop) {
                try {
                    int[] point = cdbSubscription.read();
                    CdbSession cdbSession
                            = cdbData.startSession(CdbDBType.CDB_RUNNING);
                    EnumSet<DiffIterateFlags> diffFlags
                            = EnumSet.of(DiffIterateFlags.ITER_WANT_PREV);
                    cdbSubscription.diffIterate(point[0], new DiffIterateImpl(),
                                                diffFlags, cdbSession);
                    cdbSession.endSession();
                    dumpDb();
                } finally {
                    cdbSubscription.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Fail in run", e);
        }
        requestStop = false;
    }

    void dumpDb() {
        LOGGER.info(" ******************** DUMP DB ******************** ");
        try {
            for (Device device : devices) {
                LOGGER.info(" Device " + device.key);
                ConfPath ifPath = new ConfPath(cdbData,
                        "/ncs:devices/device[%d]/config"
                                + "/r:sys/interfaces/interface",
                        device.getOrdinal());

                for (RouterInterface routerInterface: device.getInterfaces()) {
                    LOGGER.info("    INTERFACE");
                    LOGGER.info("      name: " + routerInterface.key);
                    LOGGER.info("      description: "
                            + routerInterface.description);
                    LOGGER.info("      speed: " + ConfEnumeration
                            .getLabelByEnum(ifPath.copyAppend("speed"),
                                            routerInterface.speed));
                    LOGGER.info("      duplex: " + ConfEnumeration
                            .getLabelByEnum(ifPath.copyAppend("duplex"),
                                            routerInterface.duplex));
                    LOGGER.info("      mtu: " + routerInterface.mtu);
                    LOGGER.info("      mac: " + routerInterface.mac);

                    for (Unit unit: routerInterface.units) {
                        LOGGER.info("      UNIT");
                        LOGGER.info("        name: " + unit.key);
                        LOGGER.info("        description: " + unit.description);
                        LOGGER.info("        vlan-id: " + unit.vlanId);

                        for (AddressFamily addressFamily : unit.family) {
                            LOGGER.info("        ADDRESS-FAMILY");
                            LOGGER.info("          key: " + addressFamily.key);
                            LOGGER.info("          prefixLength: "
                                    + addressFamily.prefixLength);
                            LOGGER.info("          broadCast: "
                                    + addressFamily.broadCast);
                        }
                    }
                }
            }
        } catch (ConfException e) {
            LOGGER.error("Fail in dumpdb", e);
        }
    }

    private Device device(ConfKey devKey) {
        Device foundDevice = null;
        for (Device device : devices) {
            if (device.key.equals(devKey)) {
                foundDevice = device;
                break;
            }
        }
        return foundDevice;
    }


    private RouterInterface routerInterface(ConfKey devKey, ConfKey routerKey) {
        ConfigCdbSub.RouterInterface foundInterface = null;

        for (Device device: devices) {
            if (device.key.equals(devKey)) {
                for (RouterInterface routerInterface: device.getInterfaces()) {
                    if (routerInterface.key.equals(routerKey)) {
                        foundInterface = routerInterface;
                        break;
                    }
                }
            }
            if (foundInterface != null) {
                break;
            }
        }
        return foundInterface;
    }

    private Unit routerInterfaceUnit(ConfKey devKey,
                                     ConfKey routerIfKey,
                                     ConfKey unitKey) {
        RouterInterface routerInterface = routerInterface(devKey, routerIfKey);
        Unit foundUnit = null;
        for (Unit unit : routerInterface.units) {
            if (unit.key.equals(unitKey)) {
                foundUnit = unit;
                break;
            }
        }
        return foundUnit;
    }

    private AddressFamily addressFamily(ConfKey devKey,
                                        ConfKey routerIfKey,
                                        ConfKey unitKey,
                                        ConfKey addressFamilyKey) {
        Unit unit = routerInterfaceUnit(devKey, routerIfKey, unitKey);
        AddressFamily foundAddressFamily = null;
        for (AddressFamily addressFamily : unit.family) {
            if (addressFamily.key.equals(addressFamilyKey)) {
                foundAddressFamily = addressFamily;
                break;
            }
        }
        return foundAddressFamily;
    }

    private void readDb(Cdb cdbDataSocket) {
        CdbSession cdbSession;
        try {
            cdbSession = cdbDataSocket.startSession(CdbDBType.CDB_RUNNING);
            int d = cdbSession.getNumberOfInstances("/ncs:devices/device");
            for (int i = 0; i < d; i++) {
                String devicePath = "/ncs:devices/device[%d]";
                ConfValue devName = cdbSession.getElem(devicePath + "/name", i);
                boolean containsDevice = false;

                for (Device device : devices) {
                    if (device.key.equals(new ConfKey(devName))) {
                        containsDevice = true;
                        break;
                    }
                }

                if (!containsDevice) {
                    devices.add(new Device(new ConfKey(devName), i));
                }
                String interfacePath = devicePath + "/config/"
                        + "r:sys/interfaces/interface";

                int nInterfaces = cdbSession.getNumberOfInstances(
                        new ConfPath(cdbDataSocket,
                        interfacePath, i));

                for (int j = 0; j < nInterfaces; j++) {
                    String ifNamePath = interfacePath + "[%d]/name";
                    ConfValue ifKey = cdbSession.getElem(
                            new ConfPath(ifNamePath, i, j));
                    readIf(cdbSession, new ConfKey(devName),
                            new ConfKey(ifKey));
                    String unitPath = interfacePath + "[%d]/unit";
                    int nUnits = cdbSession.getNumberOfInstances(
                            new ConfPath(unitPath, i, j));
                    LOGGER.info(" rUnits:" + nUnits);
                    for (int k = 0; k < nUnits; k++) {
                        ConfValue unitKey = cdbSession.getElem(
                                new ConfPath(unitPath + "[%d]/name", i, j, k));
                        readUnit(cdbSession, new ConfKey(devName),
                                 new ConfKey(ifKey), new ConfKey(unitKey));

                        try {
                            ConfTag tag = (ConfTag) cdbSession.getCase(
                                    "family",
                                    "/ncs:devices/device[%d]/"
                                            + "config/r:sys/interfaces/"
                                            + "interface[%d]/unit[%d]/family",
                                    i, j, k);
                            String addrFamilyPath = unitPath + "[%d]/family";

                            if (router._c1 == tag.getTagHash()) {
                                addrFamilyPath += "/inet";
                            } else if (router._c2 == tag.getTagHash()) {
                                addrFamilyPath += "/inet6";
                            }

                            addrFamilyPath += "/address";
                            int nAddress = cdbSession.getNumberOfInstances(
                                new ConfPath(addrFamilyPath, i, j, k));

                            for (int l = 0; l < nAddress; l++) {
                                ConfValue addrKey = cdbSession.getElem(
                                    new ConfPath(addrFamilyPath
                                            + "[%d]/name", i, j, k, l));
                                readFamily(cdbSession,
                                           new ConfKey(devName),
                                           new ConfKey(ifKey),
                                           new ConfKey(unitKey),
                                           new ConfKey(addrKey));
                            }
                        } catch (CdbException e) {
                            if (e.getErrorCode() == ErrorCode.ERR_NOEXISTS) {
                                LOGGER.warn(e.getMessage());
                            } else {
                                LOGGER.error("Fail in readDB", e);
                            }
                        } catch (ConfException e) {
                            LOGGER.error("Fail in readDB", e);
                        }
                    }
                }

            }
            cdbSession.endSession();
        } catch (Exception e) {
            LOGGER.error("Could not start new session ", e);
        }
    }

    private void readIf(CdbSession cdbSession, ConfKey deviceKey, ConfKey key) {
        LOGGER.info(" devices :" + devices);
        try {
            RouterInterface currentRouterIf = new RouterInterface();

            for (Device device : devices) {
                if (device.getKey().equals(deviceKey)) {
                    device.getInterfaces().add(currentRouterIf);
                    break;
                }
            }

            ConfPath ifPath = new ConfPath(
                    "/ncs:devices/device{%x}/config/r:sys/"
                            + "interfaces/interface{%x}",
                    deviceKey, key);
            cdbSession.cd(ifPath);
            currentRouterIf.key = new ConfKey(cdbSession.getElem("name"));
            currentRouterIf.description
                    = (ConfBuf) cdbSession.getElem("description");
            currentRouterIf.speed
                    = (ConfEnumeration) cdbSession.getElem("speed");
            currentRouterIf.duplex
                    = (ConfEnumeration) cdbSession.getElem("duplex");
            currentRouterIf.mtu = (ConfInt16) cdbSession.getElem("mtu");
            currentRouterIf.mac = (ConfBinary) cdbSession.getElem("mac");
        } catch (Exception e) {
            LOGGER.error("Could not read values", e);
        }
    }

    private void readFamily(CdbSession cdbSession, ConfKey devKey,
                            ConfKey routerIfKey, ConfKey unitKey,
                            ConfKey addressFamilyKey) {
        try {
            AddressFamily currentFamily = addressFamily(
                    devKey, routerIfKey,
                    unitKey, addressFamilyKey);
            if (currentFamily == null) {
                currentFamily = new AddressFamily();
                routerInterfaceUnit(devKey, routerIfKey, unitKey).family
                        .add(currentFamily);
            }
            ConfTag tag;
            tag = (ConfTag) cdbSession.getCase("family",
                    "/ncs:devices/device{%x}/config/r:sys/"
                            + "interfaces/interface{%x}/unit{%x}/family",
                    devKey, routerIfKey, unitKey);
            String inetPathString;
            if (tag.getTagHash() == router._c2) {
                // IPv6
                inetPathString = "/ncs:devices/device{%x}/config/r:sys/"
                        + "interfaces/interface{%x}/unit{%x}/family"
                        + "/inet6/address{%x}";
            } else {
                // IPv4
                inetPathString = "/ncs:devices/device{%x}/config/r:sys/"
                        + "interfaces/interface{%x}/unit{%x}/family"
                        + "/inet/address{%x}";
            }
            ConfPath inetPath = new ConfPath(inetPathString, devKey,
                        routerIfKey, unitKey, addressFamilyKey);

            cdbSession.cd(inetPath);
            currentFamily.key = new ConfKey(cdbSession.getElem("name"));
            currentFamily.prefixLength
                    = cdbSession.getElem("prefix-length");
            currentFamily.broadCast = cdbSession.getElem("broadcast");
        } catch (Exception e) {
            LOGGER.error("Fail in readFamily", e);
        }
    }

    private void readUnit(CdbSession cdbSession, ConfKey devKey, ConfKey ifKey,
                          ConfKey unitKey) {
        LOGGER.info(" Reading units ** ");
        try {
            Unit currentUnit = routerInterfaceUnit(devKey, ifKey, unitKey);
            if (currentUnit == null) {
                currentUnit = new Unit();
                routerInterface(devKey, ifKey).units.add(currentUnit);
            }

            ConfPath ifPath = new ConfPath(
                    "/ncs:devices/device{%x}/config/r:sys/"
                            + "interfaces/interface{%x}/unit{%x}",
                    devKey, ifKey, unitKey);
            cdbSession.cd(ifPath);
            currentUnit.key = new ConfKey(cdbSession.getElem("name"));
            currentUnit.description
                    = (ConfBuf) cdbSession.getElem("description");
            currentUnit.vlanId = (ConfUInt16) cdbSession.getElem("vlan-id");
        } catch (Exception e) {
            LOGGER.error("Fail in readUnit", e);
        }
    }

    private void updateRouterAddress(ConfObject newValue, ConfTag inetMod,
                                     ConfKey addressKey, ConfKey deviceKey,
                                     ConfKey unitKey, ConfKey routerIfKey) {
        switch (inetMod.getTagHash()) {
            case router._name: {
                addressFamily(deviceKey, routerIfKey, unitKey, addressKey)
                        .key = new ConfKey(newValue);
                break;
            }
            case router._prefix_length: {
                addressFamily(deviceKey, routerIfKey, unitKey, addressKey)
                        .prefixLength = (ConfValue) newValue;
                break;
            }
            case router._broadcast: {
                addressFamily(deviceKey, routerIfKey, unitKey, addressKey)
                        .broadCast = (ConfValue) newValue;
                break;
            }
            default: {
                LOGGER.warn("Unhandled router address configuration: "
                        + inetMod.getTag());
            }
        }
    }

    private void updateRouterUnit(ConfObject newValue, ConfTag unitProp,
                                  ConfKey unitKey, ConfKey routerIfKey,
                                  ConfKey deviceKey) {
        Unit unit = routerInterfaceUnit(deviceKey, routerIfKey, unitKey);
        switch (unitProp.getTagHash()) {
            case router._name: {
                unit.key = new ConfKey(newValue);
                break;
            }
            case router._description: {
                unit.description = (ConfBuf) newValue;
                break;
            }
            case router._vlan_id: {
                unit.vlanId = (ConfUInt16) newValue;
                break;
            }
            default: {
                LOGGER.warn("Unhandled router unit configuration: "
                        + unitProp.getTag());
            }
        }
    }

    private void updateRouterInterface(ConfObject newValue, ConfTag tag,
                                       ConfKey routerKey, ConfKey deviceKey) {
        RouterInterface routerInterface
                = routerInterface(deviceKey, routerKey);
        switch (tag.getTagHash()) {
            case router._name: {
                routerInterface.key = new ConfKey(newValue);
                break;
            }
            case router._description: {
                routerInterface.description = (ConfBuf) newValue;
                break;
            }
            case router._speed: {
                routerInterface.speed = (ConfEnumeration) newValue;
                break;
            }
            case router._duplex: {
                routerInterface.duplex = (ConfEnumeration) newValue;
                break;
            }
            case router._mtu: {
                routerInterface.mtu = (ConfInt16) newValue;
                break;
            }
            case router._mac: {
                routerInterface.mac = (ConfBinary) newValue;
                break;
            }
            default: {
                LOGGER.warn("Unhandled router interface configuration: "
                        + tag.getTag());
            }
        }
    }


    // This class implements the com.tailf.cdb.CdbDiffIterate
    private class DiffIterateImpl implements CdbDiffIterate {
        // We create a logger for this inner class
        // to see what this class prints out.
        // The logger must be configured which can
        // be done in the CLI.
        // %> set java-vm java-logger
        //     com.example.cdb.ConfigCdbSub$DiffIterateImpl level-info
        // %> commit
        private final Logger log
                = LogManager.getLogger(DiffIterateImpl.class);

        public DiffIterateResultFlag iterate(ConfObject[] kp,
                                             DiffIterateOperFlag op,
                                             ConfObject oldValue,
                                             ConfObject newValue,
                                             Object initstate) {
            CdbSession cdbSession = (CdbSession) initstate;
            ConfPath path = new ConfPath(kp);

            log.info(path + " --> " + op);
            log.info("kp=" + java.util.Arrays.toString(kp));

            switch (op) {
                case MOP_CREATED: {
                    log.info("Created " + path);
                    // the case in which some entry has been created
                    // in the list or a presence container has been created
                    // however we do not have a presence container in this
                    // example.
                    // Retrieve the list tag, kp[0] should always be
                    // the ConfKey
                    ConfTag tag = (ConfTag) kp[1];

                    // What was created?
                    switch (tag.getTagHash()) {
                        case router._interface: {
                            // a router interface was created with keypath:
                            // /devices/device{$key1}/config/r:sys
                            // /interfaces/interface{$key2}
                            readIf(cdbSession, (ConfKey) kp[5],
                                    (ConfKey) kp[0]);
                            break;
                        }
                        case router._unit: {
                            // new unit has been added to existing interface
                            // /devices/device{$key1}/config/r:sys
                            // /interfaces/interface{$key2}/unit{$key3}
                            readUnit(cdbSession, (ConfKey) kp[7],
                                    (ConfKey) kp[2], (ConfKey) kp[0]);
                            break;
                        }
                        case router._address: {
                            // new address has been added to existing unit
                            // with keypath:
                            // /devices/device{$key1}/config/r:sys
                            // /interfaces/interface{$key2}/unit{$key3}
                            // /family/[inet|inet6]/address{$key4}
                            readFamily(cdbSession, (ConfKey) kp[11],
                                    (ConfKey) kp[6], (ConfKey) kp[4],
                                    (ConfKey) kp[0]);
                            break;
                        }
                        default: {
                            LOGGER.warn("Unhandled MOP_CREATED: "
                                    + tag.getTag());
                        }
                    }
                    return DiffIterateResultFlag.ITER_RECURSE;
                }
                case MOP_DELETED: {
                    log.info("Delete: " + path);
                    ConfTag tag = (ConfTag) kp[1];

                    switch (tag.getTagHash()) {
                        case router._interface: {
                            // a router interface was deleted with keypath:
                            // /devices/device{$key}/config/r:sys
                            // /interfaces/interface{$key2}
                            ConfKey devKey = (ConfKey) kp[5];
                            ConfKey ifKey = (ConfKey) kp[0];
                            device(devKey).getInterfaces().remove(
                                    routerInterface(devKey, ifKey));
                            return DiffIterateResultFlag.ITER_CONTINUE;
                        }
                        case router._unit: {
                            // a router interface unit was deleted with keypath:
                            // /devices/device{$key1}/config/r:sys/interfaces
                            // /interface{$key2}/unit{$key3}
                            ConfKey devKey = (ConfKey) kp[7];
                            ConfKey ifKey = (ConfKey) kp[2];
                            ConfKey unitKey = (ConfKey) kp[0];

                            routerInterface(devKey, ifKey).units
                                    .remove(routerInterfaceUnit(devKey, ifKey,
                                            unitKey));
                            return DiffIterateResultFlag.ITER_CONTINUE;
                        }
                        case router._address: {
                            // a router interface unit was deleted with keypath:
                            // /devices/device{$key1}/config/r:sys/interfaces
                            // /interface{$key2}/unit{$key3}
                            // /family/[inet|inet6]/address{$key4}
                            ConfKey devKey = (ConfKey) kp[11];
                            ConfKey ifKey = (ConfKey) kp[6];
                            ConfKey unitKey = (ConfKey) kp[4];
                            ConfKey addrKey = (ConfKey) kp[0];

                            routerInterfaceUnit(devKey, ifKey, unitKey).family
                                    .remove(addressFamily(devKey, ifKey,
                                            unitKey, addrKey));
                            return DiffIterateResultFlag.ITER_CONTINUE;
                        }
                        default: {
                            LOGGER.warn("Unhandled MOP_DELETED: "
                                    + tag.getTag());
                        }
                    }
                }
                case MOP_MODIFIED: {
                    log.info("Modified " + path);
                    return DiffIterateResultFlag.ITER_RECURSE;
                }
                case MOP_VALUE_SET: {
                    log.info("Value set " + path);
                    // /devices/device{$key}/config/r:router/interfaces
                    // /interface{$key2}/unit{$key3}
                    // /family/[inet|inet6]/address{$key4}/name
                    ConfTag modTag = (ConfTag) kp[2];
                    switch (modTag.getTagHash()) {
                        case router._interface: {
                            // keypath is:
                            // /devices/device{$key}/config/r:router
                            // /interfaces/interface{$key2}/
                            // [description|speed|duplex|mtu|mac]
                            ConfTag tag = (ConfTag) kp[0];
                            ConfKey routerKey = (ConfKey) kp[1];
                            ConfKey deviceKey = (ConfKey) kp[6];

                            updateRouterInterface(newValue, tag, routerKey,
                                    deviceKey);
                            return DiffIterateResultFlag.ITER_RECURSE;
                        }
                        case router._unit: {
                            // /devices/device{$key}/config/r:router
                            // /interfaces/interface{$key2}
                            // /unit{$key3}/[name|description|vlan-id]
                            ConfTag unitProp = (ConfTag) kp[0];
                            ConfKey unitKey = (ConfKey) kp[1];
                            ConfKey routerIfKey = (ConfKey) kp[3];
                            ConfKey deviceKey = (ConfKey) kp[8];

                            updateRouterUnit(newValue, unitProp, unitKey,
                                    routerIfKey, deviceKey);
                            return DiffIterateResultFlag.ITER_RECURSE;
                        }
                        case router._address: {
                            // /devices/device{$k1}/config/r:router/interfaces
                            // /interface{$k2}/unit{$k3}
                            // /family/[inet|inet6]/address{$key4}/name
                            ConfTag inetMod = (ConfTag) kp[0];
                            ConfKey addressKey = (ConfKey) kp[1];
                            ConfKey deviceKey = (ConfKey) kp[12];
                            ConfKey unitKey = (ConfKey) kp[5];
                            ConfKey routerIfKey = (ConfKey) kp[7];

                            updateRouterAddress(newValue, inetMod, addressKey,
                                    deviceKey, unitKey, routerIfKey);
                            return DiffIterateResultFlag.ITER_RECURSE;
                        }
                        default: {
                            LOGGER.warn("Unhandled MOP_VALUE_SET: "
                                    + modTag.getTag());
                        }
                    }
                }
                default: {
                    log.debug(String.format("Unhandled DiffIterateOperFlag"
                            + " %s for path: %s", op, path));
                    return DiffIterateResultFlag.ITER_CONTINUE;
                }
            }
        }
    }
}
