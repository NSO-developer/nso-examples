package com.tailf.packages.ned.netsimgen;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiException;
import com.tailf.maapi.MaapiSchemas;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedEditOp;
import com.tailf.ned.NedException;
import com.tailf.ned.NedGenericBase;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedTTL;
import com.tailf.ned.NedTracer;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedErrorCode;
import com.tailf.ned.NedWorker.TransactionIdMode;
import com.tailf.packages.ned.netsimgen.namespaces.*;

/**
 * This class implements NED interface
 *
 */
public class NetsimGen extends NedGenericBase {
    private String date_string = "2025-03-25";
    private String version_string = "1.0.0.0";

    private static int instance = 0;

    // Instance data and methods
    private int         thisInstance = 0;
    private String      device_id;
    private String      ncs_prefix;
    private String      ncs_stat_prefix;
    MaapiSchemas.CSNode netsimgenCs;
    MaapiSchemas.CSNode netsimgenStatCs;

    private RestconfClient restconf;
    private String pendingYangPatchJson = null;  // staged in prepare(), sent in commit()
    private boolean connected = false;

    private InetAddress ip;
    private int         port;
    private String      luser;
    private boolean     trace;
    private int         connectTimeout;  // msec
    private int         readTimeout;     // msec
    private int         writeTimeout;    // msec
    NedCapability[]     capabilities;
    private String      ruser;
    private String      rpassword;

    private MaapiSchemas schemas;

    private static Logger log =
        LogManager.getLogger(NetsimGen.class);

    // Connects to the NcsServer and sets mm
    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public Maapi mm;

    private synchronized void incrInstance(){
        this.thisInstance = ++NetsimGen.instance;
    }

    private NetsimGen init(String device_id,
                      NedMux mux,
                      NedWorker worker) {
        this.device_id = device_id;
        this.ncs_prefix = "/ncs:devices/device{"+device_id+"}/config";
        this.ncs_stat_prefix = "/ncs:devices/device{"+device_id+"}/live-status";

        try {
            int usid = worker.getUsid();
            mm.setUserSession(usid);
            int tid = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
            mm.finishTrans(tid);
        } catch (Exception e) {
            log.error("Cannot set user session", e);
        }

        incrInstance();

        this.schemas = Maapi.getSchemas();

        try {
            this.netsimgenCs = schemas.findCSNode(Ncs.uri, ncs_prefix);
        } catch (Exception e) {
            log.error("Cannot get root YANG node: ", ncs_prefix, e);
        }

        try {
            this.netsimgenStatCs = schemas.findCSNode(Ncs.uri, ncs_stat_prefix);
        } catch (Exception e) {
            log.error("Cannot get root YANG node: ", ncs_stat_prefix, e);
        }

        this.device_id = device_id;
        useStoredCapabilities();
        return this;
    }

    private NetsimGen init(String deviceId,
                        InetAddress anIp, int aPort,
                        String aLuser, boolean aTrace,
                        int aConnectTimeout, int aReadTimeout, int aWriteTimeout,
                        NedMux aMux, NedWorker aWorker) throws NedGenericException, NedException {

        this.device_id = deviceId;
        this.ncs_prefix = "/ncs:devices/device{" + deviceId + "}/config";
        this.ncs_stat_prefix = "/ncs:devices/device{" + deviceId + "}/live-status";
        this.ip = anIp;
        this.port = aPort;
        this.luser = aLuser;
        this.trace = aTrace;
        this.connectTimeout = aConnectTimeout;
        this.readTimeout    = aReadTimeout;
        this.writeTimeout   = aWriteTimeout;
        this.ruser = aWorker.getRemoteUser();
        this.rpassword = aWorker.getPassword();

        aWorker.trace("NED VERSION: netsim-gen "+version_string+" "+date_string,"out",deviceId);

        incrInstance();
        this.schemas = Maapi.getSchemas();
        try { this.netsimgenCs = schemas.findCSNode(Ncs.uri, ncs_prefix); } catch (Exception e) {
            log.error("Cannot get root YANG node: " + ncs_prefix, e);
        }
        try { this.netsimgenStatCs = schemas.findCSNode(Ncs.uri, ncs_stat_prefix); } catch (Exception e) {
            log.error("Cannot get root YANG node: " + ncs_stat_prefix, e);
        }

        // Build RESTCONF base URI, ex: https://<ip>:<port>/restconf/
        try {
            var base = new java.net.URI("https", null, ip.getHostAddress(), port, "/restconf/", null, null);
            this.restconf = new RestconfClient(base, ruser, rpassword, connectTimeout, readTimeout);

            // Lightweight health check
            var resp = restconf.get("data?depth=1", "application/yang-data+json");
            if (resp.statusCode() / 100 == 2) {
                this.capabilities = getCapabilities(aWorker);
                setConnectionData(this.capabilities, this.capabilities, false,
                                TransactionIdMode.UNIQUE_STRING);
                this.connected = true;
            } else {
                aWorker.connectError(NedErrorCode.CONNECT_CONNECTION_REFUSED,
                                    "RESTCONF GET /data failed: " + resp.statusCode());
            }
        } catch (Exception e) {
            log.error("RESTCONF connect failed", e);
            aWorker.connectError(NedErrorCode.CONNECT_CONNECTION_REFUSED, e.getMessage());
        }
        return this;
    }

