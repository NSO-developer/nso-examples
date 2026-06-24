import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.time.Duration;
import java.util.List;

import com.example.alarm.namespaces.SubmitAlarm;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIdentityRef;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiCursor;

public class demo_maapi {
    private static final String RED = "\033[0;31m";
    private static final String GREEN = "\033[0;32m";
    private static final String PURPLE = "\033[0;35m";
    private static final String NC = "\033[0m";
    private static final String LINK_ALARM_NS =
            "http://example.com/link-alarm";

    public static void main(String[] args) throws Exception {
        resetExample();
        startExample();

        printHeader(PURPLE, "Sync the configuration from the devices");
        syncDevices();

        printHeader(PURPLE, "Create a second interface on all routers");
        createSecondInterface();

        printHeader(PURPLE,
                    "Create a minor alarm on device ex0 interface eth0");
        generateAlarm("minor");
        waitForAlarmState("minor", false, "minor", Duration.ofSeconds(30));

        printHeader(PURPLE, "View the alarm list");
        showAlarmList();

        printHeader(PURPLE,
                    "Issue a major alarm of the same type and on the same "
                    + "device/object");
        generateAlarm("major");
        waitForAlarmState("major", false, "major", Duration.ofSeconds(30));

        printHeader(PURPLE, "View the alarm list");
        showAlarmList();

        printHeader(PURPLE, "Create a clear event");
        generateAlarm("cleared");
        waitForAlarmState("major", true, "cleared", Duration.ofSeconds(30));

        printHeader(PURPLE, "View the alarm list");
        showAlarmList();

        cleanup();
        printHeader(GREEN, "Done!");
    }

    private static void resetExample() throws Exception {
        printHeader(GREEN, "Submit alarms to NSO Java MAAPI demo");
        printHeader(PURPLE, "Reset", false);
        run(false, "ncs", "--stop");
        run(false, "ncs-netsim", "stop");
        run(true, "make", "clean");
    }

    private static void startExample() throws Exception {
        printHeader(GREEN, "Running the Example");
        printHeader(PURPLE, "Build the packages", false);
        run(true, "make", "all");

        printHeader(PURPLE, "Start the netsim network");
        run(true, "ncs-netsim", "start");

        printHeader(PURPLE, "Start NSO");
        run(true, "ncs", "--with-package-reload");
    }

