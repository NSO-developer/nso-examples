package com.example.xmlrpcdevice.xmlrpc;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcTransportFactory;
import org.apache.xmlrpc.client.XmlRpcTransport;
import org.apache.xmlrpc.client.XmlRpcStreamTransport;
import org.apache.xmlrpc.client.XmlRpcSunHttpTransport;
import org.apache.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xml.sax.SAXException;

import com.example.xmlrpcdevice.model.DeviceInterface;
import com.example.xmlrpcdevice.model.DeviceInterfaceAlias;
import com.example.xmlrpcdevice.model.DeviceModel;
import com.example.xmlrpcdevice.xmlrpc.namespaces.interfaces;
import com.tailf.conf.ConfBinary;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfHexList;
import com.tailf.conf.ConfIPv4;
import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedEditOp;
import com.tailf.ned.NedErrorCode;
import com.tailf.ned.NedException;
import com.tailf.ned.NedGenericBase;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedTTL;
import com.tailf.ned.NedTracer;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedWorker.TransactionIdMode;
import com.tailf.ned.SSHSession;


/**
 * This class implements NED interface
 *
 */


public class XMLRpcNed extends NedGenericBase  {

    private int         connection_id;
    private String      device_id;
    private InetAddress ip;
    private int         port;
    private String      luser;
    private boolean     trace;
    private int         connectTimeout; // msec
    private int         readTimeout;    // msec
    private int         writeTimeout;   // msec
    private NedMux      mux;
    private NedWorker   worker;


    private static Logger LOGGER = LogManager.getLogger(XMLRpcNed.class);
    private XmlRpcClientConfigImpl   config;
    private XmlRpcClient             client;
    private boolean                  connected = true;

    private String                   currvid;

    public  Maapi                    maapi = null;

    private static int               count = 0;
    private boolean                  wantReverse=true;

    public XMLRpcNed(){
        this(true);
    }

    /**
     * Custom XML-RPC transport which traces (raw setting) the outgoing and
     * incoming XML-RPC messages.
     */
    private class MessageLoggingTransport extends XmlRpcSunHttpTransport
    {
        /**
         * Default constructor
         *
         * @see XmlRpcSunHttpTransport#XmlRpcSunHttpTransport(XmlRpcClient)
         * @param pClient
         */
        public MessageLoggingTransport(final XmlRpcClient pClient)
        {
            super(pClient);
        }

        /**
         * Dumps outgoing XML-RPC requests to the device trace (raw setting)
         */
        @Override
        protected void writeRequest(final XmlRpcStreamTransport.ReqWriter
            pWriter) throws IOException, XmlRpcException, SAXException
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pWriter.write(baos);
            worker.trace(baos.toString(), "out", device_id);
            super.writeRequest(pWriter);
        }