    private NedCapability[] getCapabilities(NedWorker worker) throws NedException {
        ArrayList<NedCapability> list = new ArrayList<>();

        // Probe YANG library via RESTCONF for module/features (RFC 8040 §3.7)
        String rel = "data/tailf-confd-monitoring:confd-state/loaded-data-models";
        try {
            var resp = restconf.get(rel, "application/yang-data+xml");
            if (resp.statusCode() / 100 == 2) {
                String xml = resp.body();
                try (InputStream xmlStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(xmlStream);

                    NodeList nsNodes = doc.getElementsByTagNameNS("*", "namespace");
                    for (int i = 0; i < nsNodes.getLength(); i++) {
                        String namespace = nsNodes.item(i).getTextContent();
                        if (namespace != null) {
                            namespace = namespace.trim();
                        }
                        if (namespace == null || namespace.isEmpty()) {
                            continue;
                        }

                        if ((namespace.indexOf("ned") >= 0 && namespace.startsWith("http://tail-f.com")) ||
                            (!namespace.startsWith("urn:ietf") && !namespace.startsWith("http://tail-f.com"))) {

                            list.add(new NedCapability(
                                    namespace,
                                    "",
                                    new ArrayList<String>(),
                                    "",
                                    new ArrayList<String>()));
                        }
                    }
                } catch (IOException e) {
                    log.error("Error reading yang-library response", e);
                } catch (Exception e) {
                    log.error("Error parsing yang-library response", e);
                }
            }
        } catch (Exception e) { log.error("Cannot get loaded-data-models", e); }

        // Two existing internal caps so NSO can call show-partial & auto-config:
        list.add(new NedCapability(
            "http://tail-f.com/ns/ncs-ned/show-partial?path-format=key-path",
            "", Collections.emptyList(), "", Collections.emptyList()));
        list.add(new NedCapability("http://tail-f.com/ns/ncs-ned/show-auto-config","",
            Collections.emptyList(), "", Collections.emptyList()));
        return list.toArray(new NedCapability[0]);
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
        return new String[] { "r" };
    }

    // Which identity is implemented by the class
    public String identity() {
        return "id:router-rc-1.0";
    }

    @Override
    public void prepare(NedWorker worker, NedEditOp[] ops)
            throws NedException, IOException {
        log.trace("{#: "+thisInstance+"}.prepare() <-- (RESTCONF)");
        try {
            // Build and cache a YANG-Patch body from the ops
            this.pendingYangPatchJson = YangPatchBuilder.fromOps(ops,
                op -> restconfPathInfoForOp(op, netsimgenCs)  // supply schema root
            );
            worker.prepareResponse();  // no device change yet
        } catch (Exception e) {
            this.pendingYangPatchJson = null;
            log.error("Error in prepare", e);
            worker.error(NedCmd.PREPARE_GENERIC, e.getMessage(), "build yang-patch");
        }
    }

    @Override
    public void prepareDry(NedWorker worker, NedEditOp[] ops) throws NedException {
        log.trace("{#: "+thisInstance+"}.prepareDry() <-- (RESTCONF)");
        try {
            this.pendingYangPatchJson = YangPatchBuilder.fromOps(ops,
                op -> restconfPathInfoForOp(op, netsimgenCs)  // supply schema root
            );
            worker.prepareDryResponse(pendingYangPatchJson);  // show the patch
        } catch (Exception e) {
            log.error("Error in prepareDry", e);
            worker.error(NedCmd.PREPARE_GENERIC, e.getMessage(), "dry-run");
        }
    }

