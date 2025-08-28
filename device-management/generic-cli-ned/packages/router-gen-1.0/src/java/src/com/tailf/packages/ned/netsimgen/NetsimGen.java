package com.tailf.packages.ned.netsimgen;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import ch.ethz.ssh2.InteractiveCallback;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBinary;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfList;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ConfXPath;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiException;
import com.tailf.maapi.MaapiSchemas;
import com.tailf.maapi.MountIdCb;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuNode;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ned.CliSession;
import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedEditOp;
import com.tailf.ned.NedException;
import com.tailf.ned.NedExpectResult;
import com.tailf.ned.NedGenericBase;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedShowFilter;
import com.tailf.ned.NedTTL;
import com.tailf.ned.NedTracer;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedErrorCode;
import com.tailf.ned.NedWorker.TransactionIdMode;
import com.tailf.ned.SSHClient;
import com.tailf.ned.SSHSession;
import com.tailf.ned.SSHSessionException;
import com.tailf.packages.ned.netsimgen.namespaces.*;

/**
 * This class implements NED interface
 *
 */
public class NetsimGen extends NedGenericBase {

    public static Logger LOGGER  = LogManager.getLogger(NetsimGen.class);

    private StringBuilder transIdString;

    private String date_string = "2025-03-25";
    private String version_string = "1.0.0.0";

    private static class Tokenizer {
        private int index;
        private String data;

        public Tokenizer(String s) {
            index = 0;
            data = s;
        }

        public boolean endOfData() {
            return endOfData(true);
        }

        public boolean endOfData(boolean lineWrap) {
            scanWhiteSpace(lineWrap);
            if (index >= data.length()) {
                return true;
            }
            if (lineWrap) {
                return false;
            }
            // If we are at the end of a line there is no more data
            char c = data.charAt(index);
            return (c == ';' || c == '\n' || c == '\r');
        }

        public int index() {
            return index;
        }

        public void index(int newIndex) {
            if (newIndex < 0) {
                newIndex = 0;
            } else if (newIndex > data.length()) {
                newIndex = data.length();
            }
            index = newIndex;
        }

        public void scanWhiteSpace(boolean lineWrap) {
            char c;

            for (; index < data.length(); ++index) {
                c = data.charAt(index);
                if (c == ' ' || c == '\t') {
                    continue;
                }
                if (lineWrap && (c == ';' || c == '\n' || c == '\r')) {
                    continue;
                }
                break;
            }
        }

        public String next() {
            return next(true);
        }

        public String next(boolean lineWrap) {
            char c;
            int startIndex = index;
            int endIndex = data.length();

            scanWhiteSpace(lineWrap);

            if (endOfData(lineWrap)) {
                return "";
            }

            c = data.charAt(index);
            if (c == '"') {
                // String token
                ++index;
                startIndex = index;

                for (; index < data.length(); ++index) {
                    c = data.charAt(index);
                    if (c == '\\') {
                        // See if we have a quoted " aka \"
                        ++index;
                        if (index < data.length()) {
                            c = data.charAt(index);
                            if (c == '"') {
                                continue;
                            }
                        }
                    } else if (c == '"') {
                        endIndex = index;
                        ++index;
                        break; // Out to the outer loop
                    }
                }
                String result = data.substring(startIndex, endIndex);
                return result.replaceAll("\\\\\"", "\\\"");

            } else {
                // Normal token
                startIndex = index;
                for (; index < data.length(); ++index) {
                    c = data.charAt(index);
                    if (c == ';' ||
                        c == ' ' ||
                        c == '\t' ||
                        c == '\n' ||
                        c == '\r') {
                        endIndex = index;
                        break;
                    }
                }
            }
            return data.substring(startIndex, endIndex);
        }
    }

    private static class Path {
        private Maapi maapi;
        private Stack<String> path;
        private String root;
        private String prefix;
        private MaapiSchemas.CSNode rootNode;
        private MaapiSchemas.CSNode node;
        private ConfNamespace ns;
        private MaapiSchemas schemas;

        public Path(Maapi maapi, String moduleRoot, ConfNamespace ns) {
            path = new Stack<String>();
            node = null;
            rootNode = null;
            this.maapi = maapi;
            this.ns = ns;

            root = moduleRoot;
            prefix = ns.prefix();

            schemas = Maapi.getSchemas();

            try {
                rootNode = schemas.findCSNode(Ncs.uri, root);
                node = rootNode;
            }
            catch (MaapiException e) {
                log.error("Cannot find YANG model node for: '"+
                          root+"'");
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(root);
            boolean isFirst = true;

            for (String s: path) {
                if (isFirst) {
                    sb.append("/"+prefix+":");
                    isFirst = false;
                } else {
                    sb.append("/");
                }
                sb.append(s);
            }
            return sb.toString();
        }

        public void push(ConfPath p) throws NedException, ConfException {
            ConfObject[] kp = p.getKP();
            Collections.reverse(Arrays.asList(kp));
            for (ConfObject o: kp) {
                if (o instanceof ConfTag) {
                    push(((ConfTag) o).getTag());
                } else if (o instanceof ConfKey) {
                    addKey((ConfKey) o);
                }
            }
        }

        public void push(String tag) throws NedException {
            List<String> mountId = MaapiSchemas.getThreadDefaultMountId();
            MaapiSchemas.CSMNsMap mnsMap = schemas.findCSMNsMap(mountId);
            MaapiSchemas.CSNode child = schemas.findCSNode(node, mnsMap, tag);

            if (child == null) {
                StringBuilder sb = new StringBuilder();
                sb.append(this.toString());
                sb.append(" has no child with tag: '");
                sb.append(tag);
                sb.append("'");
                throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                       sb.toString());
            }

            path.push(tag);
            node = child;
        }

        public String pop() {
            String elem = path.pop();
            node = node.getParentNode();
            return elem;
        }

        public String peek() {
            return path.peek();
        }

        /*
        ** Add a key to the top element. The key is a space separated
        ** string of keys.
        */
        public void addKey(ConfKey key) {
            String top = path.pop();
            path.push(top + key.toString());
        }

        public void addKey(String key) {
            String top = path.pop();
            path.push(top+"{"+key+"}");
        }

        public boolean isContainer() {
            return node.isContainer();
        }

        public boolean isPresenceContainer() {
            return node.isContainer() && node.getMinOccurs() == 0;
        }

        public boolean isList() {
            return node.isList();
        }

        public boolean isLeaf() {
            return node.isLeaf();
        }

        public boolean isEmptyLeaf() {
            return node.isEmptyLeaf();
        }

        public boolean isLeafList() {
            return node.isLeafList();
        }

        public boolean isRoot() {
            return node == rootNode;
        }

        public ConfObject getDefval() {
            return node.getNodeInfo().getDefval();
        }

        public void clear() {
            path.clear();
            node = rootNode;
        }

        public Path copy() throws NedException {
            Path copy = new Path(this.maapi, root, ns);
            for (String s: path) {
                if (s.contains("{")) {
                    String[] parts = s.split(Pattern.quote("{"));
                    copy.push(parts[0]);
                    copy.addKey(parts[1].replace("}", ""));
                } else {
                    copy.push(s);
                }
            }
            return copy;
        }
     }

    private static int instance = 0;

    // Instance data and methods
    //
    private int         thisInstance = 0;
    private String      device_id;
    private String      ncs_prefix;
    MaapiSchemas.CSNode netsimgenCs;

    private SSHClient sshClient;
    private CliSession session;

    private InetAddress ip;
    private int         port;
    private String      luser;
    private boolean     trace;
    private NedTracer   tracer;
    private int         connectTimeout; // msec
    private int         readTimeout;    // msec
    private int         writeTimeout;   // msec
    NedCapability[]     capabilities;
    private String      ruser;
    private String      rpassword;

    private MaapiSchemas schemas;

    private static Logger log =
        LogManager.getLogger(NetsimGen.class);

    // Connects to the NcsServer and sets mm
    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public  Maapi mm;

    private boolean inConfig = false;
    private String prompt = "\\(netsim\\)% ";
    private String operPrompt = "\\(netsim\\)> ";

    private synchronized void incrInstance(){
        this.thisInstance = ++NetsimGen.instance;
    }