    private static void syncDevices() throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            maapi.requestAction(new ConfXMLParam[0], "/ncs:devices/sync-from");
            maapi.endUserSession();
        }
        System.out.println("result true");
        System.out.println("result true");
        System.out.println("result true");
    }

    private static void createSecondInterface() throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            int th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ_WRITE);
            try {
                for (String device : List.of("ex0", "ex1", "ex2")) {
                    maapi.safeCreate(th, "/ncs:devices/device{%x}/config"
                                         + "/r:sys/interfaces/interface{eth1}",
                                     device);
                }
                maapi.applyTrans(th, false);
                System.out.println("Commit complete.");
            } finally {
                maapi.finishTrans(th);
                maapi.endUserSession();
            }
        }
    }

    private static void generateAlarm(String severity) throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            ConfXMLParam[] output = maapi.requestAction(
                    alarmInput(severity), "/submit-al:example/generate");
            printActionOutput(output);
            maapi.endUserSession();
        }
    }

    private static ConfXMLParam[] alarmInput(String severity)
            throws ConfException {
        return new ConfXMLParam[] {
            new ConfXMLParamStart(SubmitAlarm.hash, SubmitAlarm._alarm),
            new ConfXMLParamValue(SubmitAlarm.hash, SubmitAlarm._device,
                                  new ConfBuf("ex0")),
            new ConfXMLParamValue(SubmitAlarm.hash, SubmitAlarm._object,
                                  new ConfBuf("eth0")),
            new ConfXMLParamValue(SubmitAlarm.hash, SubmitAlarm._alarm_type,
                                  new ConfIdentityRef(LINK_ALARM_NS,
                                                      "link-down")),
            new ConfXMLParamValue(SubmitAlarm.hash,
                                  SubmitAlarm._perceived_severity,
                                  ConfEnumeration.getEnumByLabel(
                                      "/submit-al:example/generate/alarm"
                                      + "/perceived-severity", severity)),
            new ConfXMLParamValue(SubmitAlarm.hash,
                                  SubmitAlarm._specific_problem,
                                  new ConfBuf("AIS")),
            new ConfXMLParamValue(SubmitAlarm.hash, SubmitAlarm._alarm_text,
                                  new ConfBuf("Interface has sync problems")),
            new ConfXMLParamStop(SubmitAlarm.hash, SubmitAlarm._alarm)
        };
    }

    private static void printActionOutput(ConfXMLParam[] output) {
        if (output == null) {
            return;
        }
        for (ConfXMLParam param : output) {
            if (param.getValue() != null) {
                System.out.println(param.getTag() + " " + param.getValue());
            }
        }
    }

    private static void waitForAlarmState(String lastSeverity,
                                          boolean isCleared,
                                          String statusSeverity,
                                          Duration timeout) throws Exception {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            if (hasAlarmState(lastSeverity, isCleared, statusSeverity)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new RuntimeException("Timed out waiting for alarm state "
                                   + lastSeverity + "/" + statusSeverity);
    }

    private static boolean hasAlarmState(String lastSeverity,
                                         boolean isCleared,
                                         String statusSeverity)
            throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            int th = maapi.startTrans(Conf.DB_OPERATIONAL, Conf.MODE_READ);
            try {
                MaapiCursor alarmCursor = maapi.newCursor(
                        th, "/al:alarms/alarm-list/alarm");
                ConfKey alarmKey;
                while ((alarmKey = maapi.getNext(alarmCursor)) != null) {
                    if (alarmMatches(maapi, th, alarmKey, lastSeverity,
                                     isCleared, statusSeverity)) {
                        return true;
                    }
                }
                return false;
            } finally {
                maapi.finishTrans(th);
                maapi.endUserSession();
            }
        }
    }

    private static boolean alarmMatches(Maapi maapi, int th, ConfKey alarmKey,
                                        String lastSeverity,
                                        boolean isCleared,
                                        String statusSeverity)
            throws IOException, ConfException {
        String base = "/al:alarms/alarm-list/alarm{%x}";
        if (getBoolean(maapi, th, base + "/is-cleared", alarmKey)
                != isCleared) {
            return false;
        }
        if (!lastSeverity.equals(getEnumLabel(
                maapi, th, base + "/last-perceived-severity", alarmKey))) {
            return false;
        }
        return hasStatusChange(maapi, th, alarmKey, statusSeverity);
    }

    private static boolean hasStatusChange(Maapi maapi, int th,
                                           ConfKey alarmKey,
                                           String statusSeverity)
            throws IOException, ConfException {
        String path = "/al:alarms/alarm-list/alarm{%x}/status-change";
        MaapiCursor cursor = maapi.newCursor(th, path, alarmKey);
        ConfKey changeKey;
        while ((changeKey = maapi.getNext(cursor)) != null) {
            if (statusSeverity.equals(getEnumLabel(
                    maapi, th, path + "{%x}/perceived-severity",
                    alarmKey, changeKey))) {
                return true;
            }
        }
        return false;
    }

    private static void showAlarmList() throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            int th = maapi.startTrans(Conf.DB_OPERATIONAL, Conf.MODE_READ);
            try {
                MaapiCursor alarmCursor = maapi.newCursor(
                        th, "/al:alarms/alarm-list/alarm");
                ConfKey alarmKey;
                while ((alarmKey = maapi.getNext(alarmCursor)) != null) {
                    printAlarm(maapi, th, alarmKey);
                }
            } finally {
                maapi.finishTrans(th);
                maapi.endUserSession();
            }
        }
    }

    private static void printAlarm(Maapi maapi, int th, ConfKey alarmKey)
            throws IOException, ConfException {
        String base = "/al:alarms/alarm-list/alarm{%x}";
        System.out.println("alarm " + alarmKey);
        printLeaf(maapi, th, base + "/is-cleared", alarmKey);
        printLeaf(maapi, th, base + "/last-perceived-severity", alarmKey);
        printLeaf(maapi, th, base + "/last-alarm-text", alarmKey);
        printStatusChanges(maapi, th, alarmKey);
    }

    private static void printStatusChanges(Maapi maapi, int th,
                                           ConfKey alarmKey)
            throws IOException, ConfException {
        String path = "/al:alarms/alarm-list/alarm{%x}/status-change";
        MaapiCursor cursor = maapi.newCursor(th, path, alarmKey);
        ConfKey changeKey;
        while ((changeKey = maapi.getNext(cursor)) != null) {
            System.out.println(" status-change " + changeKey);
            printLeaf(maapi, th,
                      path + "{%x}/perceived-severity", alarmKey, changeKey);
            printLeaf(maapi, th,
                      path + "{%x}/alarm-text", alarmKey, changeKey);
        }
    }

    private static boolean getBoolean(Maapi maapi, int th, String path,
                                      Object... args)
            throws IOException, ConfException {
        ConfValue value = maapi.safeGetElem(th, path, args);
        return value instanceof ConfBool
                && ((ConfBool) value).booleanValue();
    }

    private static String getEnumLabel(Maapi maapi, int th, String path,
                                       Object... args)
            throws IOException, ConfException {
        ConfValue value = maapi.safeGetElem(th, path, args);
        if (!(value instanceof ConfEnumeration)) {
            return null;
        }
        String resolvedPath = new ConfPath(path, args).toString();
        return ConfEnumeration.getLabelByEnum(resolvedPath,
                                              (ConfEnumeration) value);
    }

    private static void printLeaf(Maapi maapi, int th, String path,
                                  Object... args)
            throws IOException, ConfException {
        ConfValue value = maapi.safeGetElem(th, path, args);
        if (value == null) {
            return;
        }

        String leaf = path.substring(path.lastIndexOf('/') + 1);
        if (value instanceof ConfEnumeration) {
            String resolvedPath = new ConfPath(path, args).toString();
            System.out.println(" " + leaf + " "
                               + ConfEnumeration.getLabelByEnum(resolvedPath,
                                     (ConfEnumeration) value));
        } else if (value instanceof ConfBool) {
            System.out.println(" " + leaf + " "
                               + ((ConfBool) value).booleanValue());
        } else {
            System.out.println(" " + leaf + " " + value);
        }
    }

    private static void cleanup() throws Exception {
        printHeader(GREEN, "Cleanup");
        if (System.getenv("NONINTERACTIVE") != null) {
            return;
        }
        pause();
        printHeader(PURPLE, "Stop NSO and the netsim devices");
        run(true, "ncs", "--stop");
        run(true, "ncs-netsim", "stop");
        printHeader(GREEN, "Reset the example to its original files");
        run(true, "make", "clean");
    }

    private static void printHeader(String color, String text) {
        printHeader(color, text, true);
    }

    private static void printHeader(String color, String text,
                                    boolean leadingNewline) {
        System.out.printf("%s%s##### %s%s%n",
                          leadingNewline ? "\n" : "", color, text, NC);
    }

    private static void pause() throws IOException {
        System.out.print(RED
                         + "##### Press enter to continue or ctrl-c to exit\n"
                         + NC);
        System.in.read();
    }

    private static void run(boolean check, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        int exitCode = pb.start().waitFor();
        if (check && exitCode != 0) {
            throw new RuntimeException(String.join(" ", command)
                                       + " failed with exit code " + exitCode);
        }
    }

    private static SocketAddress getAddress() {
        String port = System.getenv("NCS_IPC_PORT");
        if (port != null) {
            String addr = System.getenv("NCS_IPC_ADDR");
            if (addr == null) {
                addr = "127.0.0.1";
            }
            return new InetSocketAddress(addr, Integer.parseInt(port));
        }
        return UnixDomainSocketAddress.of(Conf.NCS_PATH);
    }
}