    @Override
    public void commit(NedWorker worker, int timeout) throws Exception {
        log.trace("{#: "+thisInstance+"}.commit() <-- (RESTCONF)");
        try {
            if (pendingYangPatchJson != null) {
                log.trace("PATCH with body: {}", pendingYangPatchJson);
                var resp = restconf.patchYang("data", pendingYangPatchJson);  // PATCH /restconf/data
                //FIXME: enable error handling when ENG-38449 is fixed
                /* if (resp.statusCode() / 100 != 2) {
                    throw new NedException(NedErrorCode.NED_EXTERNAL_ERROR,
                        "PATCH failed: HTTP " + resp.statusCode() + " " + resp.body());
                } */
                pendingYangPatchJson = null;
            }
            worker.commitResponse();
        } catch (Exception e) {
            log.error("Commit failed", e);
            worker.error(NedCmd.COMMIT, e.getMessage(), "RESTCONF patch");
        }
    }

    @Override
    public void abort(NedWorker worker, NedEditOp[] ignored) throws NedException, IOException {
        log.trace("{#: "+thisInstance+"}.abort() <-- (RESTCONF)");
        pendingYangPatchJson = null;  // reset
        worker.abortResponse();
    }

    @Override
    public void revert(NedWorker worker, NedEditOp[] ignored) throws NedException, IOException {
        log.trace("{#: "+thisInstance+"}.revert() <-- (RESTCONF)");
        pendingYangPatchJson = null;  // reset
        worker.revertResponse();
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

    @Override
    public void persist(NedWorker worker) throws NedException, IOException {
        log.trace("{#: "+thisInstance+"}.persist() <-- (RESTCONF XML)");
        // Applied the change in commit(); RESTCONF has no extra “persist” step.
        worker.persistResponse();
    }

    @Override
    public void close(NedWorker worker) {
        log.trace("{#: "+thisInstance+"}.close(worker) <-- (RESTCONF XML)");
        connected = false;
    }

    @Override
    public void close() {
        log.trace("{#: "+thisInstance+"}.close() <-- (RESTCONF XML)");
        connected = false;
    }

    @Override
    public void show(NedWorker worker, int th) throws NedException, IOException {
        log.trace("{#: "+thisInstance+"}.show() <-- (RESTCONF XML)");
        try {
            mm.attach(th, -1, 1);

            MaapiSchemas.CSNode parent = netsimgenCs;
            for (MaapiSchemas.CSNode n : parent.getChildren()) {
                String moduleTop = rf8040ModuleName(n) + ":" + n.getTag();
                String rel = "data/" + moduleTop + "?content=config";

                var resp = restconf.get(rel, "application/yang-data+xml");
                if (resp.statusCode() / 100 != 2) {
                    log.warn("RESTCONF GET {} -> {}", rel, resp.statusCode());
                    continue;
                }

                String inner = Xml2Maapi.unwrapRestconfData(resp.body());
                if (inner == null || inner.isEmpty()) continue;

                // Find the namespace for the current node
                ConfNamespace ns = ConfNamespace.findNamespace(n.getNS());

                // Parse once, then feed subtree(s) to MAAPI using schema
                org.w3c.dom.Document doc = Xml2Maapi.parseXml("<root>"+inner+"</root>");
                org.w3c.dom.NodeList tops = doc.getDocumentElement().getChildNodes();
                for (int i = 0; i < tops.getLength(); i++) {
                    org.w3c.dom.Node node = tops.item(i);
                    if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                    org.w3c.dom.Element el = (org.w3c.dom.Element) node;

                    // Only apply elements matching this top node
                    if (n.getTag().equals(el.getLocalName())) {
                        Xml2Maapi.applyModuleSubtree(mm, th, ns, n, ncs_prefix, el);
                    }
                }
            }

            mm.detach(th);
            worker.showGenericResponse();
        } catch (Exception e) {
            log.error("show() RESTCONF XML error", e);
            worker.error(NedCmd.SHOW_GENERIC, e.getMessage(), "RESTCONF XML");
        }
    }

    @Override
    public void showPartial(NedWorker worker, int th, ConfPath[] paths)
            throws NedException, IOException {
        log.trace("{#: "+thisInstance+"}.show_partial() <-- (RESTCONF XML)");
        try {
            mm.attach(th, -1, 1);
            for (ConfPath cp : paths) {
                // Build RESTCONF path from ConfPath
                String rel = "data/" + restconfPathForConfPath(cp, netsimgenCs) + "?content=config";
                var resp = restconf.get(rel, "application/yang-data+xml");
                if (resp.statusCode() / 100 != 2) {
                    log.warn("RESTCONF GET {} -> {}", rel, resp.statusCode());
                    continue;
                }
                String inner = Xml2Maapi.unwrapRestconfData(resp.body());
                if (inner == null || inner.isEmpty()) continue;

                // Work out the top schema node & namespace at cp root
                // Map cp’s last tag to a CSNode and use that as base
                com.tailf.conf.ConfObject[] kp = cp.getKP();
                com.tailf.conf.ConfTag lastTag = (com.tailf.conf.ConfTag) kp[ kp.length - 1 ];
                MaapiSchemas.CSNode base = getChild(netsimgenCs, lastTag);
                ConfNamespace ns = ConfNamespace.findNamespace(base.getNS());

                org.w3c.dom.Document doc = Xml2Maapi.parseXml("<root>"+inner+"</root>");
                org.w3c.dom.NodeList tops = doc.getDocumentElement().getChildNodes();
                for (int i = 0; i < tops.getLength(); i++) {
                    org.w3c.dom.Node node = tops.item(i);
                    if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                    org.w3c.dom.Element el = (org.w3c.dom.Element) node;

                    if (base.getTag().equals(el.getLocalName())) {
                        Xml2Maapi.applyModuleSubtree(mm, th, ns, base, ncs_prefix, el);
                    }
                }
            }
            mm.detach(th);
            worker.showGenericResponse();
        } catch (Exception e) {
            log.error("show_partial() RESTCONF XML error", e);
            worker.error(NedCmd.SHOW_PARTIAL_GENERIC, e.getMessage(), "RESTCONF XML");
        }
    }

    @Override
    public void showStatsPath(NedWorker worker, int th, ConfPath path)
        throws Exception {
        log.trace("{#: "+thisInstance+"}.showStatsPath() <-- (RESTCONF XML)");
        showStatsConfPath(worker, th, new ConfPath[]{removeLiveStatus(path)});
        worker.showStatsPathResponse(new NedTTL[] {
            new NedTTL(path, 60, true)
        });
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

    /** Find the NavuNode under /live-status that corresponds to the given ConfPath (without /live-status). */
    private NavuNode navuAtLiveStatusSubtree(NavuNode liveStatus, ConfPath noLive) throws Exception {
        // Start at live-status
        NavuNode cur = liveStatus;

        // Track schema so we can tell container vs list and map keys correctly
        MaapiSchemas.CSNode cs = netsimgenStatCs;

        // Walk ConfPath from outermost to innermost
        ConfObject[] kp = noLive.getKP();
        for (int i = kp.length - 1; i >= 0; i--) {
            ConfObject o = kp[i];
            if (o instanceof ConfTag) {
                ConfTag tag = (ConfTag) o;
                MaapiSchemas.CSNode child = getChild(cs, tag);

                if (child.isList()) {
                    // Navigate to the list node itself (no keys at this step)
                    cur = cur.list(tag.getTagHash());
                } else if (child.isContainer()) {
                    cur = cur.container(tag.getTagHash());
                } else {
                    // Leaf/leaf-list: return the parent; setValues() isn’t for leaves
                    return cur;
                }
                cs = child;
            } else if (o instanceof ConfKey) {
                ConfKey key = (ConfKey) o;

                // Build key strings in schema order
                java.util.List<String> keyStrings = new java.util.ArrayList<>();
                com.tailf.conf.ConfObject[] keyElems = key.elements();

                for (int k = 0; ; k++) {
                    MaapiSchemas.CSNode keyNode;
                    try {
                        keyNode = cs.getKey(k);  // schema key #k
                        if (keyNode == null) break;  // end (some impls return null)
                    } catch (Exception e) {
                        break;  // end (some impls throw)
                    }
                    if (k >= keyElems.length) break;  // safety

                    // Convert ConfValue to canonical string per schema type
                    String s = schemas.valueToString(
                            keyNode.getNodeInfo().getType(),
                            (ConfValue) keyElems[k]);
                    keyStrings.add(s);
                }
                // Navigate to the list entry with these keys
                cur = (NavuNode)((NavuList)cur).elem(keyStrings.toArray(new String[0]));
            }
        }
        return cur;
    }

    protected void showStatsConfPath(NedWorker worker, int th, ConfPath[] paths) throws Exception {
        log.trace("{#: "+thisInstance+"}.showStatsConfPath() <-- (RESTCONF XML)");
        mm.attach(th, 0);

        NavuNode liveStatus = new NavuContainer(mm, th, Ncs.hash)
            .container(Ncs._devices)
            .list(Ncs._device)
            .elem(device_id)
            .container(Ncs._live_status);

        for (ConfPath path : paths) {
            ConfPath noLive = removeLiveStatus(path);
            String rel = "data/" + restconfPathForConfPath(noLive, netsimgenStatCs) + "?content=nonconfig";
            var resp = restconf.get(rel, "application/yang-data+xml");

            if (resp.statusCode() / 100 != 2) {
                log.warn("RESTCONF GET {} -> {}", rel, resp.statusCode());
                continue;
            }

            // Strip the <data> wrapper so we store only the subtree content
            String inner = Xml2Maapi.unwrapRestconfData(resp.body());
            if (inner == null || inner.trim().isEmpty()) continue;

            // Navigate to the exact node for the requested path, e.g. /r:sys/interfaces/interface
            NavuNode target = navuAtLiveStatusSubtree(liveStatus, noLive);

            // Now set the values on that node: for a list node, inner should contain one or more <interface> entries;
            // for a container node, inner should be its child XML.
            target.setValues(inner);
        }

        mm.detach(th);
    }

    @Override
    public boolean isAlive(NedWorker worker) { return connected; }

    @Override
    public void reconnect(NedWorker worker) {
        // nothing needs to be done
    }

    @Override
    public boolean keepAlive(NedWorker worker) {
        try {
            var r = restconf.get("data?depth=1", "application/yang-data+json");
            return (r.statusCode() / 100) == 2;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean isConnection(String deviceId,
                                InetAddress ip,
                                int port,
                                String luser,
                                boolean trace,
                                int connectTimeout,  // msecs
                                int readTimeout,     // msecs
                                int writeTimeout) {  // msecs
        log.trace("{#: "+thisInstance+"}.isConnection() -->");
        boolean isConnection = ((this.device_id.equals(deviceId)) &&
                                (this.ip.equals(ip)) &&
                                (this.port == port) &&
                                (this.luser.equals(luser)) &&
                                (this.trace == trace) &&
                                (this.connectTimeout == connectTimeout) &&
                                (this.readTimeout == readTimeout) &&
                                (this.writeTimeout == writeTimeout));
        return isConnection;
    }

    @Override
    public void command(NedWorker worker, String cmd, ConfXMLParam[] params) throws Exception {
        log.trace("{#: " + thisInstance + "}.command(" + cmd + ") -->");

        if (!"archive-log".equals(cmd)) {
            worker.error(NedCmd.CMD, "not implemented");
            return;
        }

        // Names/URI for the XML body
        final com.tailf.conf.ConfNamespace n = new com.tailf.packages.ned.netsimgen.namespaces.router();
        final String module = "router";
        int i = n.uri().lastIndexOf('#');
        final String nsUri  = n.uri().substring(i + 1);
        final String pfx    = n.prefix();  // "r"

        // 1) Extract input leaves from params
        String archivePath = null;
        Boolean compress = null;
        for (ConfXMLParam p : params) {
            if (p instanceof ConfXMLParamValue) {
                ConfXMLParamValue v = (ConfXMLParamValue) p;
                String tag = v.getTag();
                if ("archive-path".equals(tag)) {
                    archivePath = v.getValue().toString();
                } else if ("compress".equals(tag)) {
                    ConfObject val = v.getValue();
                    compress = (val instanceof ConfBool) ? ((ConfBool) val).booleanValue()
                                                        : Boolean.valueOf(val.toString());
                }
            }
        }
        if (archivePath == null) archivePath = "";
        if (compress == null) compress = Boolean.FALSE;

        // 2) Get the current action path and extract server key + build list-instance RFC8040 path
        ConfPath actionPath = worker.getCurrentPath();
        // Example: /ncs:devices/device{ex0}/config/r:sys/syslog/server{10.3.4.5}/archive-log
        String listInstancePath = restconfPathToListInstance(actionPath, "server");  // e.g. "router:sys/syslog/server=10.3.4.5"
        if (listInstancePath == null) {
            worker.error(NedCmd.CMD, "archive-log: could not resolve syslog server list instance from current path");
            return;
        }

        // 3) Build RESTCONF relative URL for the action (module-qualify the action name)
        String rel = "data/" + listInstancePath + "/" + module + ":archive-log";

        // 4) Build XML body
        StringBuilder body = new StringBuilder();
        body.append("<").append(pfx).append(":archive-log xmlns:")
            .append(pfx).append("=\"").append(nsUri).append("\">")
            .append("<archive-path>").append(escapeXml(archivePath)).append("</archive-path>")
            .append("<compress>").append(compress ? "true" : "false").append("</compress>")
            .append("</").append(pfx).append(":archive-log>");

        // 5) POST and parse response
        log.trace("{#: \" + thisInstance + \"}.command(archive-log) POST rel: {} body.toString(): {}", rel, body.toString());
        var resp = restconf.postXml(rel, body.toString());
        int sc = resp.statusCode();
        if (sc / 100 != 2) {
            String msg = "archive-log RESTCONF POST failed: HTTP " + sc + " body: " + resp.body();
            log.error(msg);
            worker.error(NedCmd.CMD, msg);
            return;
        }

        String resultVal = parseActionResult(resp.body(), nsUri, "result");
        if (resultVal == null || resultVal.isEmpty()) resultVal = "done";

        worker.commandResponse(new ConfXMLParam[] {
            new ConfXMLParamValue(n, "result", new ConfBuf(resultVal))
        });
        log.trace("{#: " + thisInstance + "}.command(archive-log) --> OK");
    }

    private String restconfPathToListInstance(ConfPath current, String listLocalName) {
        try {
            StringBuilder sb = new StringBuilder();
            ConfObject[] kp = current.getKP();
            // Start at config root schema
            MaapiSchemas.CSNode cs = netsimgenCs;
            boolean first = true;

            for (int i = kp.length - 1; i >= 0; i--) {
                ConfObject o = kp[i];
                if (o instanceof ConfTag) {
                    ConfTag t = (ConfTag) o;
                    if ("archive-log".equals(t.getTag())) {
                        // skip the action node; it's not part of the data path
                        continue;
                    }
                    // Only walk nodes under config root; ignore ncs:devices/... wrappers by catching failures
                    MaapiSchemas.CSNode child;
                    try { child = getChild(cs, t); }
                    catch (Exception e) { continue; }  // outside our module/config subtree

                    String seg = child.getTag();
                    if (first) {
                        String moduleName = child.getSchema().getModule();  // RFC8040 module
                        sb.append(moduleName).append(":").append(seg);
                        first = false;
                    } else {
                        sb.append("/").append(seg);
                    }
                    cs = child;

                    if (listLocalName.equals(seg)) {
                        // Expect ConfKey next
                        if (i > 0 && kp[i - 1] instanceof ConfKey) {
                            ConfKey ck = (ConfKey) kp[--i];
                            // Build comma-separated key string (each value URL-encoded, commas literal)
                            StringBuilder csv = new StringBuilder();
                            ConfObject[] elems = ck.elements();
                            for (int k = 0; ; k++) {
                                MaapiSchemas.CSNode keyNode;
                                try {
                                    keyNode = cs.getKey(k);
                                    if (keyNode == null) break;
                                } catch (Exception ignore) { break; }
                                String v = schemas.valueToString(keyNode.getNodeInfo().getType(),
                                                                (ConfValue) elems[k]);
                                if (k > 0) csv.append(',');
                                csv.append(encKeyVal(v));
                            }
                            sb.append('=').append(csv);
                            return sb.toString();  // stop at the list instance
                        } else {
                            return null;  // malformed path
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("restconfPathToListInstance: failed to build list instance path", e);
        }
        return null;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  b.append("&amp;");  break;
                case '<':  b.append("&lt;");   break;
                case '>':  b.append("&gt;");   break;
                case '"':  b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default:   b.append(c);
            }
        }
        return b.toString();
    }

    private static String parseActionResult(String xml, String nsUri, String localName) {
        if (xml == null || xml.trim().isEmpty()) return null;
        try (java.io.InputStream in = new java.io.ByteArrayInputStream(
                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(in);
            org.w3c.dom.NodeList nl = doc.getElementsByTagNameNS(nsUri, localName);
            if (nl.getLength() > 0) return nl.item(0).getTextContent().trim();
            nl = doc.getElementsByTagName(localName);  // fallback
            if (nl.getLength() > 0) return nl.item(0).getTextContent().trim();
        } catch (Exception e) {
            log.warn("archive-log: result XML parse failed", e);
        }
        return null;
    }

    @Override
    public NedGenericBase newConnection(String deviceId,
                                    InetAddress ip,
                                    int port,
                                    String luser,
                                    boolean trace,
                                    int connectTimeout,  // msecs
                                    int readTimeout,     // msecs
                                    int writeTimeout,    // msecs
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

    @Override
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

    @Override
    public void getTransId(NedWorker worker) throws Exception {
        String transId = "no-trans-id";
        final String rel = "data/ietf-netconf-monitoring:netconf-state/datastores/datastore=running/tailf-netconf-monitoring:transaction-id";
        try {
            var resp = restconf.get(rel, "application/yang-data+xml");
            if (resp.statusCode() / 100 == 2) {
                String xml = resp.body();
                try (InputStream xmlStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignore) {}
                    try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignore) {}
                    try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignore) {}
                    dbf.setExpandEntityReferences(false);

                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(xmlStream);

                    // Find all <transaction-id> elements regardless of the document's default namespace.
                    NodeList nodes = doc.getElementsByTagNameNS("*", "transaction-id");
                    transId = nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
                } catch (IOException e) {
                    log.error("Error reading transaction-id response", e);
                } catch (Exception e) {
                    log.error("Error parsing transaction-id response", e);
                }
            } else {
                log.warn("RESTCONF GET {} failed: HTTP {}", rel, resp.statusCode());
            }
        } catch (Exception e) { log.error("Cannot get transaction-id", e); }
        log.trace("Transaction ID: {}", transId);
        worker.getTransIdResponse(transId);
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

    // Turn a NedEditOp path into a relative "data/<module>:a/b=listKey/leaf" path
    private String restconfPathForOp(NedEditOp op) {
        try {
            return "data/" + restconfPathForConfPath(op.getPath(), netsimgenCs);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build RESTCONF path for: " + op.getPath(), e);
        }
    }

    private String restconfPathForConfPath(ConfPath cp, MaapiSchemas.CSNode cs) {
        try {
            StringBuilder sb = new StringBuilder();
            ConfObject[] kp = cp.getKP();
            int kpIndex = kp.length;

            boolean first = true;

            for (--kpIndex; kpIndex >= 0; --kpIndex) {
                ConfObject o = kp[kpIndex];

                if (o instanceof ConfTag) {
                    ConfTag t = (ConfTag) o;
                    MaapiSchemas.CSNode child = getChild(cs, t);

                    String seg = child.getTag();
                    if (first) {
                        // RFC8040: <module-name>:<data-node>
                        String moduleName = rf8040ModuleName(child);
                        seg = moduleName + ":" + seg;
                        first = false;
                    }
                    sb.append(seg);
                    cs = child;

                } else if (o instanceof ConfKey) {
                    // Remove the slash we added after the list name in the previous iteration
                    int len = sb.length();
                    if (len > 0 && sb.charAt(len - 1) == '/') {
                        sb.setLength(len - 1);
                    }

                    // schema-aware key formatting returns "k1 k2 ..."
                    String keyStr = keyToString(cs, (ConfKey) o);
                    String[] keys = keyStr.split("\\s+");

                    // Encode EACH key value, then join with a literal comma (do NOT encode the separator)
                    StringBuilder csv = new StringBuilder();
                    for (int j = 0; j < keys.length; j++) {
                        if (j > 0) csv.append(',');
                        // URLEncoder is form-encoding; convert '+' back to '%20'
                        String enc = java.net.URLEncoder.encode(keys[j], java.nio.charset.StandardCharsets.UTF_8.name());
                        enc.replace("+", "%20");
                        csv.append(enc);
                    }
                    sb.append('=').append(csv);
                }

                if (kpIndex > 0) sb.append("/");
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build RESTCONF path for ConfPath: " + cp, e);
        }
    }

    // RFC 8040 requires module-name (not prefix) in the first path segment.
    private String rf8040ModuleName(MaapiSchemas.CSNode cs) {
        return cs.getSchema().getModule();  // actual YANG module name
    }

    private static String encKeyVal(String s) throws java.io.UnsupportedEncodingException {
        // Encode each key value, but we'll join with a *literal* comma
        String enc = java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name());
        // URLEncoder turns spaces into '+', but RFC8040 URIs want %20
        enc = enc.replace("+", "%20");
        // If value itself contained a comma, keep it encoded (don’t undo %2C here)
        return enc;
    }

    private PathInfo restconfPathInfoForOp(NedEditOp op, MaapiSchemas.CSNode rootCs) {
        try {
            ConfPath cp = op.getPath();
            StringBuilder sb = new StringBuilder();
            ConfObject[] kp = cp.getKP();
            int i = kp.length - 1;
            boolean first = true;
            MaapiSchemas.CSNode cs = rootCs;
            MaapiSchemas.CSNode last = null;

            // Only the *final* node’s keys should be carried in PathInfo for building the JSON value
            java.util.List<String> finalKeyNames = java.util.Collections.emptyList();
            java.util.List<String> finalKeyValues = java.util.Collections.emptyList();

            while (i >= 0) {
                // Expect a tag
                if (!(kp[i] instanceof ConfTag)) {
                    throw new IllegalStateException("Expected ConfTag at index " + i + " in " + cp);
                }
                ConfTag t = (ConfTag) kp[i--];

                MaapiSchemas.CSNode child = getChild(cs, t);
                String seg = child.getTag();
                if (first) {
                    sb.append(rf8040ModuleName(child)).append(":").append(seg);
                    first = false;
                } else {
                    sb.append("/").append(seg);
                }
                cs = child;
                last = child;

                // If there’s a ConfKey here, process *only this list’s* keys
                if (i >= 0 && kp[i] instanceof ConfKey) {
                    ConfKey ck = (ConfKey) kp[i--];

                    // Collect this list's keys in schema order
                    java.util.List<String> thisKeyNames = new java.util.ArrayList<>();
                    java.util.List<String> thisKeyValues = new java.util.ArrayList<>();
                    ConfObject[] elems = ck.elements();
                    for (int k = 0; ; k++) {
                        MaapiSchemas.CSNode keyNode;
                        try {
                            keyNode = cs.getKey(k);
                            if (keyNode == null) break;
                        } catch (Exception e) { break; }
                        thisKeyNames.add(keyNode.getTag());
                        String v = schemas.valueToString(keyNode.getNodeInfo().getType(), (ConfValue) elems[k]);
                        thisKeyValues.add(v);
                    }

                    // Append "=k1,k2" for this list only
                    StringBuilder csv = new StringBuilder();
                    for (int j = 0; j < thisKeyValues.size(); j++) {
                        if (j > 0) csv.append(',');
                        csv.append(encKeyVal(thisKeyValues.get(j)));
                    }
                    sb.append('=').append(csv);

                    // If THIS list is the final node in the path, remember its keys for the JSON "value"
                    finalKeyNames = thisKeyNames;
                    finalKeyValues = thisKeyValues;
                }
            }
            return new PathInfo(sb.toString(), last, finalKeyNames, finalKeyValues);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to build RESTCONF path for Operation: " + op, e);
        }
    }
}

class PathInfo {
    public final String target;              // RFC8040 path (no leading slash in your builder)
    public final MaapiSchemas.CSNode cs;     // schema node at the target
    public final String nodeName;            // cs.getTag()
    public final List<String> keyNames;      // list key names in schema order
    public final List<String> keyValues;     // corresponding key values (unencoded)

    public PathInfo(String target,
                    MaapiSchemas.CSNode cs,
                    List<String> keyNames,
                    List<String> keyValues) {
        this.target   = target;
        this.cs       = cs;
        this.nodeName = cs != null ? cs.getTag() : "";
        this.keyNames = keyNames != null ? keyNames : Collections.emptyList();
        this.keyValues= keyValues!= null ? keyValues: Collections.emptyList();
    }
}