    // empty default contsructor for ObjectFactory
    public NetsimGen() { }

    private class keyboardInteractive implements InteractiveCallback {
        private String pass;
        public keyboardInteractive(String password) {
            this.pass = password;
        }
        public String[] replyToChallenge(String name, String instruction,
                                         int numPrompts, String[] prompt,
                                         boolean[] echo) throws Exception {
            log.trace("ssh name: "+name+
                     ", instruction: "+instruction+
                     ", numPrompts: "+numPrompts+
                     ", prompt: "+Arrays.asList(prompt)+
                     ", echo: "+Arrays.asList(echo));
            if (numPrompts == 0)
                return new String[] {};
            if (numPrompts != 1) {
                throw new Exception("giving up");
            }
            String[] passwords = new String[] { pass };
            log.trace("passwords: "+Arrays.asList(passwords));
            return passwords;
        }
    }

    private NetsimGen init(String device_id,
                      NedMux mux,
                      NedWorker worker) {
        this.device_id = device_id;
        this.ncs_prefix = "/ncs:devices/device{"+device_id+"}/config";

        try {
            int usid = worker.getUsid();
            mm.setUserSession(usid);
            int tid = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
            mm.finishTrans(tid);
        } catch (Exception e) {
        }

        incrInstance();

        log.trace("{#: "+thisInstance+"} <--");

        this.schemas = Maapi.getSchemas();

        try {
            this.netsimgenCs = getCSNode(ncs_prefix);
        } catch (Exception e) {
            log.error("Cannot get root YANG node: "+ncs_prefix, e);
        }

        this.device_id = device_id;
        useStoredCapabilities();
        return this;
    }

    private NetsimGen init(String aDevice_id,
                          InetAddress anIp,
                          int aPort,
                          String aLuser,
                          boolean aTrace,
                          int aConnectTimeout,
                          int aReadTimeout,
                          int aWriteTimeout,
                          NedMux aMux,
                          NedWorker aWorker) throws NedGenericException{
        this.device_id = aDevice_id;
        this.ncs_prefix = "/ncs:devices/device{"+device_id+"}/config";
        this.ip = anIp;
        this.port = aPort;
        this.luser = aLuser;
        this.trace = aTrace;
        this.connectTimeout = aConnectTimeout;
        this.readTimeout = aReadTimeout;
        this.writeTimeout = aWriteTimeout;
        this.ruser = aWorker.getRemoteUser();
        this.rpassword = aWorker.getPassword();

        aWorker.trace("NED VERSION: netsim-gen "+version_string+" "+date_string,
                       "out",aDevice_id);

        if (trace)
            tracer = aWorker;
        else
            tracer = null;

        incrInstance();

        log.trace("{#: "+thisInstance+"} <--");

        this.schemas = Maapi.getSchemas();

        try {
            this.netsimgenCs = getCSNode(ncs_prefix);
        } catch (Exception e) {
            log.error("Cannot get root YANG node: "+ncs_prefix, e);
        }

        try {
            int tid = aWorker.getToTransactionId();
            int usid = aWorker.getUsid();
            mm.setUserSession(usid);
            if (tid == 0) {
                tid = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
            } else {
                mm.attach(tid, 0, usid);
            }
            try {
                String path = "/ncs:devices/ncs:device{"+ aDevice_id + "}";
                if (!mm.exists(tid, path)) {
                    aWorker.connectError(NedErrorCode.CONNECT_CONNECTION_REFUSED,
                                         "no device");
                    return this;
                }
            } finally {
                if (aWorker.getToTransactionId() == 0) {
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
                sshClient = SSHClient.createClient(aWorker, this);
                sshClient.connect(connectTimeout, 0);
                try {
                    String[] methods = {SSHClient.AUTH_PASSWORD,
                        SSHClient.AUTH_KEYBOARD_INTERACTIVE};
                    sshClient.authenticate(methods);
                } catch (IOException e) {
                    aWorker.connectError(
                            NedErrorCode.CONNECT_BADAUTH, "Auth failed");
                    return this;
                }
                log.info(sshClient.getConnectionInfo());
            }
            catch (Exception e) {
                log.error("connect failed ",  e);
                aWorker.connectError(
                        NedErrorCode.CONNECT_CONNECTION_REFUSED,
                        e.getMessage());
                return this;
            }

            if (device_id.startsWith("_fail_connect")) {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) { }
                aWorker.connectError(
                        NedErrorCode.CONNECT_TIMEOUT, "_fail_connect_");
            }
        } catch (NedException e) {
            log.error("connect response failed ",  e);
            return this;
        }

        try {
            session = session = sshClient.createSession();
            ((SSHClient.CliSession) session).setReadTimeout(aReadTimeout);

            String str;

            if (trace) {
                session.setTracer(aWorker);
            }

            // Wait for the first prompt to appear
            String firstPrompt = ruser+"@\\S*>";
            log.trace("1: Wait for the first prompt: '"+
                      firstPrompt+"' to appear");
            str = session.expect(
                    firstPrompt, true, false, readTimeout, aWorker);
            log.trace("2: recieving "+str);

            // Set a minimalistic prompt so we know what we match for.
            // Operational prompt will end with '> '
            // Configuration prompt will end with '% '
            session.println("set prompt1 \"(netsim)> \"");

            // First occurence is the command itself
            str = session.expect(operPrompt, aWorker);
            log.trace("3: recieving "+str);
            // Now the actual prompt
            str = session.expect(operPrompt, aWorker);
            log.trace("3.1: recieving "+str);

            session.println("set prompt2 \"(netsim)% \"");
            str = session.expect(operPrompt, aWorker);
            log.trace("4: recieving "+str);

            // We do not want any paging
            session.println("set paginate false");
            str = session.expect(operPrompt, aWorker);
            log.trace("5: recieving "+str);

            // We do not complete on space
            session.println("set complete-on-space false");
            str = session.expect(operPrompt, aWorker);
            log.trace("6: recieving "+str);

            // Ignore leading space
            session.println("set ignore-leading-space true");
            str = session.expect(operPrompt, aWorker);
            log.trace("7: recieving "+str);

            this.capabilities = getCapabilities(aWorker);

            setConnectionData(this.capabilities,
                              this.capabilities,
                              false,
                              TransactionIdMode.UNIQUE_STRING);
            log.trace("{NetsimGen() #: "+thisInstance+"} --> OK");
        } catch (SSHSessionException e) {
            aWorker.error(
                    NedCmd.CONNECT_GENERIC, e.getMessage(), "expect error");
        } catch (IOException e) {
            aWorker.error(NedCmd.CONNECT_GENERIC, e.getMessage(), "IO error");
        } catch (Exception e) {
            aWorker.error(NedCmd.CONNECT_GENERIC, e.getMessage(), "IO error");
        }

        return this;
    }

    private NedCapability[] getCapabilities(NedWorker worker)
        throws NedException {
        ArrayList<NedCapability> list = new ArrayList<NedCapability>();

        String output;
        output =
            executeCommand("show netconf-state capabilities capability",
                           operPrompt, false);

        String[] lines = output.split("[\r\n]+");
        String prefix = "netconf-state capabilities capability ";

        for (String line: lines) {
            if (line.indexOf(prefix) == 0) {
                NedCapability capa;
                String uri="";
                String module="";
                String revision="";
                List<String> features=new ArrayList<String>();

                String l = line.substring(prefix.length(), line.length());
                String fields[] = l.split("\\?");
                int index = 0;
                for (String field: fields) {
                    if (index == 0) {
                        uri = field;
                        log.trace("URI: "+uri);
                    } else {
                        String pars[] = l.split("&");
                        String tmp;

                        for (String par: pars) {
                            if (par.indexOf("module=") == 0) {
                                tmp = par.substring(7, par.length());
                                module = tmp;
                                log.trace("module: "+module);
                            } else if (par.indexOf("revision=") == 0) {
                                tmp = par.substring(9, par.length());
                                revision = tmp;
                                log.trace("revision: "+revision);
                            } else if (par.indexOf("features=") == 0) {
                                tmp = par.substring(9, par.length());
                                features = Arrays.asList(tmp.split("[, ]+"));
                                log.trace("features: "+tmp);
                            }
                        }
                    }
                    ++index;
                }
            }
        }

        NedCapability capa = new NedCapability(
            "http://tail-f.com/ns/ncs-ned/show-partial?path-format=key-path",
            "", Collections.emptyList(), "", Collections.emptyList());
        list.add(capa);

        NedCapability autoConfigureAllowSyncFromCapa =
            new NedCapability(
                    "http://tail-f.com/ns/ncs-ned/show-auto-config", "",
                    Collections.emptyList(), "", Collections.emptyList());
        list.add(autoConfigureAllowSyncFromCapa);

        return list.toArray(new NedCapability[list.size()]);
    }

