import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfXMLParam;
import com.tailf.maapi.Maapi;

public class demo_maapi {
    private static final String RED = "\033[0;31m";
    private static final String GREEN = "\033[0;32m";
    private static final String PURPLE = "\033[0;35m";
    private static final String NC = "\033[0m";
    private static final Path LOG_PATH = Path.of("logs/ncs-java-vm.log");

    public static void main(String[] args) throws Exception {
        resetExample();
        startExample();

        printHeader(PURPLE, "Sync the configuration from the devices");
        syncDevices();

        printHeader(PURPLE, "Trigger the CDB configuration data subscriber");
        triggerConfigSubscriber();

        printHeader(PURPLE,
                    "Resulting log entries in logs/ncs-java-vm.log");
        waitForLog("4.5.6.7", Duration.ofSeconds(30));
        printMatchingLogLines("4.5.6.7");

        printHeader(PURPLE, "Trigger the CDB operational data subscriber");
        createOperStatsItem("dawnfm");
        deleteOperStatsItem("dawnfm");

        printHeader(PURPLE,
                    "Resulting log entries in logs/ncs-java-vm.log");
        waitForLog("dawnfm", Duration.ofSeconds(30));
        printMatchingLogLines("dawnfm");

        cleanup();
        printHeader(GREEN, "Done!");
    }

    private static void resetExample() throws Exception {
        printHeader(GREEN, "CDB API Java MAAPI subscriber demo");
        printHeader(PURPLE, "Reset", false);
        run(false, "make", "stop");
        run(true, "make", "clean");
    }

    private static void startExample() throws Exception {
        printHeader(GREEN, "Running the Example");
        printHeader(PURPLE, "Build the two packages", false);
        run(true, "make", "all");

        printHeader(PURPLE,
                    "Start NSO, the subscribers, and the netsim network");
        run(true, "ncs-netsim", "start");
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

    private static void triggerConfigSubscriber() throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            int th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ_WRITE);
            try {
                String path = "/ncs:devices/device{ex0}/config"
                              + "/r:sys/syslog/server{4.5.6.7}";
                maapi.create(th, path);
                maapi.setElem(th, "true", path + "/enabled");
                maapi.applyTrans(th, false);
                System.out.println("Commit complete.");
            } finally {
                maapi.finishTrans(th);
            }

            th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ_WRITE);
            try {
                maapi.delete(th, "/ncs:devices/device{ex0}/config"
                                 + "/r:sys/syslog/server{4.5.6.7}");
                maapi.applyTrans(th, false);
                System.out.println("Commit complete.");
            } finally {
                maapi.finishTrans(th);
                maapi.endUserSession();
            }
        }
    }

    private static void createOperStatsItem(String key) throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            int th = maapi.startTrans(Conf.DB_OPERATIONAL,
                                      Conf.MODE_READ_WRITE);
            try {
                maapi.create(th, "/t:test/stats-item{%x}", key);
                maapi.setElem(th, new ConfInt32(key.hashCode()),
                              "/t:test/stats-item{%x}/i", key);
                maapi.setElem(th, new ConfBuf("boogaloo " + key),
                              "/t:test/stats-item{%x}/inner/l", key);
                maapi.applyTrans(th, false);
            } finally {
                maapi.finishTrans(th);
                maapi.endUserSession();
            }
        }
    }

    private static void deleteOperStatsItem(String key)
            throws IOException, ConfException {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
            int th = maapi.startTrans(Conf.DB_OPERATIONAL,
                                      Conf.MODE_READ_WRITE);
            try {
                maapi.delete(th, "/t:test/stats-item{%x}", key);
                maapi.applyTrans(th, false);
            } finally {
                maapi.finishTrans(th);
                maapi.endUserSession();
            }
        }
    }

    private static void waitForLog(String text, Duration timeout)
            throws Exception {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            if (Files.exists(LOG_PATH)
                    && Files.readString(LOG_PATH).contains(text)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new RuntimeException("Timed out waiting for " + text
                                   + " in " + LOG_PATH);
    }

    private static void printMatchingLogLines(String text) throws IOException {
        for (String line : Files.readAllLines(LOG_PATH)) {
            if (line.contains(text)) {
                System.out.println(line);
            }
        }
    }

    private static void cleanup() throws Exception {
        printHeader(GREEN, "Cleanup");
        if (System.getenv("NONINTERACTIVE") != null) {
            return;
        }
        pause();
        printHeader(PURPLE, "Stop NSO and the netsim devices");
        run(true, "make", "stop");
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
