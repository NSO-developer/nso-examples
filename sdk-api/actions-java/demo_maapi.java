import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.example.actions.namespaces.actionTest;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;

public class demo_maapi {
    private static final String RED = "\033[0;31m";
    private static final String GREEN = "\033[0;32m";
    private static final String PURPLE = "\033[0;35m";
    private static final String NC = "\033[0m";
    private static final ConfNamespace ACTION_NS = new actionTest();

    public static void main(String[] args) throws Exception {
        resetExample();
        startExample();
        runActions();
        showLog();
        cleanup();
        printHeader(GREEN, "Done!");
    }

    private static void resetExample() throws Exception {
        printHeader(GREEN, "Java MAAPI action demo");
        printHeader(PURPLE, "Reset", false);
        run(false, "make", "stop");
        run(true, "make", "clean");
    }

    private static void startExample() throws Exception {
        printHeader(GREEN, "Running the Example");
        printHeader(PURPLE, "Build the package and start NSO", false);
        run(true, "make", "all", "start");
    }

    private static void runActions() throws Exception {
        try (Maapi maapi = new Maapi(getAddress())) {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});

            printHeader(PURPLE, "Run the 'reboot' action");
            maapi.requestAction(new ConfXMLParam[0],
                                "/a:action-test/system/reboot");

            printHeader(PURPLE, "Run the 'restart' action");
            printActionOutput(maapi.requestAction(restartInput(),
                                "/a:action-test/system/restart"));

            printHeader(PURPLE,
                        "Run the 'verify' action - will finish after "
                        + "10 seconds");
            printActionOutput(maapi.requestAction(new ConfXMLParam[0],
                                "/a:action-test/system/verify"));

            printHeader(PURPLE, "Run the 'reset' action");
            runResetAction(maapi);

            printHeader(PURPLE,
                        "Call the 'reboot' action");
            maapi.requestAction(new ConfXMLParam[0],
                                "/a:action-test/system/reboot");

            maapi.endUserSession();
        }
    }

    private static ConfXMLParam[] restartInput() {
        return new ConfXMLParam[] {
            new ConfXMLParamValue(ACTION_NS, "mode", new ConfBuf("xx"))
        };
    }

    private static void runResetAction(Maapi maapi)
            throws IOException, ConfException {
        int th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ_WRITE);
        try {
            maapi.safeCreate(th, "/a:action-test/server{test}");
            String when = LocalTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm:ss"));
            ConfXMLParam[] input = new ConfXMLParam[] {
                new ConfXMLParamValue(ACTION_NS, "when", new ConfBuf(when))
            };
            printActionOutput(maapi.requestActionTh(th, input,
                                "/a:action-test/server{test}/reset"));
        } finally {
            maapi.finishTrans(th);
        }
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

    private static void showLog() throws Exception {
        printHeader(PURPLE, "View the log output in ncs-java-vm.log");
        ProcessBuilder pb = new ProcessBuilder("cat", "logs/ncs-java-vm.log");
        pb.inheritIO();
        int exitCode = pb.start().waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("cat logs/ncs-java-vm.log failed");
        }
    }

    private static void cleanup() throws Exception {
        if (System.getenv("NONINTERACTIVE") != null) {
            return;
        }
        printHeader(GREEN, "Cleanup", false);
        pause();
        printHeader(PURPLE, "Stop NSO and clean all created files", false);
        run(true, "make", "stop", "clean");
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
