package com.tailf.packages.ned.netsimcli;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.security.MessageDigest;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.lang.reflect.Constructor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXPath;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiConfigFlag;
import com.tailf.maapi.MaapiException;
import com.tailf.maapi.MaapiSchemas;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCliBase;
import com.tailf.ned.NedCliBaseTemplate;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedErrorCode;
import com.tailf.ned.NedException;
import com.tailf.ned.NedExpectResult;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedShowFilter;
import com.tailf.ned.NedTTL;
import com.tailf.ned.NedTracer;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedWorker.TransactionIdMode;
import com.tailf.ned.SSHClient;
import com.tailf.ned.SSHSessionException;

/**
 * This class implements NED interface for Cli devices
 *
 */

public class NetsimCli extends NedCliBaseTemplate {
    public static Logger LOGGER  = LogManager.getLogger(NetsimCli.class);

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public  Maapi mm;
    private final static String privexec_prompt, prompt;
    private final static Pattern[] plw, ec, ec2, ec3, config_prompt;

    private boolean inConfig = false;
    NedCapability[] capabilities;

    static {
        // start of input, > 0 non-# and ' ', one #, >= 0 ' ', eol
        privexec_prompt = "\\A[^\\# ]+#[ ]?$";

        prompt = "\\A\\S*#";

        plw = new Pattern[] {
            Pattern.compile("Continue\\?\\[confirm\\]"),
            Pattern.compile("\\A.*\\(cfg\\)#"),
            Pattern.compile("\\A.*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("\\A\\S.*#"),
            Pattern.compile(prompt)
        };

        config_prompt = new Pattern[] {
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#")
        };

        ec = new Pattern[] {
            Pattern.compile("Do you want to kill that session and continue"),
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("Aborted.*\n"),
            Pattern.compile("Error.*\n"),
            Pattern.compile("syntax error.*\n"),
            Pattern.compile("error:.*\n")
        };

        ec2 = new Pattern[] {
            Pattern.compile("\\A.*\\(cfg\\)#"),
            Pattern.compile("\\A.*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("Aborted.*\n"),
            Pattern.compile("Error.*\n"),
            Pattern.compile("syntax error.*\n"),
            Pattern.compile("error:.*\n")
        };

        ec3 = new Pattern[] {
            Pattern.compile("\\A\\S*\\(config.*\\)#"),
            Pattern.compile("\\A\\S*#")
        };

    }

    public NetsimCli() {
        super();
    }

    private NetsimCli(String device_id,
                    InetAddress ip,
                    int port,
                    String proto,  // ssh or telnet
                    String ruser,
                    String pass,
                    String secpass,
                    boolean trace,
                    int connectTimeout, // msec
                    int readTimeout,    // msec
                    int writeTimeout,   // msec
                    NedMux mux,
                    NedWorker worker) {
        super(device_id, ip, port, proto, ruser, pass, secpass, trace,
                connectTimeout, readTimeout, writeTimeout, mux, worker);
    }

    private NetsimCli init(NedWorker worker) {
        try {
            int tid = worker.getToTransactionId();
            int usid = worker.getUsid();
            mm.setUserSession(usid);
            if (tid == 0) {
                tid = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
            } else {
                mm.attach(tid, 0, usid);
            }
            try {
                String path = "/ncs:devices/ncs:device{"+ device_id + "}";
                if (!mm.exists(tid, path)) {
                    worker.connectError(NedErrorCode.CONNECT_CONNECTION_REFUSED,
                                        "no device");
                    return this;
                }
            } finally {
                if (worker.getToTransactionId() == 0) {
                    mm.finishTrans(tid);
                } else {
                    mm.detach(tid);
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        try {
            try {
                setupSSH(worker);
            }
            catch (Exception e) {
                LOGGER.error("connect failed ",  e);
                worker.connectError(NedErrorCode.CONNECT_CONNECTION_REFUSED,
                                    e.getMessage());
                return this;
            }
        }
        catch (NedException e) {
            LOGGER.error("connect response failed ",  e);
            return this;
        }

        try {
            NedExpectResult res;

            res = session.expect(new String[] {
                    "\\A[Ll]ogin:",
                    "\\A[Uu]sername:",
                    "\\A[Pp]assword:",
                    "\\A\\S.*>",
                    privexec_prompt},
                worker);
            if (res.getHit() < 3)
                throw new NedException(NedErrorCode.CONNECT_BADAUTH,
                                       "Authentication failed");
            if (res.getHit() == 3) {
                session.print("enable\n");
                session.expect("enable", worker);
                res = session.expect(new String[] {"[Pp]assword:", prompt},
                                     worker);
                if (res.getHit() == 0) {
                    if (secpass == null || secpass.isEmpty())
                        throw new
                    NedException(NedErrorCode.CONNECT_BADAUTH,
                                 "Secondary password " +"not set");
                    session.print(secpass+"\n"); // enter password here
                    try {
                        res = session.expect(new String[] {"\\A\\S*>", prompt},
                                             worker);
                        if (res.getHit() == 0)
                            throw new NedException(NedErrorCode.CONNECT_BADAUTH,
                                                   "Secondary password "
                                                   +"authentication failed");
                    } catch (Exception e) {
                        throw new NedException(NedErrorCode.CONNECT_BADAUTH,
                                               "Secondary password "
                                               +"authentication failed");
                    }
                }
            }

            session.print("screen-length 0\n");
            session.expect("screen-length 0", worker);
            session.expect(prompt, worker);
            session.print("screen-width 0\n");
            session.expect("screen-width 0", worker);
            session.expect(prompt, worker);

            session.print("show confd-state version\n");
            session.expect("show confd-state version", worker);
            String netsimver = session.expect(prompt, worker);
            if (netsimver.indexOf("confd-state version") == -1) {
                throw new NedException(
                    NedErrorCode.NED_EXTERNAL_ERROR,
                    "This NED can only be used with NSO netsim devices");
            }

            this.capabilities = getCapabilities(worker);

            setConnectionData(this.capabilities,
                              this.capabilities,
                              false,
                              TransactionIdMode.UNIQUE_STRING);
        }
        catch (SSHSessionException e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
        catch (IOException e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
        catch (Exception e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
        return this;
    }

    private NetsimCli(String deviceId, NedMux mux) {
        this.device_id = deviceId;
        this.mux = mux;
    }

    private NetsimCli initNoConnect(NedWorker worker) {
        useStoredCapabilities();
        return this;
    }

    @Override
    public void setupSSH(NedWorker worker) throws Exception {
        trace(worker, "SSH connecting to host: "+
            ip.getHostAddress()+":"+port, "out");
        sshClient = SSHClient.createClient(worker, this);
        sshClient.connect(connectTimeout, 0);
        try {
            trace(worker, "SSH authenticating", "out");
            sshClient.authenticate();
        } catch (IOException e) {
            worker.connectError(NedErrorCode.CONNECT_BADAUTH, "Auth failed");
            return;
        }
        trace(worker, "SSH initializing session", "out");
        session = sshClient.createSession();

        if (trace) {
            session.setTracer(worker);
        }
    }

    private NedCapability[] getCapabilities(NedWorker worker)
        throws NedException, IOException, SSHSessionException {
        ArrayList<NedCapability> list = new ArrayList<NedCapability>();

        session.print("show confd-state loaded-data-models data-model namespace\n");
        session.expect("show confd-state loaded-data-models data-model namespace", worker);
        String output = session.expect(prompt, worker);

        String[] lines = output.split("[\r\n]+");
        for (String line : lines) {
            // Skip header and separator lines
            if (line.startsWith("NAME") || line.startsWith("---") || line.trim().isEmpty()) {
                continue;
            }
            // Split the line by whitespace and assume the last token is the namespace
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length > 1) {
                String namespace = tokens[tokens.length - 1];
                // Add namespace only if it doesn't start with "urn:ietf" or "http://tail-f.com"
                if ((namespace.indexOf("ned") >= 0 && namespace.startsWith("http://tail-f.com")) ||
                    (!namespace.startsWith("urn:ietf") && !namespace.startsWith("http://tail-f.com"))) {
                    list.add(new NedCapability(namespace,
                             "",
                             new ArrayList<String>(),
                             "",
                             new ArrayList<String>()));
                }
            }
        }
        NedCapability capa =
            new NedCapability(
   "http://tail-f.com/ns/ncs-ned/show-partial?path-format=cmd-path-full",
                    "", Collections.emptyList(), "", Collections.emptyList());
        NedCapability autoCfgAllowSyncFrom_capa = new NedCapability(
            "http://tail-f.com/ns/ncs-ned/show-auto-config", "",
            Collections.emptyList(), "", Collections.emptyList());

        list.add(capa);
        list.add(autoCfgAllowSyncFrom_capa);

        return list.toArray(new NedCapability[list.size()]);
    }

    public void trace(NedWorker worker, String msg, String direction) {
        if (trace) {
            worker.trace("-- "+msg+" --\n", direction, device_id);
        }
    }

    public void reconnect(NedWorker worker) {
    }

    public boolean keepAlive(NedWorker worker) {
        try {
            session.setTracer(null); // Let's not trace this
            session.print("\n");
            session.setTracer(worker);
            NedExpectResult res =
                session.expect(new String[] {"\\A\\S*>", prompt}, worker);
            if (res.getHit() != 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void moveToTopConfig() throws IOException, SSHSessionException {
        NedExpectResult res;

        while(true) {
            session.print("exit\n");
            res = session.expect(config_prompt);
            if (res.getHit() == 0)
                return;
        }
    }

    private boolean isCliError(String reply) {
        String[] errprompt = {
            "error",
            "aborted",
            "exceeded",
            "invalid",
            "incomplete",
            "duplicate name",
            "may not be configured",
            "should be in range",
            "is used by",
            "being used",
            "cannot be deleted",
            "bad mask",
            "is not supported",
            "is not permitted",
            "cannot negate",
            "does not exist. create first",
            "failed"
        };

        if (reply.indexOf("hqm_tablemap_inform: CLASS_REMOVE error") >= 0)
            // 'error' when "no table-map <name>", but entry is removed
            return false;

        int size = errprompt.length;
        for (int n = 0; n < size; n++) {
            if (reply.toLowerCase().indexOf(errprompt[n]) >= 0)
                return true;
        }
        return false;
    }

    private boolean print_line_wait(NedWorker worker, int cmd, String line,
                                    int retrying, boolean waitForEcho)
        throws NedException, IOException, SSHSessionException, ApplyException {
        NedExpectResult res = null;
        boolean isAtTop;
        String lines[];

        session.print(line+"\n");
        if (waitForEcho)
            session.expect(new String[] { Pattern.quote(line) }, worker);
        res = session.expect(plw, worker);
        if (res.getHit() == 0 || res.getHit() == 2) {
            isAtTop = true;
        } else if (res.getHit() == 1 || res.getHit() == 3) {
            isAtTop = false;
        } else {
            inConfig = false;
            throw new ApplyException(line, "exited from config mode",
                                        false, inConfig);
        }

        lines = res.getText().split("\n|\r");
        for (int i = 0 ; i < lines.length ; i++) {
            if (isCliError(lines[i])) {
                throw new ExtendedApplyException(line, lines[i], isAtTop, true);
            }
            if (lines[i].toLowerCase().indexOf("is in use") >= 0 ||
             lines[i].toLowerCase().indexOf("wait for it to complete") >= 0 ||
                lines[i].toLowerCase().indexOf("already exists") >= 0) {
                // wait a while and retry
                if (retrying > 60) {
                    // already tried enough, give up
                    throw new ExtendedApplyException(line, lines[i], isAtTop,
                                                     true);
                }
                else {
                    if (retrying == 0)
                        worker.setTimeout(10*60);
                    // sleep a second
                    try { Thread.sleep(1*1000);
                    } catch (InterruptedException e) {
                        System.err.println("sleep interrupted");
                    }
                    return print_line_wait(worker, cmd, line, retrying+1,
                                           waitForEcho);
                }
            }
        }

        return isAtTop;
    }

    private void print_line_wait_oper(NedWorker worker, int cmd,
                                      String line)
        throws NedException, IOException, SSHSessionException, ApplyException {
        NedExpectResult res = null;
        boolean isAtTop;

        session.print(line+"\n");
        session.expect(new String[] { Pattern.quote(line) }, worker);
        res = session.expect(new String[] {prompt}, worker);

        String lines[] = res.getText().split("\n|\r");
        for(int i=0 ; i < lines.length ; i++) {
            if (lines[i].toLowerCase().indexOf("error") >= 0 ||
                lines[i].toLowerCase().indexOf("failed") >= 0) {
                inConfig = false;
                throw new ExtendedApplyException(line, lines[i],
                                                 true, inConfig);
            }
        }
    }

    private boolean enterConfig(NedWorker worker, int cmd)
        throws NedException, IOException, SSHSessionException {
        NedExpectResult res = null;

        session.print("config t\n");
        res = session.expect(ec3, worker);
        if (res.getHit() > 0) {
            worker.error(cmd, NedCmd.cmdToString(cmd), res.getText());
            return false;
        }

        inConfig = true;

        return true;
    }

    private void exitConfig() throws IOException, SSHSessionException {
        NedExpectResult res;

        while(true) {
            session.print("exit\n");
            res = session.expect(new String[]
                {"\\A\\S*\\(config\\)#",
                 "\\A\\S*\\(cfg\\)#",
                 "\\A.*\\(.*\\)#",
                 "\\A\\S*\\(cfg.*\\)#",
                 prompt});
            if (res.getHit() == 4) {
                inConfig = false;
                return;
            } else if (res.getHit() == 5) {
               session.print("yes\n");
               session.expect(prompt);
               inConfig = false;
               return;
            }
        }
    }

    @Override
    public void applyConfig(NedWorker worker, int cmd, String data)
        throws NedException, IOException, SSHSessionException, ApplyException {
        if (trace) {
            session.setTracer(worker);
        }
        doApplyConfig(worker, cmd, data);
    }


    private void doApplyConfig(NedWorker worker, int cmd, String data)
        throws NedException, IOException, SSHSessionException, ApplyException {
        String[] lines = data.split("\n");
        try {
            enterConfig(worker, cmd);
            sendConfig(worker, cmd, lines);
            exitConfig();
        } catch (Exception e) {
            throw e;
        }
    }

    private void sendConfig(NedWorker worker, int cmd, String[] lines)
        throws NedException, IOException, SSHSessionException, ApplyException {
        String trimmed = "";
        try {
            String toptag = "";
            for (int n = 0 ; n < lines.length ; n++) {
                trimmed = lines[n].trim();
                trimmed = trimmed.replaceAll("!$", "exit");
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("!")) {
                    continue;
                }
                print_line_wait(worker, cmd, trimmed, 0, true);
            }
        } catch (ApplyException e) {
            throw e;
        }
    }

    protected boolean isTopExit(String line) {
        line = line.replace("\r", "");
        if ("exit".equals(line)) {
            return true;
        }
        return "!".equals(line);
    }

    @SuppressWarnings("serial")
    private class ExtendedApplyException extends ApplyException {
        public ExtendedApplyException(String line, String msg,
                                      boolean isAtTop,
                                      boolean inConfigMode) {
            super("command: "+line+": "+msg, isAtTop, inConfigMode);
            inConfig = inConfigMode;
         }
    }

    public void close(NedWorker worker)
        throws NedException, IOException {
        try {
            ResourceManager.unregisterResources(this);
        } catch (Exception ignore) {
        }
        super.close(worker);
    }

    public void close() {
        try {
            ResourceManager.unregisterResources(this);
        } catch (Exception ignore) {
        }
        super.close();
    }

    public void getTransId(NedWorker worker)
        throws Exception {
        if (trace)
            session.setTracer(worker);

        String res = getTransIdValue(worker);
        worker.getTransIdResponse(res);
    }

    private String getTransIdValue(NedWorker worker) throws Exception {
        String cmd = "show netconf-state datastores | display xpath | include transaction-id";
        if (inConfig) {
            session.print("do " + cmd + "\n");
            session.expect("do " + cmd, worker);
        }
        else {
            session.print(cmd + "\n");
            session.expect(cmd, worker);
        }

        String res = session.expect(privexec_prompt, worker);
        return res.substring(res.lastIndexOf(" ") + 1);
    }

    public void showPartial(NedWorker worker, ConfPath[] paths,
                            String[] cmdpaths)
        throws Exception {
        if (trace)
            session.setTracer(worker);
        ArrayList<String> resultList = new ArrayList<>(cmdpaths.length);
        String result = "";
        for (int i = 0; i < cmdpaths.length; i ++) {
            String showPath = cmdpaths[i].replace("\\ ", "");
            session.print("show running-config "+ showPath + "\n");
            session.expect("show running-config " + showPath);
            result = session.expect(privexec_prompt, worker);
            worker.setTimeout(readTimeout);
            if (result.contains("% No entries found.")) {
                resultList.add("");
            } else if (result.contains("syntax error")) {
                resultList.add("");
            } else {
                int offset = result.indexOf("--- WARNING");
                if (offset > 0) {
                    result = result.substring(0, offset);
                }
                if (result.charAt(result.length()-1) == '!') {
                    result = result.substring(0, result.length()-1) + "exit";
                }
                resultList.add(result);
            }
        }
        worker.showCliResponse(resultList);
    }

    public void show(NedWorker worker, String toptag)
        throws Exception {
        if (trace)
            session.setTracer(worker);

        // check context classloader
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL cliUrl = cl.getResource(
                "com/tailf/packages/ned/netsimcli/NetsimCli.class");
        if (cliUrl == null) {
            LOGGER.error("Cli URL: "+cliUrl);
            worker.error(NedCmd.SHOW_CLI, "wrong context classloader");
            return;
        } else {
            LOGGER.info("Class URL: "+cliUrl);
        }

        session.print("show running-config "+ toptag + "\n");
        session.expect("show running-config " + toptag);
        String res = session.expect(privexec_prompt, worker);

        worker.setTimeout(readTimeout);
        if (res.contains("% No entries found.")) {
            worker.showCliResponse("");
        } else {
            res = res.replaceAll("!$", "exit");
            worker.showCliResponse(res);
        }
    }

    public boolean isConnection(String device_id,
                                InetAddress ip,
                                int port,
                                String proto,  // ssh or telnet
                                String ruser,
                                String pass,
                                String secpass,
                                String keydir,
                                boolean trace,
                                int connectTimeout, // msec
                                int readTimeout,
                                int writeTimeout) {
        return ((this.device_id.equals(device_id)) &&
                (this.ip.equals(ip)) &&
                (this.port == port) &&
                (this.proto.equals(proto)) &&
                (this.ruser.equals(ruser)) &&
                (this.pass.equals(pass)) &&
                (this.secpass.equals(secpass)) &&
                (this.trace == trace) &&
                (this.connectTimeout == connectTimeout) &&
                (this.readTimeout == readTimeout) &&
                (this.writeTimeout == writeTimeout));
    }

    @Override
    public void showStatsPath(NedWorker worker, int th, ConfPath path)
        throws Exception {
        showStatsConfPath(worker, th, new ConfPath[]{removeLiveStatus(path)});
        worker.showStatsPathResponse(new NedTTL[] {
            new NedTTL(path, 10)
        });
    }

    @Override
    public void showStatsFilter(NedWorker worker, int th, ConfPath[] paths)
        throws Exception {
        showStatsConfPath(worker, th, paths);
        worker.showStatsFilterResponse();
    }

    @Override
    public void showStatsFilter(NedWorker worker, int th, String[] xpaths)
        throws Exception {
        List<ConfPath> paths = new ArrayList<ConfPath>();
        for (String xpath : xpaths) {
            try {
                xpath = "/ncs:devices/ncs:device[name = '" + device_id + "']" +
                    "/ncs:live-status" + xpath;
                ConfPath path = new ConfPath((new ConfXPath(xpath)).getKP());
                paths.add(removeLiveStatus(path));
            } catch (Exception e) {
                // Ignored
            }
        }
        showStatsConfPath(
            worker, th, paths.toArray(new ConfPath[paths.size()]));
        worker.showStatsFilterResponse();
    }

    protected ConfPath removeLiveStatus(ConfPath path) throws Exception{
        ConfTag liveStatus = new ConfTag(new Ncs(), Ncs._live_status);
        List<ConfObject> kp = new ArrayList<ConfObject>();
        for (ConfObject o: path.getKP()) {
            if (liveStatus.equals(o)) {
                break;
            } else {
                kp.add(o);
            }
        }
        return new ConfPath(kp.toArray(new ConfObject[kp.size()]));
    }

    protected void showStatsConfPath(NedWorker worker, int th, ConfPath[] paths)
            throws Exception {
        String startCmd = inConfig ? "do show" : "show";

        List<String> cmds = new ArrayList<String>();
        for (ConfPath path : paths) {
            String str = "";
            for (ConfObject pathelem : path.getKP()) {
                if (pathelem instanceof ConfKey) {
                    str = pathelem.toString().replaceAll(
                            "(\\A\\{)|(\\}\\z)", "") + " " + str;
                } else {
                    str = ((ConfTag)pathelem).getTag() + " " + str;
                }
            }

            cmds.add(startCmd + " " + str + "| display xml");
        }

        if (cmds.isEmpty()) {
            cmds.add(startCmd + " sys | display xml");
        }

        mm.attach(th, 0);
        NavuNode liveStatus = new NavuContainer(mm, th, Ncs.hash)
            .container(Ncs._devices)
            .list(Ncs._device)
            .elem(device_id)
            .container(Ncs._live_status);
        for (String cmd : cmds) {
            session.println(cmd);
            session.expect(cmd.replaceAll("\\|", "\\\\|"));

            NedExpectResult res = session.expect(new String[]{
                "\\Asyntax error: .*",
                "\\ANo entries found\\.",
                privexec_prompt
            }, worker);

            if (res.getHit() == 2) {
                String data = res.getText()
                    .replaceFirst("\\A\\s*" +
                        "<config xmlns=\"http://tail-f.com/ns/config/1.0\">", "")
                    .replaceAll("</config>\\Z", "");

                liveStatus.setValues(data);
            }
        }
        mm.detach(th);
    }

    @Override
    public void showStatsFilter(
            NedWorker worker, int th, NedShowFilter[] filters)
        throws Exception {
        final String startCmd = inConfig ? "do show" : "show";

        List<String> cmds = new ArrayList<String>();
        ConfTag liveStatusTag = new ConfTag(new Ncs(), Ncs._live_status);
        if (filters == null || filters.length == 0) {
            cmds.add(startCmd + " sys");
        } else {
            cmds(Arrays.asList(filters), startCmd, cmds);
        }

        mm.attach(th, 0);
        NavuNode liveStatus = new NavuContainer(mm, th, Ncs.hash)
                                .container(Ncs._devices)
                                .list(Ncs._device)
                                .elem(device_id)
                                .container(Ncs._live_status);

        for (String cmd : cmds) {
            cmd = cmd + " | display xml";
            session.println(cmd);
            session.expect(cmd.replaceAll("\\|", "\\\\|"));

            NedExpectResult res = session.expect(new String[]{
                "\\Asyntax error: .*",
                "\\ANo entries found\\.",
                privexec_prompt
            }, worker);

            if (res.getHit() == 2) {
                String data = res.getText()
                    .replaceFirst("\\A\\s*" +
                        "<config xmlns=\"http://tail-f.com/ns/config/1.0\">",
                        "")
                    .replaceAll("</config>\\Z", "");

                liveStatus.setValues(data);
            }
        }
        mm.detach(th);
        worker.showStatsFilterResponse();
    }

    public void cmds(
        List<NedShowFilter> filters, String cmd, List<String> cmds) {
            for (NedShowFilter filter : filters) {
                switch (filter.getType()) {
                    case SELECTION:
                        cmds.add(cmd + " " + filter.getTag().getTag());
                        break;
                    case CONTENT_MATCH:
                        cmds.add(cmd + " " + filter.getData());
                        break;
                    case CONTAINMENT:
                        cmds(filter.getChildren(), cmd + " " + filter.getTag().getTag(), cmds);
                        break;
                }
            }
    }

    @Override
    public void command(NedWorker worker, String cmd, ConfXMLParam[] params)
        throws Exception {
        worker.error(NedCmd.CMD, "not implemented");
    }

    @Override
    public NedCliBase newConnection(String device_id,
                                    InetAddress ip,
                                    int port,
                                    String proto,  // ssh or telnet
                                    String ruser,
                                    String pass,
                                    String secpass,
                                    String publicKeyDir,
                                    boolean trace,
                                    int connectTimeout, // msec
                                    int readTimeout,    // msec
                                    int writeTimeout,   // msecs
                                    NedMux mux,
                                    NedWorker worker) {
        LOGGER.debug("device " + device_id +
                " => connectTimeout " + connectTimeout +
                " readTimeout " + readTimeout +
                " writeTimeout " + writeTimeout);
        try {
            NetsimCli ned = new NetsimCli(device_id, ip, port, proto, ruser, pass,
                    secpass, trace, connectTimeout, readTimeout, writeTimeout, mux,
                    worker);
            ResourceManager.registerResources(ned);
            return ned.init(worker);
        } catch (Exception e) {
            LOGGER.error("Failed starting new connection", e);
            return null;
        }
    }

    @Override
    public NedCliBase initNoConnect(String device_id,
                                    NedMux mux,
                                    NedWorker worker)
        throws NedWorker.NotEnoughDataException {
        try {
            NetsimCli ned = new NetsimCli(device_id, mux);
            ResourceManager.registerResources(ned);
            return ned.initNoConnect(worker);
        } catch (Exception e) {
            LOGGER.error("Failed initializing NED", e);
            return null;
        }
    }

    private static Map<String, String> Values =
        new ConcurrentHashMap<String, String>();
    private static Map<String, String> abortValues =
        new ConcurrentHashMap<String, String>();

    public void commit(NedWorker worker, int timeout) throws Exception {
        session.setTracer(worker);
        saveConfig(worker, NedCmd.COMMIT);
        worker.commitResponse();
    }

    @Override
    public void abort(NedWorker worker, String data) throws Exception {
        if (trace) {
            session.setTracer(worker);
        }
        doApplyConfig(worker, NedCmd.ABORT_CLI , data);
        worker.abortResponse();
    }

    @Override
    public void revert(NedWorker worker, String data) throws Exception {
        if (trace) {
            session.setTracer(worker);
        }
        doApplyConfig(worker, NedCmd.REVERT_CLI, data);
        saveConfig(worker, NedCmd.REVERT_CLI);
        worker.revertResponse();
    }

    @Override
    public void persist(NedWorker worker) throws Exception {
        if (trace) {
            session.setTracer(worker);
        }
        saveConfig(worker, NedCmd.PERSIST);
        worker.persistResponse();
    }

    private void saveConfig(NedWorker worker, int cmd)
        throws NedException, IOException, SSHSessionException, ApplyException {
        // Save running-config to startup-config
        print_line_wait_oper(worker, cmd, "copy running-config startup-config");
    }
}