    private void enterConfig()
        throws NedException {
        executeCommand("configure exclusive no-confirm",
                       this.prompt, true);
        inConfig = true;
        return;
    }

    private void exitConfig() {
        NedExpectResult res;
        try {
            while(true) {
                session.print("exit\n");
                res = session.expect(new String[]
                    {operPrompt,
                     prompt,
                     "You are exiting after a 'commit confirm'"});
                if (res.getHit() == 0) {
                    inConfig = false;
                    return;
                } else if (res.getHit() == 2) {
                    session.print("yes\n");
                    session.expect(operPrompt);
                    inConfig = false;
                    return;
                }
            }
        } catch (Exception ignore) {
            log.error("Error when closing connection");
        }
    }

    public String device_id() {
        return device_id;
    }

    // should return "cli" or "generic"
    public String type() {
        return "generic";
    }

    // Which Yang modules are covered by the class
    public String [] modules() {
        log.trace("modules");
        return new String[] { "r" };
    }

    // Which identity is implemented by the class
    public String identity() {
        return "id:router-gen-1.0";
    }

    /**
     * Invoked by NSO to take the configuration to a new state.
     *
     * @param w - worker used for sending responses to NSO.
     * @param data commands for moving the configuration to a new state.
     */
    public void prepare(NedWorker worker, NedEditOp[] ops)
        throws NedException, IOException {
        if (trace)
            session.setTracer(worker);

        log.trace("{#: "+thisInstance+"}.prepare() <--");

        try {
            enterConfig();
            revertNoConfirm();
            // All edit ops should succeed in the prepare phase
            edit(worker, ops, null, false);

            commitConfirmed(worker);

            worker.prepareResponse();
            log.trace("{#: "+thisInstance+"}.prepare() --> OK");

        } catch (Exception e) {
            revertNoConfirm();
            log.error("Error in prepare", e);
            worker.error(NedCmd.PREPARE_GENERIC, e.getMessage(), "edit error");
        }
    }

    /**
     * Invoked by NSO to ask the NED what actions it would take towards the
     * device if it would do a prepare.
     *
     * The NED can send the preformatted output back to NSO through
     * {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     * prepareDryResponse()}
     *
     * The NED should invoke the method
     * {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     * prepareDryResponse()} in <code>NedWorker w</code> when the operation
     * is completed.
     *
     * If the functionality is not supported or an error is detected
     * answer this through a call to
     * {@link com.tailf.ned.NedWorker#error(int,String,String) error()}
     * in <code>NedWorker w</code>.
     *
     * @param w - The NedWorker instance currently responsible for driving the
     * communication between NSO and the device. This NedWorker instance is
     * used when communicating with the NSO, i.e., for sending responses,
     * errors, and trace messages. It is also implements the {@link NedTracer}
     * API and can be used in, for example, the {@link SSHSession} as a tracer.
     *
     * @param ops - Edit operations representing the changes to the
     * configuration.
     */
    public void prepareDry(NedWorker worker, NedEditOp[] ops)
        throws NedException {

        StringBuilder result = new StringBuilder();

        log.trace("{#: "+thisInstance+"}.prepareDry() <--");

        try {
            edit(worker, ops, result, false);
            worker.prepareDryResponse(result.toString());
            log.trace("{#: "+thisInstance+"}.prepareDry() --> OK");
        }catch (Exception e) {
            log.error("Error in prepare", e);
            worker.error(NedCmd.PREPARE_GENERIC, e.getMessage(), "edit error");
        }
    }

    /**
     * Invoked by NSO to commit the configuration.
     */
    public void commit(NedWorker worker, int timeout) throws Exception {
        if (trace) {
            session.setTracer(worker);
        }
        log.trace("{#: "+thisInstance+"}.commit() <--");
        try {
            worker.commitResponse();
            log.trace("{#: "+thisInstance+"}.commit() --> OK");

        } catch(Exception e){
            log.error("{#: "+thisInstance+"}, "+
                     "Could not commit towards the device: ",e);
            worker.error(NedCmd.COMMIT, e.getMessage(), "Commit Error");
        }
    }

    /**
     * Invoked by NSO to abort the configuration to a previous state.
     *
     * @param w - the processing worker. It should be used for sending
     * responses to NSO.
     * @param data - the commands for taking the config back to the previous
     * state.
     */
    public void abort(NedWorker worker, NedEditOp[] ops0)
        throws NedException, IOException {
        if (trace)
            session.setTracer(worker);

        log.trace("{#: "+thisInstance+"}.abort() <--");
        try {
            if (!inConfig) {
                enterConfig();
            }
            revertNoConfirm();
            abortChanges();

            worker.abortResponse();
            log.trace("{#: "+thisInstance+"}.abort() --> OK");
        }
        catch (Exception e) {
            log.error("",e);
            log.trace("{#: "+thisInstance+"}, "+
                     "Could not abort towards the device");
            revertNoConfirm();
            exitConfig();
            worker.error(NedCmd.PREPARE_GENERIC, e.getMessage(), "edit error");
        }
    }

    /**
     * Invoked by NSO to revert the configuration.
     */
    public void revert(NedWorker worker , NedEditOp[] ops0)
        throws NedException, IOException {
        if (trace)
            session.setTracer(worker);

        log.trace("{#: "+thisInstance+"}.revert() <--");
        try {
            if (!inConfig) {
                enterConfig();
            }
            revertNoConfirm();
            abortChanges();

            worker.revertResponse();
            log.trace("{#: "+thisInstance+"}.revert() --> OK");
        }catch(Exception e){
            log.trace("{#: "+thisInstance+"}, "+
                     "Could not revert towards the device");
            revertNoConfirm();
            exitConfig();
            worker.error(NedCmd.REVERT_GENERIC, e.getMessage(), "Revert Error");
        }
    }

    private int readUntilWouldBlock(SSHClient.CliSession session) {
        int ret = 0;
        int c;
        while (true)
        try {
            if (!(session.ready()))
            return ret;
            c = session.getReader().read();
            if (c == -1)
            return ret;
            ret++;
        } catch (IOException e) {
            return ret;
        }
    }