        /**
         * Dumps incoming XML-RPC responses to the device trace (raw setting)
         */
        @Override
        protected Object readResponse(XmlRpcStreamRequestConfig pConfig,
            InputStream pStream) throws XmlRpcException
        {
            final StringBuffer sb = new StringBuffer();
            try {
                final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(pStream));
                String line = reader.readLine();
                while(line != null)
                {
                    sb.append(line);
                    line = reader.readLine();
                }
            }
            catch(final IOException e) {
                LOGGER.error("While reading server response", e);
            }
            worker.trace(sb.toString(), "in", device_id);
            final ByteArrayInputStream bais =
                new ByteArrayInputStream(sb.toString().getBytes());
            return super.readResponse(pConfig, bais);
        }
    }

    public XMLRpcNed(boolean wantReverse){
        this.wantReverse = wantReverse;
    }

    private void rpcConfig(){
        try{
            config = new XmlRpcClientConfigImpl();
            String url = "http://" + this.ip.getHostAddress() +
                ":" + this.port + "/xmlrpc";
            config.setServerURL(new URL(url));
            config.setUserAgent("Apache XML RPC 1.0.0");
            config.setEnabledForExtensions(true);

            final XmlRpcTransportFactory transportFactory =
                new XmlRpcTransportFactory()
            {
                public XmlRpcTransport getTransport()
                {
                    return new MessageLoggingTransport(client);
                }
            };

            client = new XmlRpcClient();
            client.setTransportFactory(transportFactory);
            client.setConfig(config);

        }catch(MalformedURLException e){
            LOGGER.error("Errro:", e);
        }
    }


    public XMLRpcNed(String device_id,
                     InetAddress ip,
                     int port,
                     String luser,
                     boolean trace,
                     int connectTimeout,
                     int readTimeout,
                     int writeTimeout,
                     NedMux mux,
                     NedWorker worker,
                     boolean wantReverse) throws XMLRpcNoConnectionException{

        this.device_id = device_id;
        this.ip = ip;
        this.port = port;
        this.luser = luser;
        this.trace = trace;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.wantReverse = wantReverse;
        this.mux = mux;
        this.worker = worker;

        LOGGER.info("CONNECTING <==");
        try {
            rpcConfig();
            exec("Connect.newConnection",new Object[]{});

            setConnectionData(this.capas(),
                              this.statscapas(),
                              this.wantReverse,  // want reverse-diff
                              TransactionIdMode.NONE);

                    LOGGER.info("CONNECTING ==> OK");
        }catch (Exception e) {
            LOGGER.error("CONNECTING FAILURE !!!");
            LOGGER.error("Error when initailizing Ned, " +
                         " No connection to device",e);
            worker.error(NedCmd.CONNECT_GENERIC, e.getMessage(),
                         "IO error");
        }

    }

    public XMLRpcNed(String device_id, NedMux mux, NedWorker worker) {
        this.device_id = device_id;
        this.mux = mux;
        LOGGER.info("NOCONNECTING <==");
        // the following method could be used if
        // the capabilities were unknown beforehand:
        // useStoredCapabilities();
        setCapabilities(this.capas());
        LOGGER.info("NOCONNECTING ==> OK");
    }

    private NedCapability[] capas() {
        return new NedCapability[] {
            new NedCapability("http://example.com/xmlrpcdevice/xmlrpc/if",
                              "interfaces"),
        };
    }

    private NedCapability[] statscapas() {
        return new NedCapability[]{
            new NedCapability("http://example.com/xmlrpcdevice/xmlrpc/if-stats",
                              "interfaces-stats")
        };
    }

    public int connection_id() {
        return connection_id;
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
        LOGGER.info("modules");
        return new String[] { "if", "if-stats" };
    }

    // Which identity is implemented by the class
    public String identity() {
        return "id:genxmlrpc";
    }

    private Object exec(String method, Object[] params)
        throws XmlRpcException {
        return exec(method, params, false);
    }

    private Object exec(String method, Object[] params, boolean dry)
        throws XmlRpcException {
        if (dry) {
            StringBuilder ret = new StringBuilder();
            ret.append(method);
            ret.append("(");
            int i = 0;
            for (Object param: params) {
                if (i>0) ret.append(", ");
                ret.append("\"");
                ret.append(param.toString());
                ret.append("\"");
                i+=1;
            }
            ret.append(");\n");
            return ret.toString();
        } else {
            return client.execute(method, params);
        }
    }

    /**
     * Is invoked by NCS to take the configuration to a new state.
     * We retrive a rev which is a transaction handle to the
     * comming write operation then we write operations towards the device.
     * If all succeded we transition to commit phase or if
     * prepare fails we transition to abort phase.
     *
     * @param w - is the processing worker. It should be used for sending
     * responses to NCS.
     * @param data is the commands for transforming the configuration to
     * a new state.
     */
    public void prepare(NedWorker worker, NedEditOp[] ops)
        throws NedException, IOException {
        LOGGER.info("PREPARE <==");

        try {
            String rev = (String)exec("Start.start",new Object[]{});
            this.currvid = rev;

            if (this.currvid == null) {
                worker.error(NedCmd.PREPARE_GENERIC,
                             "Could not start write towards device",
                             "Edit Error");
            } else {
                edit(ops);
                worker.prepareResponse();
                LOGGER.info("PREPARE ==> OK");
            }
        } catch (Exception e) {
            LOGGER.error("Error in prepare", e);
            worker.error(NedCmd.PREPARE_GENERIC,
                         e.getMessage(),
                         "edit error");

        }
    }

    /**
     * Is invoked by NCS to ask the NED what actions it would take towards
     * the device if it would do a prepare.
     *
     * The NED can send the preformatted output back to NCS through the
     * call to  {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     * prepareDryResponse()}
     *
     * The Ned should invoke the method
     * {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     *   prepareDryResponse()} in <code>NedWorker w</code>
     * when the operation is completed.
     *
     * If the functionality is not supported or an error is detected
     * answer this through a call to
     * {@link com.tailf.ned.NedWorker#error(int,String,String) error()}
     * in <code>NedWorker w</code>.
     *
     * @param w
     *    The NedWorker instance currently responsible for driving the
     *    communication
     *    between NCS and the device. This NedWorker instance should be
     *    used when communicating with the NCS, ie for sending responses,
     *    errors, and trace messages. It is also implements the
     *    {@link NedTracer}
     *    API and can be used in, for example, the {@link SSHSession}
     *    as a tracer.
     *
     * @param ops
     *    Edit operations representing the changes to the configuration.
     */
    public void prepareDry(NedWorker worker, NedEditOp[] ops)
        throws NedException {
        StringBuilder resp = new StringBuilder();
        try {
            for (NedEditOp op : ops) {
                switch (op.getOperation()) {
                case NedEditOp.CREATED:
                    resp.append((String)create(op, true));
                    break;
                case NedEditOp.DELETED:
                    resp.append((String)delete(op, true));
                    break;
                case NedEditOp.VALUE_SET:
                    resp.append((String)valueSet(op, true));
                    break;
                }
            }
            worker.prepareDryResponse(resp.toString());
        } catch (Exception e) {
            throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                    "failed in prepareDry", e);
        }
    }

    public void commit(NedWorker worker, int timeout)
        throws NedException, IOException {
        LOGGER.info("COMMIT <==");

        try{
            Object[] param = new Object[]{
                this.currvid
            };
            LOGGER.info("Call XMLRpc CommitDevice.commit");
            boolean ret = (Boolean)exec("CommitDevice.commit",param);
            LOGGER.info("-->" + ret);
            if (ret == false) {
                worker.error(NedCmd.COMMIT,
                             "Could not Commit Data towards device",
                             "Commit Error");
            } else {
                worker.commitResponse();
                LOGGER.info("COMMIT ==> OK");
            }
        } catch(Exception e) {
            LOGGER.error("Could not commit towards the device",e);
            worker.error(NedCmd.COMMIT,
                         e.getMessage(),
                         "Commit Error");
        }

    }
    /**
     * Is invoked by NCS to abort the configuration to a previous state.
     *
     * @param w is the processing worker. It should be used for sending
     * responses to NCS. * @param data is the commands for taking the config
     * back to the previous
     * state. */

    public void abort(NedWorker worker , NedEditOp[] ops)
        throws NedException, IOException {
        LOGGER.info("ABORT <==");
        try {
            if(this.wantReverse)
                edit(ops);
            else{
                Object[] obj = new Object[]{};
                exec("AbortDevice.abort",obj);
            }
            worker.abortResponse();
            LOGGER.info("ABORT ==> OK");
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.info("Could not abort towards the device");
            worker.error(NedCmd.PREPARE_GENERIC, e.getMessage(), "edit error");
        }
    }

    public void revert(NedWorker worker , NedEditOp[] ops)
        throws NedException, IOException {
        LOGGER.info("REVERT <==");
        try {
            if(this.wantReverse){
                edit(ops);
            } else{
                Object[] param = new Object[]{};
                exec("AbortDevice.abort",param);
            }

            worker.revertResponse();
            LOGGER.info("REVERT ==> OK");
        } catch(Exception e) {
            LOGGER.info("Could not revert towards the device");
            worker.error(NedCmd.REVERT_GENERIC,
                         e.getMessage(),
                         "Revert Error");

        }

    }

    private void edit(NedEditOp[] ops)
        throws XmlRpcException, ConfException, NedException, Exception {
        for (NedEditOp op: ops) {
            switch (op.getOperation()) {
            case NedEditOp.CREATED:
                if (!(Boolean)create(op, false)) {
                    throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                           "Could not create path " +
                                           op.getPath().toString());
                }
                break;
            case NedEditOp.DELETED:
                if (!(Boolean)delete(op, false)) {
                    throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                            "Could not delete path " +
                                           op.getPath().toString());
                }
                break;
            case NedEditOp.VALUE_SET:
                if (!(Boolean)valueSet(op, false)) {
                    throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                            "Could not update path " +
                                           op.getPath().toString());
                }
                break;
            }
        }
    }

    private Object create(NedEditOp op, boolean dry)
        throws XmlRpcException, ConfException {
        LOGGER.info("op.value=" + op.getValue());
        ConfObject[] kp = op.getPath().getKP();
        LOGGER.info("op.keypath= " + java.util.Arrays.toString(kp));
        ConfKey key = null;

        Object ret;
        if (kp[0] instanceof ConfKey) {
            key = (ConfKey) kp[0];
        }

        if (kp[1] instanceof ConfTag &&
            ((ConfTag)kp[1]).getTag().equals(interfaces._alias_)) {
            ConfKey ifname = (ConfKey)kp[2];

            Object[] param =
                new Object[]{
                key.elementAt(0).toString(),
                ifname.elementAt(0).toString()
            };

            LOGGER.info(
                    "Calling XMLRpc CreateInterfaceAlias.createInterfaceAlias");
            ret = exec("CreateInterfaceAlias.createInterfaceAlias", param, dry);
        } else {
            Object[] param =
                new Object[]{
                key.elementAt(0).toString()
            };
            LOGGER.info("Calling XMLRpc CreateInterfaceAlias.createInterface");
            ret = exec("CreateInterface.createInterface", param, dry);
        }
        LOGGER.info("--> " + ret);

        return ret;
    }

    private Object valueSet(NedEditOp op, boolean dry)
        throws XmlRpcException, ConfException, Exception {
        LOGGER.info("OP VALUE " + op.getValue());

        ConfObject[] kp = op.getPath().getKP();

        LOGGER.info("OP KP: " + java.util.Arrays.toString(kp));

        ConfKey key = null;
        ConfTag tag = null;

        if (kp[0] instanceof ConfTag) {
            tag = (ConfTag) kp[0];
        }

        if (kp[1] instanceof ConfKey) {
            key = (ConfKey)kp[1];
        }

        //Magic
        if((tag.getTag().equals(interfaces._mtu_)) &&
           op.getValue().toString().equals("1789"))
           throw new Exception("Magic MTU 1789 is rejected");

        ConfObject value = op.getValue();
        String opvalue = "";

        if(value instanceof ConfBinary){
            ConfBinary binary = (ConfBinary)value;
            ConfHexList hex = new ConfHexList(binary.bytesValue());
            opvalue = hex.toString();

        } else {
            opvalue = op.getValue().toString();
        }

        Object ret;

        if((kp[2] instanceof ConfTag) &&
           ((ConfTag)kp[2]).getTag().equals(interfaces._alias_)) {
            //Update interface alias.
            Object[] param =
                new Object[]{
                ((ConfKey)kp[3]).elementAt(0).toString(),
                ((ConfKey)kp[1]).elementAt(0).toString(),
                ((ConfTag)kp[0]).getTag(),
                value.toString()};

            LOGGER.info("Call XMLRpc UpdateInterfaceAlias.setAlias");
            ret = exec("UpdateInterfaceAlias.setAlias", param, dry);
        } else {
            Object[] param =
                new Object[]{
                key.elementAt(0).toString(),
                tag.toString(),
                opvalue};

            LOGGER.info("Call XMLRpc UpdateInterface.setInterface");
            ret = exec("UpdateInterface.setInterface", param, dry);
        }
        LOGGER.info("-->" + ret);
        return ret;
    }



    private Object delete(NedEditOp op, boolean dry)
        throws XmlRpcException, ConfException {
        LOGGER.info("DEL OP VALUE " + op.getValue());

        ConfObject[] kp = op.getPath().getKP();
        ConfKey key = null;

        LOGGER.info("OP KP2 " + java.util.Arrays.toString(kp));
        if (kp[0] instanceof ConfKey) {
            key = (ConfKey) kp[0];
            //tag = (ConfTag) kp[1];
        }

        Object ret;

        //Delete alias from a interface
        if (kp[1] instanceof ConfTag
            && (((ConfTag)kp[1]).getTag().equals(interfaces._alias_))){

            Object[] param =
                new Object[]{
                ((ConfKey)kp[2]).elementAt(0).toString(),
                ((ConfKey)kp[0]).elementAt(0).toString()
            };

            LOGGER.info("Call XMLRpc DeleteInterfaceAlias.delete");
            ret = exec("DeleteInterfaceAlias.delete", param, dry);
            LOGGER.info("-->" + ret);
        }
        else if (kp[0] instanceof ConfTag) {
            LOGGER.info("Reset leaf, ignore");
            ret = dry?"[ignore reset leaf]\n":new Boolean(true);
        }
        else{
            //Delete whole interface
            Object[] param =
                new Object[]{
                key.elementAt(0).toString(),
            };

            LOGGER.info("Call XMLRpc DeleteInterface.deleteInterface");
            ret = exec("DeleteInterface.deleteInterface", param, dry);
            LOGGER.info("-->" + ret);
        }
        return ret;
    }


    public void persist(NedWorker worker)
        throws NedException, IOException {
        LOGGER.info("PERSIST <==");

        if(this.currvid != null){
            try{
                Object[] param =
                    new Object[]{ this.currvid};
                LOGGER.info("Call XMLRpc PersistDevice.persist");
                exec("PersistDevice.persist", param);
                LOGGER.info("--> true");
                worker.persistResponse();
            }catch(Exception e){
                LOGGER.error("Persist error",e);
            }

        }else{
            Object[] param = new Object[]{};
            //exec("Revert.revert",param);
        }
        LOGGER.info("PERSIST ==> OK");
    }

    public void close(NedWorker worker)
        throws NedException, IOException {
        close();
    }

    public void close() {
        if (connected) {
            try {
                LOGGER.info("CLOSE <==");
                exec("Close.closeConnection",new Object[]{});
                LOGGER.info("CLOSE ==> OK");
            } catch (Exception e) {
                LOGGER.error("Error when closing connection");
            }
        }
        try {
            ResourceManager.unregisterResources(this);
        } catch (IllegalAccessException e) {
            LOGGER.error("Error unRegistering Resources", e);
        }
    }

    /**
     * The generic show command is to
     * grab all configuration from the device and
     * populate the th passed to us.
     **/
     public void show(NedWorker worker, int th)
        throws NedException, IOException {
        LOGGER.info("SHOW <==");
        LOGGER.info("TH:" + th);
        try {
            maapi = ResourceManager.getMaapiResource(this, Scope.INSTANCE);
            LOGGER.info( this.toString()  + " Attaching to Maapi " + maapi);
            maapi.attach(th, 0);

            Object[] params =
                new Object[]{};
            DeviceModel devmodel =
                (DeviceModel) exec("GetModel.get", params);


            for(DeviceInterface interf: devmodel.getInterfaces()){

                String path0 =
                    "/ncs:devices/device{" + device_id + "}/config";
                String path = path0 + "/if:interface{" + interf.getName() + "}";
                maapi.create(th, path);

                maapi.setElem(th,
                          new ConfHexList(interf.getMacAddress()
                                          .getAddress()),
                          path + "/macaddr");

                maapi.setElem(th, new ConfIPv4(interf.getAddress()),
                          path + "/ipv4-address");

                maapi.setElem(th, new ConfIPv4(interf.getMask()),
                          path + "/ipv4-mask");


                switch (interf.getStatus()){
                case DeviceInterface.UP:
                    maapi.setElem(th, ConfEnumeration.getEnumByLabel(
                              path + "/status", "Up"),
                              path + "/status");
                    break;
                case DeviceInterface.DOWN:
                    maapi.setElem(th,ConfEnumeration.getEnumByLabel(
                              path + "/status", "Down"),
                              path + "/status");
                    break;
                }

                maapi.setElem(th,new ConfUInt32(interf.getMtu())
                          , path + "/mtu");

                for(DeviceInterfaceAlias alias : interf.getAliases()){

                    maapi.create(th, new ConfPath(path + "/alias{%x}",
                                              alias.getNumber() + ""));
                    maapi.setElem(th, new ConfIPv4(alias.getAddress()),
                              new ConfPath(path + "/alias{%x}/ipv4-address",
                                           alias.getNumber() + ""));

                    maapi.setElem(th, new ConfIPv4(alias.getMask()),
                              new ConfPath(path + "/alias{%x}/ipv4-mask",
                                           alias.getNumber()));

                }
                maapi.setElem(th, new ConfUInt32(interf.getSpeed())
                          ,  path + "/speed");

                maapi.setElem(th, new ConfInt32(interf.getTxQueuelen())
                          ,  path + "/txqueuelen");
            }
            maapi.detach(th);
        } catch (ConfException e)  {
            LOGGER.error("Error:" , e);
            worker.error(NedCmd.SHOW_GENERIC,
                         e.getMessage(),
                         "xmlrpc ned error");
            return;
        } catch (Exception e)  {
            LOGGER.error("Error:" , e);
            worker.error(NedCmd.SHOW_GENERIC,
                         e.getMessage(),
                         "IO error");
            return;
        }
        worker.showGenericResponse();
        LOGGER.info("SHOW ==> OK");
    }

    public void showStatsPath(NedWorker worker, int th, ConfPath path)
        throws NedException, IOException {

        LOGGER.info("showStatsPath invoked:worker:" + worker);
        LOGGER.info("showStatsPath invoked:th:" + th);
        LOGGER.info("showStatsPath invoked:path:" + path);
        try{
            Maapi maapi;
            try {
                maapi = ResourceManager.getMaapiResource(this, Scope.INSTANCE);
            } catch (IllegalAccessException e) {
                throw new NedException(NedErrorCode.NED_INTERNAL_ERROR,
                                       "could not retrieve maapi", e);
            }
            LOGGER.info( this.toString()  + " Attaching to Maapi " + maapi);
            maapi.attach(th, 0);
            String pp = "/ncs:devices/device{" + device_id + "}" +
                "/live-status/";
            if (!maapi.exists(th, pp + "/stats")) {
                maapi.create(th, pp + "/stats");
            }
            if (!maapi.exists(th, pp + "/stats/receive")) {
                maapi.create(th, pp + "/stats/receive");
            }
            if (!maapi.exists(th, pp + "/stats/receive")) {
                maapi.create(th, pp + "/stats/transmit");
            }

            int receivedBytes =
                (Integer)exec("GetDeviceStats.getReceiveBytes",
                                        new Object[]{});
            maapi.setElem(th,new ConfUInt32(receivedBytes),pp
                      + "stats/receive/bytes");

            int receivedPackets =
                (Integer)exec("GetDeviceStats.getReceivePackets",
                                        new Object[]{});

            maapi.setElem(th,new ConfUInt32(receivedPackets),pp +
                      "stats/receive/packets");

            int receivedErrors =
                (Integer)exec("GetDeviceStats.getReceiveErrors",
                                        new Object[]{});

            maapi.setElem(th,new ConfUInt32(receivedErrors),pp +
                      "stats/receive/errors");


            int receivedDropped =
                (Integer)exec("GetDeviceStats.getReceiveDropped",
                                        new Object[]{});

            maapi.setElem(th,new ConfUInt32(receivedDropped),
                      pp + "stats/receive/dropped");

            int transmitBytes  =
                (Integer)exec("GetDeviceStats.getTransmitBytes",
                                        new Object[]{});

            maapi.setElem(th,new ConfUInt32(transmitBytes),pp +
                      "stats/transmit/bytes");

            int transmitPackets  =
                (Integer)exec("GetDeviceStats.getTransmitPackets",
                                        new Object[]{});

            maapi.setElem(th,new ConfUInt32(transmitPackets),
                      pp + "stats/transmit/packets");

            int transmitErrors  =
                (Integer)exec("GetDeviceStats.getTransmitErrors",
                                        new Object[]{});

            maapi.setElem(th,new ConfUInt32(transmitErrors),
                      pp + "stats/transmit/errors");

            int transmitDropped  =
                (Integer)exec("GetDeviceStats.getTransmitDropped",
                                        new Object[]{});

            maapi.setElem(th,new ConfUInt32(transmitDropped),
                      pp + "stats/transmit/dropped");

            maapi.detach(th);
        }catch(ConfException e){
            LOGGER.error("",e );
        }catch(XmlRpcException e){
            LOGGER.error("",e);
        }

         NedTTL ttl = new NedTTL(path,0);
         worker.showStatsPathResponse(new NedTTL[]{ttl});
    }

    @Override
    public boolean isAlive(NedWorker worker) {
        return connected;
    }

    public void reconnect(NedWorker worker) {
        // all capas and transmode already set in constructor
        // nothing needs to be done
    }

    public boolean isConnection(String deviceId,
                                InetAddress ip,
                                int port,
                                String luser,
                                boolean trace,
                                int connectTimeout, // msecs
                                int readTimeout,    // msecs
                                int writeTimeout) { // msecs
        return ((this.device_id.equals(device_id)) &&
                (this.ip.equals(ip)) &&
                (this.port == port) &&
                (this.luser.equals(luser)) &&
                (this.trace == trace) &&
                (this.connectTimeout == connectTimeout) &&
                (this.readTimeout == readTimeout) &&
                (this.writeTimeout == writeTimeout));
    }

    /*
     * very trivial command example in this example
     * It only returns a String result status OK
     *
     */
    public void command(NedWorker worker, String cmdname, ConfXMLParam[] p)
        throws NedException, IOException {

        LOGGER.info("XMLRPC ID:" + device_id);
        worker.commandResponse(new ConfXMLParam[]{
                new ConfXMLParamValue(new interfaces(),
                                      "result",
                                      new ConfBuf("OK"))
            });

    }

    /**
     * Establish a new connection to a device and send response to
     * NCS with information about the device.
     *
     * @param deviceId name of devide
     * @param ip address to connect to device
     * @param port port to connect to
     * @param proto ssh or telnet
     * @param luser name of local NCS user initiating this connection
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
                                    NedWorker worker ) {
        LOGGER.info("newConnection() <==");
        XMLRpcNed ned = null;

        try{
            ned = new XMLRpcNed(deviceId, ip, port, luser, trace,
                                connectTimeout, readTimeout, writeTimeout,
                                mux, worker,
                                wantReverse );
            LOGGER.info("NED invoking newConnection() ==> OK");
        }catch(XMLRpcNoConnectionException e){
            LOGGER.error("newConnection() ==> FAILURE!");
        }
        return ned;
    }

    /**
     * Make a new instance of Ned object without establishing a connection
     * towards the device. This instance will only be used to calculate
     * a dry-run native diff using prepareDry() call.
     *
     * @param deviceId name of device
     * @param mux
     * @param w
     *    The NedWorker instance currently responsible for driving the
     *    communication between NCS and the device. This NedWorker instance
     *    should be used when communicating with the NCS, ie for sending
     *    responses, errors, and trace messages.
     * @return the NED instance
     */
    public NedGenericBase initNoConnect(String deviceId,
                                    NedMux mux,
                                    NedWorker worker ) {
        LOGGER.info("initNoConnect() <==");
        XMLRpcNed ned = new XMLRpcNed(deviceId, mux, worker);
        LOGGER.info("initNoConnect() ==> OK");
        return ned;
    }

    class XMLRpcNoConnectionException extends Exception{

    }

    public void getTransId(NedWorker w) throws NedException, IOException {
        // TODO Auto-generated method stub

    }
}