    /*
     * Execute command cmd, return when stop is found
     * NOTE: stop matches the buffer for every recieved character
     * There is thus no need to add the $ character as this is implicit.
     */
    private String executeCommand(String cmd,
                                  String stop_regex,
                                  boolean checkOutput)
        throws NedException{
        Pattern error = Pattern.compile("^\\[error\\]");
        Pattern edit = Pattern.compile("^\\[edit\\]");
        Pattern ok = Pattern.compile("^\\[ok\\]");
        Pattern pointer = Pattern.compile("^[-]+\\^");

        String output;
        String result;
        Pattern stop = Pattern.compile(stop_regex);
        boolean foundError = false;

        log.trace("{#: "+thisInstance+"}.executeCommand() <-- "
                  +cmd+", checkOutput: "+checkOutput);

        if (session instanceof SSHClient.CliSession) {
            readUntilWouldBlock((SSHClient.CliSession) session);
        } else {
            ((SSHSession) session).readUntilWouldBlock();
        }

        try {
            session.println(cmd);
            output = session.expect(stop_regex, true, readTimeout);
        }
        catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append(e.getMessage());
            sb.append(", waiting on result after: '");
            sb.append(cmd);
            log.error(sb.toString());
            throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR,
                                   sb.toString());
        }

        if (checkOutput) {
            // We can have one or more lines now
            String[] lines = output.split("[\r\n]+");
            StringBuilder sb = new StringBuilder();

            for (String line: lines) {
                if (line.equals(cmd)) {
                    continue;
                }
                Matcher m1 = stop.matcher(line);
                if (m1.find()) {
                    continue;
                }
                Matcher m2 = pointer.matcher(line);
                if (m2.find()) {
                    continue;
                }
                Matcher m3 = error.matcher(line);
                if (m3.find()) {
                    foundError = true;
                    continue;
                }
                Matcher m4 = edit.matcher(line);
                if (m4.find()) {
                    continue;
                }
                Matcher m5 = ok.matcher(line);
                if (m5.find()) {
                    continue;
                }
                sb.append(line);
            }
            result = sb.toString();

        } else {
            // Start index
            int s = output.indexOf(cmd);
            if (s >= 0) {
                s += cmd.length();
            } else {
                s = 0;
            }

            // Scan over any white space in the beginning
            char c;
            for (; s < output.length(); ++s) {
                c = output.charAt(s);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    break;
                }
            }

            Matcher m = stop.matcher(output);
            int e = output.length();
            if (m.find()) {
                e = m.start();
            }
            result = output.substring(s, e);
        }

        if (foundError) {
            StringBuilder sb = new StringBuilder();
            sb.append("when executing '");
            sb.append(cmd);
            sb.append("': ");
            sb.append(result);
            log.trace(sb.toString());
            throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR,
                                   sb.toString());
        }

        log.trace("{#: "+
            thisInstance+"}.executeCommand() --> '"+ result+"'");
      return result;

    }

    private void commitConfirmed(NedWorker worker) throws NedException {
        String res = "";

        log.trace("{#: "+thisInstance+"}.commitConfirmed() <--");

        try {
            String commitCmd = "commit confirmed 1";
            if (worker.getLabel() != null) {
                commitCmd = commitCmd + " label \"" + worker.getLabel() + "\"";
            }
            if (worker.getComment() != null) {
                commitCmd = commitCmd + " comment \"" +
                    worker.getComment() + "\"";
            }
            res = executeCommand(commitCmd, this.prompt, true);
        }
        catch (Exception e) {
            throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR,
                                   e.getMessage());
        }
        String okReply =
                "Warning: The configuration will be reverted if "+
                "you exit the CLI";
        if (res.indexOf(okReply) >= 0) {
            // Success
        } else if (res.indexOf("No modifications to commit") >= 0) {
            // Success
        } else {
            throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR, res);
        }

        log.trace("{#: "+thisInstance+"}.commitConfirmed() --> OK");
    }

    private void commitChanges() throws NedException {
        String res = "";

        log.trace("{#: "+thisInstance+"}.commitChanges() <--");

        try {
            res = executeCommand("commit", this.prompt, true);
        }
        catch (Exception e) {
            throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR,
                                   e.getMessage());
        }
        if (res.length() > 0) {
            if (res.indexOf("Commit complete") >= 0) {
                // Success
            } else if (res.indexOf("No modifications to commit") >= 0) {
                // Success
            }else {
                throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR, res);
            }
        }

        log.trace("{#: "+thisInstance+"}.commitChanges() --> OK");
    }

    private void abortChanges() throws NedException {
        String res = "";

        log.trace("{#: "+thisInstance+"}.abortChanges() <--");

        try {
            res = executeCommand("commit abort", this.prompt, false);
        }
        catch (Exception e) {
            throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR,
                                   e.getMessage());
        }
        if (res.length() > 0) {
            if (res.indexOf("Confirmed commit has been aborted") >= 0) {
                // Success
            } else if (res.indexOf("no confirmed commit in progress") >= 0) {
                // Success
            } else {
                throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR, res);
            }
        }
        exitConfig();
        log.trace("{#: "+thisInstance+"}.abortChanges() --> OK");
    }

    // The NED is in configure mode
    // revert whatever changes has been made
    private void revertNoConfirm() throws NedException {
        log.trace("{#: "+thisInstance+"}.revertNoConfirm() <--");

        executeCommand("revert no-confirm", prompt, true);

        log.trace("{#: "+thisInstance+"}.revertNoConfirm() --> OK");
    }

    private void edit(NedWorker worker, NedEditOp[] ops,
                      StringBuilder dryRun, boolean ignoreErrors)
        throws NedException, Exception {
        long lastTime = System.currentTimeMillis();
        long time;
        List<NedEditOp> opersList = new ArrayList<NedEditOp>();

        for (int i = 0; i < ops.length; ++i) {
            NedEditOp op = ops[i];
            if (op.getOpDone()) {
                continue;
            }
            opersList.add(ops[i]);
        }
        NedEditOp[] opers = opersList.toArray(new NedEditOp[opersList.size()]);

        for (int i = 0; i < opers.length; ++i) {
            time = System.currentTimeMillis();
            if (dryRun == null && ((time - lastTime) > (0.8 * readTimeout))) {
                lastTime = time;
                worker.setTimeout(readTimeout);
            }
            NedEditOp op = opers[i];
            if (op.getOpDone()) {
                continue;
            }

            switch (op.getOperation()) {
            case NedEditOp.CREATED:
                create(opers, i, dryRun, ignoreErrors);
                break;
            case NedEditOp.DELETED:
                delete(opers, i, dryRun, ignoreErrors);
                break;
            case NedEditOp.MOVED:
                move(opers, i, dryRun, ignoreErrors);
                break;
            case NedEditOp.VALUE_SET:
                valueSet(opers, i, dryRun, ignoreErrors);
                break;
            case NedEditOp.DEFAULT_SET:
                defaultSet(opers, i, dryRun, ignoreErrors);
                break;
            }
        }
    }


    private String quoteString(String s) {
        StringBuilder res = new StringBuilder();

        res.append("\"");

        StringBuilder sb = new StringBuilder();
        char c;

        for (int i = 0; i < s.length(); ++i) {
            c = s.charAt(i);
            switch (c) {
            case '\b':
                sb.append("\\b");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\");
                break;
            default:
                sb.append(c);
            }
        }
        res.append(sb.toString());
        res.append("\"");

        return res.toString();
    }

    /*
     * Return the value as a quoted string, this handles data in the direction
     * NSO -> device
     */
    private String valueToString(MaapiSchemas.CSNode cs, ConfValue value) {
        StringBuilder res = new StringBuilder();

        res.append("\"");
        String s = schemas.valueToString(cs.getNodeInfo().getType(), value);
        if (value instanceof ConfBuf ||
            value instanceof ConfBinary) {
            StringBuilder sb = new StringBuilder();
            char c;

            for (int i = 0; i < s.length(); ++i) {
                c = s.charAt(i);
                switch (c) {
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\");
                    break;
                default:
                    sb.append(c);
                }
            }
            s = sb.toString();
        }

        res.append(s);
        res.append("\"");

        return res.toString();
    }

    private String keyToString(MaapiSchemas.CSNode cs,  ConfKey key) {
        boolean isFirst = true;
        MaapiSchemas.CSNode csKey;
        int keyIndex = 0;
        String keys = "";

        for (ConfObject ko: key.elements()) {
            csKey = cs.getKey(keyIndex);
            ++keyIndex;

            if (isFirst) {
                isFirst = false;
            } else {
                keys = keys + " ";
            }
            keys = keys + schemas.valueToString
                (csKey.getNodeInfo().getType(),
                 (ConfValue) ko);
        }
        return keys;
    }

    private String buildCommandPath(ConfPath path)
        throws NedException, ConfException {
        StringBuilder cmdLine = new StringBuilder();
        ConfObject[] kp = path.getKP();
        int kpIndex = kp.length;

        MaapiSchemas.CSNode cs = netsimgenCs;

        for (--kpIndex; kpIndex >= 0; --kpIndex) {
            ConfObject o = kp[kpIndex];
            if (o instanceof ConfTag) {
                ConfTag t = (ConfTag) o;
                MaapiSchemas.CSNode child = getChild(cs, t);
                cmdLine.append(" ");
                cmdLine.append(child.getTag());
                cs = child;
            } else if (o instanceof ConfKey) {
                cmdLine.append(" ");
                cmdLine.append(keyToString(cs, (ConfKey) o));
                cmdLine.append(" ");
            }
        }
        return cmdLine.toString();
    }

    private MaapiSchemas.CSNode
    buildCommandPath(NedEditOp[] ops,
                     int opsIndex,
                     StringBuilder cmdLine)
        throws NedException {
        ConfObject[] kp = getKP(ops[opsIndex]);
        String path = ops[opsIndex].getPath().toString();
        NedEditOp op = ops[opsIndex];
        MaapiSchemas.CSNode resultingCs;
        int kpIndex = kp.length;

        MaapiSchemas.CSNode cs = netsimgenCs;

        for (--kpIndex; kpIndex >= 0; --kpIndex) {
            ConfObject o = kp[kpIndex];
            if (o instanceof ConfTag) {
                ConfTag t = (ConfTag) o;
                MaapiSchemas.CSNode child = getChild(cs, t);

                cmdLine.append(" ");
                cmdLine.append(child.getTag());
                cs = child;

            } else if (o instanceof ConfKey) {
                cmdLine.append(" ");
                cmdLine.append(keyToString(cs, (ConfKey) o));
                cmdLine.append(" ");
            }
        }
        resultingCs = cs;

        printMetaData(path);

        if (op.getOperation() == NedEditOp.CREATED ||
            op.getOperation() == NedEditOp.VALUE_SET ||
            op.getOperation() == NedEditOp.DEFAULT_SET) {

            if (cs.isEmptyLeaf()) {
                op.setOpDone();
                kp = getParentKP(kp);
                cs = cs.getParentNode();

            } else if (cs.isLeaf() || cs.isLeafList()) {
                cmdLine.append(" ");
                addValueToCommand(cmdLine, cs, op);
                op.setOpDone();
                kp = getParentKP(kp);
                cs = cs.getParentNode();

            } else if (cs.isContainer() || cs.isList()) {
                op.setOpDone();
            }

        } else if (op.getOperation() == NedEditOp.DELETED) {
            op.setOpDone();
        } else if (op.getOperation() == NedEditOp.MOVED) {
            switch (op.getMoveDestination()) {
            case NedEditOp.FIRST:
                cmdLine.append("first");
                if (op.getValue() != null) {
                    throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                            "Value not null for move=first");
                }
                break;
            case NedEditOp.AFTER:
                cmdLine.append("after");
                addValueToCommand(cmdLine, cs, op);
                break;
            }
            op.setOpDone();
        } else {
            throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                   "Internal error, unknown op("+
                                   op.getOperation()+") at:"+cmdLine);
        }

        return resultingCs;
    }

    private void printMetaData(String path) {
        try {
            ConfPath devPath = new ConfPath(this.mm, -1,
                                            "%s%s", ncs_prefix, path);
            MaapiSchemas.CSNode node =
                schemas.findCSNode("http://tail-f.com/ns/ncs",
                                   devPath.toString());
            if (node.hasMetaData()) {
                for (Map.Entry<String, String> ent :
                         node.getNodeInfo().getMetaData().entrySet()) {
                    log.trace("META DATA: " + ent.getKey() + " = "
                              + ent.getValue());
                }
            }
        } catch (Exception e) {
        }
    }

    private ConfObject[] getKP(NedEditOp op) throws NedException {
        ConfPath cp = op.getPath();
        ConfObject[] kp;

        try {
            kp = cp.getKP();
        } catch (Exception e) {
            throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                   "Internal error, cannot get key path: "
                                   +e.getMessage());
        }
        return kp;
    }

    private ConfObject[] getParentKP( ConfObject[] kp) throws NedException {
        ConfObject[] parent = new  ConfObject[kp.length-1];

        for (int i = 0; i < parent.length; ++i) {
            parent[i] = kp[i+1];
        }

        return parent;
    }

    private MaapiSchemas.CSNode getChild(MaapiSchemas.CSNode cs, ConfTag t)
        throws NedException {
        MaapiSchemas.CSNode child;

        child = cs.getChild(t.getTagHash());
        if (child != null) {
            return child;
        }
        throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                               "Cannot find child: '"+t.getTag()+
                               "' to YANG node: "+cs);
    }

    /**
     * Add value to commandline
     * cmd - the command line
     * cs - points at the YANG node, this has to be a leaf or leaf-list
     * op - the operation to perform, has to be VALUE_SET or DEFAULT_SET
     */
    private void addValueToCommand(StringBuilder cmdLine,
                                   MaapiSchemas.CSNode cs,
                                   NedEditOp op)
        throws NedException {
        log.info("schema: " + cs.toString());
        ConfObject o = null;

        if (op.getOperation() == NedEditOp.VALUE_SET
                || op.getOperation() == NedEditOp.MOVED) {
            o = op.getValue();
        } else if (op.getOperation() == NedEditOp.DEFAULT_SET) {
            o = cs.getDefval();
        }

        if (o == null) {
            throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                   "Internal error, no value supplied at: "+
                                   cmdLine);
        }

        // Now we need to format the value according to the YANG model
        if (op.getOperation() == NedEditOp.MOVED) {
            // For move operation the object must be a ConfKey
            ConfKey k = (ConfKey)o;
            cmdLine.append(" ");
            cmdLine.append(keyToString(cs, (ConfKey) o));
        }
        else if (cs.isLeafList()) {
            if (!(o instanceof ConfList)) {
                throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                       "Internal error, value is not list at: "
                                       +cmdLine);
            }

            cmdLine.append("[ ");

            ConfList l = (ConfList) o;
            boolean isFirst = true;
            for (ConfObject e: l.elements()) {
                String value = valueToString(cs, (ConfValue) e);
                if (isFirst) {
                    isFirst = false;
                } else {
                    cmdLine.append(" ");
                }
                cmdLine.append(value);
            }
            cmdLine.append(" ]");

        } else if (cs.isLeaf()) {
            cmdLine.append(valueToString(cs, (ConfValue) o));

        } else {
            throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                   "Internal error, not a leaf or leaf-list: "
                                   +cmdLine);
        }
    }

    private void create(NedEditOp[] ops, int index,
                        StringBuilder dryRun, boolean ignoreErrors)
        throws NedException  {

        StringBuilder cmdLine = new StringBuilder("set");

        // Build the the command with it's paths
        buildCommandPath(ops, index, cmdLine);

        if (dryRun == null) {
            boolean checkOutput = !ignoreErrors;
            executeCommand(cmdLine.toString(), prompt, checkOutput);
        } else {
            dryRun.append(cmdLine.toString());
            dryRun.append('\n');
        }
    }

    private void move(NedEditOp[] ops, int index,
                      StringBuilder dryRun, boolean ignoreErrors)
        throws NedException  {

        StringBuilder cmdLine = new StringBuilder("move");

        // Build the the command with it's paths
        buildCommandPath(ops, index, cmdLine);

        if (dryRun == null) {
            boolean checkOutput = !ignoreErrors;
            executeCommand(cmdLine.toString(), prompt, checkOutput);
        } else {
            dryRun.append(cmdLine.toString());
            dryRun.append('\n');
        }
    }

    private void valueSet(NedEditOp[] ops, int index,
                          StringBuilder dryRun, boolean ignoreErrors)
        throws NedException  {

        StringBuilder cmdLine = new StringBuilder("set");

        // Build the the command with it's paths
        buildCommandPath(ops, index, cmdLine);

        if (dryRun == null) {
            boolean checkOutput = !ignoreErrors;
            executeCommand(cmdLine.toString(), prompt, checkOutput);
        } else {
            dryRun.append(cmdLine.toString());
            dryRun.append('\n');
        }
    }

    private void defaultSet(NedEditOp[] ops, int index,
                            StringBuilder dryRun, boolean ignoreErrors)
        throws NedException {

        StringBuilder cmdLine = new StringBuilder("set");

        // Build the the command with it's paths
        buildCommandPath(ops, index, cmdLine);

        if (dryRun == null) {
            boolean checkOutput = !ignoreErrors;
            executeCommand(cmdLine.toString(), prompt, checkOutput);
        } else {
            dryRun.append(cmdLine.toString());
            dryRun.append('\n');
        }
    }

    private void delete(NedEditOp[] ops, int index,
                        StringBuilder dryRun, boolean ignoreErrors)
        throws NedException  {

        StringBuilder cmdLine = new StringBuilder("delete");

        // Build the the command with it's paths
        buildCommandPath(ops, index, cmdLine);

        if (dryRun == null) {
            boolean checkOutput = !ignoreErrors;
            executeCommand(cmdLine.toString(), prompt, checkOutput);
        } else {
            dryRun.append(cmdLine.toString());
            dryRun.append('\n');
        }
    }

    public void persist(NedWorker worker)
        throws NedException, IOException {
        if (trace) {
            session.setTracer(worker);
        }
        log.trace("{#: "+thisInstance+"}.persists() <--");
        try {
            String res = "";
            res = executeCommand("commit", this.prompt, true);
            exitConfig();
            worker.persistResponse();
            log.trace("{#: "+thisInstance+"}.persist --> OK");
        } catch(Exception e){
            log.error("{#: "+thisInstance+"}, "+
                     "Could not persist towards the device: ",e);
            worker.error(NedCmd.PERSIST, e.getMessage(), "Persist Error");
        }
    }

    public void close(NedWorker worker) {
        try {
            ResourceManager.unregisterResources(this);
        } catch (Exception ignore) {
        }
        try {
            exitConfig();
            if (session != null) {
                session.close();
            }
            if (sshClient != null) {
                sshClient.close();
            }
            log.trace("(" + this + ").close(" + worker.getName() + ") --> OK");
        }  catch (Exception ignore) {
        }
    }

    public void close() {
        log.trace("{#: "+thisInstance+"}.close() -->");
        try {
            ResourceManager.unregisterResources(this);
        } catch (Exception ignore) {
        }
        try {
            exitConfig();
            if (session != null) {
                session.close();
            }
            if (sshClient != null) {
                sshClient.close();
            }
            log.trace("{#: "+thisInstance+"}.close() --> OK");
        } catch (Exception ignore) {
        }
    }

    private  MaapiSchemas.CSNode getCSNode(String path)
        throws MaapiException {

        // log.trace("++++ getCSNode: uri: "+Ncs.uri+", path: "+path);
        return schemas.findCSNode(Ncs.uri, path);
    }

    private void createElement(int th, Path path)
        throws ConfException, IOException {

        // log.trace("++++ Safe Creating: "+path);
        mm.safeCreate(th, path.toString());
    }

    private String unQuote(String s) {
        StringBuilder sb = new StringBuilder();
        char c;

        for (int i = 0; i < s.length(); ++i) {
            c = s.charAt(i);
            if (c != '\\' || i == s.length()-1) {
                // Not a quoted character or
                // last character which means nothing to unquote
                sb.append(c);
            } else {
                ++i;
                c = s.charAt(i);
                switch (c) {
                case 'b':
                    sb.append('\b');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                default:
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private void setElement(int th, Path path, String v)
    throws ConfException, IOException {
        // log.trace("++++ Setting leaf: "+path+" to: "+v);
        try {
            mm.setElem(th, unQuote(v), path.toString());
        }
        catch (ConfException e) {
            log.error(e.getMessage());
        }
    }

    private void setLeafList(int th, Path path, ArrayList<String> leafs)
    throws ConfException, IOException {

        if (leafs.isEmpty()) {
            return;
        }
        if (leafs.size() == 1) {
            if (leafs.get(0).equals("none")) {
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean isFirst =true;

        for (String leaf: leafs) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(" ");
            }
            sb.append(leaf);
        }
        // log.trace("++++ Setting leaf list: "+path+" to: "+sb.toString());
        mm.setElem(th, sb.toString() , path.toString());
    }

    public void showPartial(NedWorker worker, int th, ConfPath[] paths)
        throws NedException, IOException {
        String config, c;

        if (trace)
            session.setTracer(worker);

        log.trace("{#: "+thisInstance+"}.show_partial(" + worker +
                  "," + th + ") -->");

        // Attach to maapi transaction
        try {
            log.trace("{#: "+thisInstance+"} Attaching to Maapi " +
                     mm.getSocket() + " -->");
            mm.attach(th, -1, 1);
            log.trace("{#: "+thisInstance+"} Attaching to Maapi " +
                     mm.getSocket() + " --> OK");

        } catch (Exception e)  {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_PARTIAL_GENERIC, e.getMessage(),
                         "IO error");
            return;
        }
        try {
            for (ConfPath p: paths) {
                ConfObject[] kp = p.getKP();
                MaapiSchemas.CSNode cs = netsimgenCs;
                ConfTag t = (ConfTag)kp[kp.length - 1];
                MaapiSchemas.CSNode child = getChild(cs, t);
                ConfNamespace ns = ConfNamespace.findNamespace(child.getNS());
                Path path = new Path(this.mm, ncs_prefix, ns);
                path.push(p);
                try {
                    c = buildCommandPath(p);
                    config = executeCommand("show configuration " + c, operPrompt, false);
                    if (path.isPresenceContainer() && config.startsWith("No entries found")) {
                        /* Running the show command on an empty presence container will
                           return 'No entries found' regardless if the container exists
                           or not. It's possible to determine the existence of an empty
                           presence container by trying to delete it.
                        */
                        enterConfig();
                        config = executeCommand("delete " + c, prompt, false);
                        revertNoConfirm();
                        exitConfig();
                    }
                } catch (NedException ne) {
                    // This is a result of changes to the cli
                    // Or this module is not supported
                    // Log this and continue
                    log.error("Result from show_partial: "+ne.getMessage());
                    continue;
                }
                try {
                    log.info("NetsimGen TEST START: "+config+
                             " : NetsimGen TEST STOP ");
                    parseConfig(p, config, worker, th, false, false, path);
                } catch (NedException ne) {
                    // This is a result of changes to the cli
                    // Often this is due to change of data format
                    // or added configurables
                    // Log this and continue
                    log.error(ne.getMessage());
                    continue;
                }
            }
        } catch (Exception e) {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_PARTIAL_GENERIC, e.getMessage(),
                         "Parse error");
            return;
        }
        try {
            mm.detach(th);
        } catch (Exception e)  {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_PARTIAL_GENERIC, e.getMessage(),
                         "IO error");
            return;
        }

        worker.showGenericResponse();
        log.trace("{#: "+thisInstance+"}.show_partial() --> OK");
    }

    public void showOffline(NedWorker worker, int th, String data)
        throws NedException, IOException {
        if (trace)
            session.setTracer(worker);

        log.trace("{#: "+thisInstance+"}.show_offline(" + worker +
                  "," + th + ") -->");

        // Attach to maapi transaction
        try {
            log.trace("{#: "+thisInstance+"} Attaching to Maapi " +
                      mm.getSocket() + " -->");
            mm.attach(th, -1, 1);
            log.trace("{#: "+thisInstance+"} Attaching to Maapi " +
                      mm.getSocket() + " --> OK");
        } catch (Exception e)  {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_OFFLINE_GENERIC, e.getMessage(),
                         "IO error");
            return;
        }

        String c = "r:sys";
        ConfNamespace ns = new router();
        try {
            parseConfig(new ConfPath(this.mm, th, "/" + c),
                        data, worker, th, false, true, ns);
        }
        catch (Exception e) {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_OFFLINE_GENERIC, e.getMessage(),
                         "Parse error");
            return;
        }
        try {
            mm.detach(th);
        }
        catch (Exception e)  {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_OFFLINE_GENERIC, e.getMessage(),
                         "IO error");
            return;
        }

        worker.setAdditionalInfo("test info");
        worker.showGenericResponse();
        log.trace("{#: "+thisInstance+"}.show_offline() --> OK");
    }

    /**
     * The generic show command is to
     * grab all configuration from the device and
     * populate the th passed to us.
     *
     **/
    public void show(NedWorker worker, int th)
        throws NedException, IOException {
        String config;

        if (trace)
            session.setTracer(worker);

        // check context classloader
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL genUrl = cl.getResource(
                "com/tailf/packages/ned/netsimgen/NetsimGen.class");
        if (genUrl == null) {
            log.error("Gen URL: "+genUrl);
            worker.error(NedCmd.SHOW_GENERIC, "wrong context classloader");
            return;
        } else {
            log.info("Class URL: "+genUrl);
        }

        log.trace("{#: "+thisInstance+"}.show(" + worker + "," + th + ") -->");

        // Attach to maapi transaction
        try {
            log.trace("{#: "+thisInstance+"} Attaching to Maapi " +
                     mm.getSocket() + " -->");
            mm.attach(th, -1, 1);
            log.trace("{#: "+thisInstance+"} Attaching to Maapi " +
                     mm.getSocket() + " --> OK");

        } catch (Exception e)  {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_GENERIC, e.getMessage(), "IO error");
            return;
        }
        try {
            MaapiSchemas.CSNode parent = netsimgenCs;
            List<MaapiSchemas.CSNode> children = parent.getChildren();
            List<String> mountId = MaapiSchemas.getThreadDefaultMountId();
            for (MaapiSchemas.CSNode n : children) {
                if (hasIntersection(n.getNodeInfo().getMountId(), mountId)) {
                    String tag = n.getTag();
                    ConfNamespace ns = ConfNamespace.findNamespace(n.getNS());
                    String prefix = ns.prefix();
                    String c = prefix + ":" + tag;
                    try {
                        config = executeCommand("show configuration " + c,
                                                operPrompt, false);
                    } catch (NedException e) {
                        // This is a result of changes to the cli
                        // Or this module is not supported
                        // Log this and continue
                        log.error("Result from show: " + e.getMessage());
                        continue;
                    }
                    try {
                        log.info("NetsimGen TEST START: "+config+
                                 " : NetsimGen TEST STOP ");
                        parseConfig(new ConfPath(this.mm, th, "/" + c),
                                    config, worker, th, false, false, ns);
                    } catch (NedException ne) {
                        // This is a result of changes to the cli
                        // Often this is due to change of data format
                        // or added configurables
                        // Log this and continue
                        log.error(ne.getMessage());
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_GENERIC, e.getMessage(), "Parse error");
            return;
        }
        try {
            mm.detach(th);
        }
        catch (Exception e)  {
            log.error("Error:" , e);
            worker.error(NedCmd.SHOW_GENERIC, e.getMessage(), "IO error");
            return;
        }
        worker.setAdditionalInfo("test info");
        worker.showGenericResponse();
        log.trace("{#: "+thisInstance+"}.show() --> OK");
    }

    private boolean hasIntersection(List<String> list1, List<String> list2) {
        for (String n : list1) {
            if (list2.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleNamespaceMappings(Tokenizer t, Path path,
                                      ArrayList<String> leafs)
    throws NedException {

        int tokenIndex = t.index();
        String token = t.next();
        if (!token.equals("{")) {
            t.index(tokenIndex);
            return false;
        }

        while (!t.endOfData()) {
            token = t.next();
            if (token.equals("}")) {
                break;
            }
            if (token.equals("{")) {
                String mappingNamespace = "";
                String mappingprefix = "";

                while (!t.endOfData()) {
                    tokenIndex = t.index();
                    token = t.next();
                    if (token.equals("mapping-namespace")) {
                        mappingNamespace = t.next();
                    } else if (token.equals("mapping-prefix")) {
                        mappingprefix = t.next();
                    } else if (token.equals("}")) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("xmlns:");
                        sb.append(mappingprefix);
                        sb.append('=');
                        sb.append(mappingNamespace);
                        leafs.add(sb.toString());
                        break;
                    } else {
                        // Something we do not recognize
                        t.index(tokenIndex);
                        break;
                    }
                }
            } else {
                // Don't know how to parse
                throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR,
                                       "Unrecognized token: '"+token+
                                       "' when handling path: "+path);
            }
        }

        return true;
    }

    private void ensurePath(int th, Path path)
        throws NedException, ConfException, IOException {
        Path pathCopy = path.copy();
        ArrayList<Path> copies = new ArrayList<Path>();
        String elem;
        while (!pathCopy.isRoot()) {
            if (pathCopy.isList()) {
                elem = pathCopy.peek();
                if (elem.contains("{")) {
                    copies.add(pathCopy.copy());
                }
            } else if (pathCopy.isPresenceContainer()) {
                copies.add(pathCopy.copy());
            }
            pathCopy.pop();
        }
        Collections.reverse(copies);
        for (Path copy: copies) {
            createElement(th, copy);
        }
    }

    private void parseConfig(ConfPath p, String config,
                             NedWorker worker, int th,
                             boolean transId, boolean offline,
                             ConfNamespace ns)
        throws ConfException, IOException, NedException {
        Path path = new Path(this.mm, ncs_prefix, ns);
        path.push(p);
        parseConfig(p, config, worker, th,
                    transId, offline, path);
    }

    private void parseConfig(ConfPath p, String config,
                             NedWorker worker, int th,
                             boolean transId, boolean offline,
                             Path path)
        throws ConfException, IOException, NedException {

        if ((config.startsWith("No entries found") &&
             !(p.getKP()[0] instanceof ConfKey))
            || config.contains("syntax error: element does not exist")
            || config.contains("syntax error: unknown argument")
            || config.contains("Error: element not found")) {
            return;
        }

        // Use Path to make it easy to push and pop
        int levels = 0; // Number of nested { levels
        Tokenizer t = new Tokenizer(config);
        long lastTime = System.currentTimeMillis();
        long time;

        ensurePath(th, path);

        while(!t.endOfData()) {

            time = System.currentTimeMillis();
            if (!offline && ((time - lastTime) > (0.8 * readTimeout))) {
                lastTime = time;
                worker.setTimeout(readTimeout);
            }

            String token = t.next();

            // log.trace("path: '"+path+"'");
            //            log.trace("token: '"+token+"'");

            if (token.startsWith("[ok]")) {
                while(!t.endOfData()) {
                    token = t.next();
                }
                continue;
            }

            if (token.equals("{")) {
                ++levels;
                // log.trace("levels: "+levels);
                continue;
            }

            if (token.equals("}")) {
                --levels;

                // log.trace("levels: "+levels+", length(): "+config.length());

                path.pop();
                continue;

            } else if (!path.isLeaf() && !path.isLeafList() &&
                       !(path.peek().equals(token))) {
                 path.push(token);
            }

            if (path.isEmptyLeaf()) {
                if (transId) {
                    transIdString.append("set "+path+"\n");
                }
                else {
                    createElement(th, path);
                }

                path.pop();

            } else if (path.isLeaf()) {
                // Next token should be a value, on the same line
                token = t.next(false);
                // log.trace("value token: '"+token+"'");

                if (token.length() == 0) {
                    log.error("Missing value for element: "
                              +path);
                    worker.error(NedCmd.SHOW_GENERIC,
                                 "Missing value for element: "+
                                 path,
                                 "confd output does not match YANG "+
                                 "model");
                    return;
                }

                if (transId) {
                    transIdString.append("set "+path+token+"\n");
                } else {
                    setElement(th, path, token);
                }
                path.pop();

            } else if (path.isLeafList()) {
                ArrayList<String> leafs = new ArrayList<String>();
                if (handleNamespaceMappings(t, path, leafs)) {
                    // All is taken care of
                } else {
                    // Collect all leafs up to the end of line
                    boolean lwrp = false;

                    if (!t.endOfData(lwrp)) {
                        int tokenIndex = t.index();
                        token = t.next(lwrp);
                        if (token.equals("[")) {
                            // We wrap around lines
                            lwrp = true;
                        } else {
                            t.index(tokenIndex);
                        }
                    }
                    while (!t.endOfData(lwrp)) {
                        token = t.next(lwrp);
                        if (token.length() == 0 || token.equals("]")) {
                            break;
                        }

                        if (!leafs.contains(token)) {
                            leafs.add(token);
                        }
                    }
                }

                // log.trace("leaf list: '"+leafs+"'");

                if (transId) {
                    transIdString.append("set "+path+leafs+"\n");
                } else {
                    setLeafList(th, path, leafs);
                }

                path.pop();

            } else if (path.isList()) {
                // A list
                // Collect all the keys up to {
                ArrayList<String> keys = new ArrayList<String>();
                int lastIndex = t.index();

                while (!t.endOfData(false)) {
                    lastIndex = t.index();
                    token = t.next(false);

                    // log.trace("key: '"+token+"'");

                    if (token.equals("{")) {
                        // Back one step among the tokens so we
                        // will find the { in next run
                        t.index(lastIndex);
                        break;
                    } else if (token.equals("}")) {
                        --levels;
                        break;
                    }
                    keys.add(token);
                }
                if (keys.size() == 0) {
                    // Empty list
                    path.pop();

                } else if (keys.size() > 0) {
                    StringBuilder sb = new StringBuilder("");

                    boolean isFirst = true;
                    for (String key: keys) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            sb.append(" ");
                        }
                        sb.append(key);
                    }
                    path.addKey(sb.toString());

                    if (transId) {
                        transIdString.append("set "+path+"\n");
                    } else {
                        createElement(th, path);
                    }
                    if (t.endOfData(false)) {
                        // List with no children, nothinh more to come on
                        // this path
                        path.pop();
                    }
                }

            } else if (path.isContainer()) {
                // Is config and presence
                if (path.isPresenceContainer()) {
                    if (transId) {
                        transIdString.append("set "+path+"\n");
                    } else {
                        createElement(th, path);
                    }
                }
            } else {

                throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                       "Unknown type of element: "+path);
            }
        }
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
        final String startCmd = inConfig ? "do show" : "show";

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
               operPrompt
            }, worker);

            if (res.getHit() == 2) {
                String data = res.getText()
                    .replaceFirst("\\A\\s*" +
                        "<config xmlns=\"http://tail-f.com/ns/config/1.0\">",
                        "")
                    .replaceAll("(?m)</config>[^<>]*\\Z", "");

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
                operPrompt
            }, worker);

            if (res.getHit() == 2) {
                String data = res.getText()
                    .replaceFirst("\\A\\s*" +
                        "<config xmlns=\"http://tail-f.com/ns/config/1.0\">",
                        "")
                    .replaceAll("(?m)</config>[^<>]*\\Z", "");

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
    public boolean isAlive(NedWorker worker) {
        log.trace("{#: "+thisInstance+"}.isAlive() <--");
        boolean ret;
        if (session == null) {
            ret = false;
        } else {
            ret = (session.serverSideClosed() == false);
        }
        log.trace("{#: "+thisInstance+"}.isAlive() --> "+ret);
        return ret;
    }

    public void reconnect(NedWorker worker) {
        // all capas and transmode already set in constructor
        // nothing needs to be done
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

    public boolean isConnection(String deviceId,
                                InetAddress ip,
                                int port,
                                String luser,
                                boolean trace,
                                int connectTimeout, // msecs
                                int readTimeout,    // msecs
                                int writeTimeout) { // msecs

        log.trace("{#: "+thisInstance+"}.isConnection() -->");

        boolean isConnection = ((this.device_id.equals(deviceId)) &&
                                (this.ip.equals(ip)) &&
                                (this.port == port) &&
                                (this.luser.equals(luser)) &&
                                (this.trace == trace) &&
                                (this.connectTimeout == connectTimeout) &&
                                (this.readTimeout == readTimeout) &&
                                (this.writeTimeout == writeTimeout));


        log.trace("{#: "+thisInstance+"}.isConnection() --> "+isConnection);

        return isConnection;
    }

    private boolean buildCommand(String cmdName, ConfObject[] kp,
                                 StringBuilder cmdLine, ConfXMLParam[] p)
        throws NedException {
        MaapiSchemas.CSNode cs;
        int kpIndex =  kp.length;
        cs = netsimgenCs;

        cmdLine.append(cmdName);

        for (--kpIndex; kpIndex >= 0; --kpIndex) {
            ConfObject o = kp[kpIndex];

            cmdLine.append(" ");
            if (o instanceof ConfTag) {
                ConfTag t = (ConfTag) o;
                MaapiSchemas.CSNode child = getChild(cs, t);

                if (kpIndex == kp.length - 1) {
                    cmdLine.append("/");
                }
                cmdLine.append(child.getTag());
                cs = child;

            } else if (o instanceof ConfKey) {
                cmdLine.append(" ");
                cmdLine.append(keyToString(cs, (ConfKey) o));
            } else {
                throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                       "Internal error when "+
                                       "building command: "+
                                       cmdLine);
            }
        }

        for (int i = 0; i < p.length; ++i) {
            String tag = p[i].getTag();
            ConfObject val = p[i].getValue();

            cmdLine.append(" ");
            cmdLine.append(tag);

            if (val != null) {
                cmdLine.append(" ");
                cmdLine.append(quoteString(val.toString()));
            }
        }
        return true;
    }

    @Override
    public void command(NedWorker worker, String cmd, ConfXMLParam[] params)
        throws Exception {
        if (trace)
            session.setTracer(worker);
        String name = "com.tailf.packages.ned.netsimgen.namespaces.router";
        try {
            Class<?> cls = Class.forName(name);
            Constructor<?> cons = cls.getConstructor();
            ConfNamespace n = (ConfNamespace) cons.newInstance();
            if (cmd.equals("archive-log")) {
                worker.commandResponse(new ConfXMLParam[] {
                        new ConfXMLParamValue(n, "result", new ConfBuf("done"))
                    });
            }
        } catch (Exception e) {
            worker.error(NedCmd.CMD, "not implemented");
        }
    }

    /**
     * Establish a new connection to a device and send response to
     * NSO with information about the device.
     *
     * @param deviceId name of devide
     * @param ip address to connect to device
     * @param port port to connect to
     * @param proto ssh or telnet
     * @param ruser name of user to connect as
     * @param pass password to use when connecting
     * @param publicKeyDir directory to read public keys.
     *         null if password given
     * @param trace indicates if trace messages should be generated or not
     * @param connectTimeout in milliseconds
     * @param readTimeout in milliseconds
     * @param writeTimeout in milliseconds
     * @return the connection instance
     **/
    public NedGenericBase newConnection(String deviceId,
                                    InetAddress ip,
                                    int port,
                                    String luser,
                                    boolean trace,
                                    int connectTimeout, // msecs
                                    int readTimeout,    // msecs
                                    int writeTimeout,   // msecs
                                    NedMux mux,
                                    NedWorker worker) {

        log.trace("newConnection() -->");
        log.trace("deviceId: \"" + deviceId + "\"");
        log.trace("ip: \"" + ip  + "\"");
        log.trace("port: \"" + port  + "\"");
        log.trace("luser: \"" + luser + "\"");
        log.trace("trace: \"" + trace + "\"");
        log.trace("connectTimeout: \"" + connectTimeout + "\"");
        log.trace("readTimeout: \"" + readTimeout +  "\"");
        log.trace("writeTimeout: \"" + writeTimeout + "\"");
        log.trace("mux: \"" + mux + "\"");
        log.trace("worker: \"" + worker.getName() + "\"");

        try{
            NetsimGen ned = new NetsimGen();
            ResourceManager.registerResources(ned);
            ned.init(deviceId, ip, port, luser, trace,
                     connectTimeout, readTimeout, writeTimeout,
                     mux, worker);

            log.trace("newConnection() --> " + ned);
            return ned;
        } catch (Exception e) {
            log.error("newConnection() FAILURE!",e);
            return null;
        }
    }

    public NedGenericBase initNoConnect(String device_id,
                                        NedMux mux,
                                        NedWorker worker)
        throws NedWorker.NotEnoughDataException {
        try {
            NetsimGen ned = new NetsimGen();
            ResourceManager.registerResources(ned);
            return ned.init(device_id, mux, worker);
        } catch (Exception e) {
            log.error("initNoConnect() FAILURE!",e);
            return null;
        }
    }

    class NedGenericException extends Exception{
        private static final long serialVersionUID = 2039866001097686087L;
    }

    public void getTransId(NedWorker w) throws Exception {
        log.trace("{#: "+thisInstance+"}.getTransId() <--");

        String cmd = "show netconf-state datastores | display xpath | include transaction-id";
        String res = executeCommand(cmd, operPrompt, false);
        res = res.substring(res.lastIndexOf(" ") + 1);
        w.getTransIdResponse(res);

        log.trace("{#: "+thisInstance+"}.getTransId() --> "+res);
    }

    public String toString(){
        String str = "{"+
            "instance: "+thisInstance+
            ", device_id: "+device_id+
            ", ip: "+ip+
            ", port: "+port+
            ", luser: "+luser+
            "}";
        return str;
    }
}
