package com.example.qinqalarm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.SocketAddress;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.qinqalarm.namespaces.qinqAlarm;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfDatetime;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIdentityRef;
import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfList;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfObjectRef;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.ConfAttributeType;
import com.tailf.conf.ConfAttributeValue;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.navu.NavuException;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.alarmman.common.ManagedDevice;
import com.tailf.ncs.alarmman.common.ManagedObject;
import com.tailf.ncs.alarmman.common.PerceivedSeverity;
import com.tailf.ncs.alarmman.producer.AlarmSink;

// Parts from
//   examples.ncs/getting-started/developing-with-ncs/9-alarms/
//     packages/alarms/src/yang/submit-alarm.yang


public class ActionCb {

    private AlarmSink sink;
    private static Logger LOGGER  = LogManager.getLogger(ActionCb.class);


    private Maapi maapi = null;
    private final NcsMain main;

    public ActionCb(NcsMain main) {
        this.main = main;
    }

    //The init() method will be the first to be invoked by NCS Java VM
    //when this action has been called
    @ActionCallback(callPoint=qinqAlarm.actionpoint_generate_alarm,
                    callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {

        LOGGER.info("The method init() on AlarmActionSubmitter called");

        if(this.sink == null) this.sink = new AlarmSink();

        if(this.maapi==null) {
            try {
                maapi = new Maapi(main.getAddress());
                maapi.startUserSession(
                    "admin", "maapi", new String[] { "admin" });
             }
             catch (Exception e){
                LOGGER.error("Failed to create Maapi connection", e);
                throw new DpCallbackException(
                  "Failed to create Maapi conneciton in Action", e);
             }
        }
    }

    //The action() method will be called when a user issue the
    //request statement in CLI
    @ActionCallback(callPoint=qinqAlarm.actionpoint_generate_alarm,
                    callType=ActionCBType.ACTION)
    public ConfXMLParam[] action(DpActionTrans trans,
                                     ConfTag name,
                                     ConfObject[] kp,
                                     ConfXMLParam[] params)
        throws IOException, ConfException {

        LOGGER.info("The method action() on AlarmActionSubmitter called");


        String errPath = null;

        // Start a read transaction towards the running configuration.
        int th = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);

        try {
            LOGGER.info("Path: " + new ConfPath(kp));

            // Create an XML representation of the parameters from the cli,
            // this is just for illustration
            LOGGER.info("Values as XML \n" +
                        ConfXMLParam.toXML(params,
                                           qinqAlarm._qinq_,
                                           qinqAlarm.uri));

            // Retrieve the action parameters
            String alarmDevice = "";
            String alarmObject = "";
            ConfIdentityRef alarmType = null;
            String alarmSpecificProblem = "";
            String alarmText = "";
            PerceivedSeverity ps = null;

            List<ManagedObject> impactedObjects = null;


            // params is a flat representation of an XML tree
            // retrieve the values of the desired elements
            for (ConfXMLParam p: params) {
                LOGGER.info("hash: "+p.getTagHash()+
                         ", str: "+
                         qinqAlarm.hashToString(p.getTagHash()));

                switch (p.getTagHash()) {
                case qinqAlarm._device:
                    alarmDevice = p.getValue().toString();
                    break;

                case qinqAlarm._object:
                    alarmObject = p.getValue().toString();
                    break;

                case qinqAlarm._perceived_severity:
                    ps = PerceivedSeverity.valueOf
                        (((ConfEnumeration) p.getValue()).getOrdinalValue());
                    break;
                case qinqAlarm._alarm_type:
                    alarmType = (ConfIdentityRef) p.getValue();
                    break;
                case qinqAlarm._specific_problem:
                    alarmSpecificProblem = p.getValue().toString();
                    break;
                case qinqAlarm._alarm_text:
                    alarmText = p.getValue().toString();
                    break;

                case qinqAlarm._impacted_objects:
                    //Container object, nothing needed to be done here.
                  break;

                case qinqAlarm._root_cause_objects:
                    break;
                    //This parameter is ignored, but could be used if one wanted
                    //to separate the alarming object from the root cause object
                    //In this particular example the alarming object is
                    //considered to be the root cause object.

                case qinqAlarm._i_obj:
                    LOGGER.info("_i_obj");
                    //This parameter is ignored. Impacted objects are set based
                    //on the BACKPOINTER parameter (see below)
                    break;

                case qinqAlarm._rc_obj :
                    break;

                default:
                    throw new ConfException("Unknown parameter in action: " +
                        p.getTag());
                }
            }

            //Search for BACKPOINTERs indicating service usage.
            impactedObjects = findImpactedObjects(th, alarmObject);

            //Update the stats of the service with this,
            setQinqServiceStatus(main.getAddress(), ps, impactedObjects);

            ManagedDevice managedDevice = new ManagedDevice(alarmDevice);
            errPath = " : object : path=" + alarmObject;

            ManagedObject managedObject =
                new ManagedObject(new ConfPath(maapi, th, alarmObject));

            sink.submitAlarm(managedDevice,
                             managedObject,
                             alarmType,
                             new ConfBuf(alarmSpecificProblem),
                             ps,
                             alarmText,
                             impactedObjects,
                             null, /** No related alarms **/
                             null, /** No root cause objects **/
                             ConfDatetime.getConfDatetime());

            return getGenerateAlarmOutput(true, "OK", null);
        }catch(NavuException e) {
            LOGGER.error("Encountered NavuException from AlarmActionSubmitter",
                    e);
            return getGenerateAlarmOutput(false, e.toString(), errPath);
        }catch(ConfException e) {
            LOGGER.error("Encountered ConfException from AlarmActionSubmitter",
                    e);
            return getGenerateAlarmOutput(false, e.toString(), errPath);
        }catch(IOException e) {
            LOGGER.error("",e);
            return getGenerateAlarmOutput(false, e.toString(), errPath);
        }
        finally {
          maapi.finishTrans(th);
        }
    }

    //Given an object on a device. Try to find any objects parenting
    //this object that contains a backpointer attribute.
    //This would indicate that the object is created by a service
    //which then would be impacted by this alarm.
    //This can be done in more ways, this particular function looks at
    //parenting objects to the alarming object for backpointers, but it
    //may also be valid to browse through child objects since they may
    //be affected by such an alarm.
    private List<ManagedObject> findImpactedObjects(int th, String path)
        throws ConfException, IOException
    {

      LOGGER.info("findImpactedObjects for: " + path);
        List<ManagedObject> objs = new ArrayList<ManagedObject>();

        try {
            ExtConfPath p = new ExtConfPath(this.maapi, th, path);

          while(!(p.topTag().equals("config")
                  || p.topTag().equals("ncs:config"))){
            //Check for backpointer
            LOGGER.info("Checking path: " + p.toString());

            ConfAttributeValue[] vals = this.maapi.getAttrs(th,
             new ConfAttributeType[] {ConfAttributeType.BACKPOINTER},
                                                            p.toString());

            LOGGER.info("Number of Backpointers: " + vals.length);
            for(ConfAttributeValue v: vals){
              ConfList refs = (ConfList)v.getAttributeValue();
              for (ConfObject co:refs.elements()){
                ManagedObject mo = new ManagedObject((ConfObjectRef)co);
                objs.add(mo);
              }
            }

            p = p.parent();
          }
        }
        catch (IOException ioe){
          LOGGER.warn("Couldn't access Maapi, "
                      +" aborting mapping attempt of impacted objects");
        }
        catch (ConfException ce){
          ce.printStackTrace();
          LOGGER.warn("Failed to retrieve Attributes via Maapi");
        }
        return objs;
    }


    private ConfXMLParam[] getGenerateAlarmOutput(boolean result, String info,
            String additionalInfo) {

        if (additionalInfo != null) {
            info = info + additionalInfo;
        }

        ConfXMLParam[] returnValues  =
                new ConfXMLParam[]{
                new ConfXMLParamValue(qinqAlarm.hash,
                                      qinqAlarm._result,
                                      new ConfBool(result)),
                new ConfXMLParamValue(qinqAlarm.hash,
                                      qinqAlarm._info,
                                      new ConfBuf(info))

            };

        return returnValues;
    }


    private void setQinqServiceStatus(SocketAddress address,
            PerceivedSeverity ps, List<ManagedObject> impactedObjects)
            throws IOException, ConfException {

        if (impactedObjects == null) {
            return;
        }

        for (ManagedObject mo : impactedObjects) {
            setOneQinqServiceStatus(address, ps, mo);
        }

    }

    private void setOneQinqServiceStatus(SocketAddress address,
            PerceivedSeverity ps, ManagedObject mo)
            throws IOException, ConfException {

        String path = mo.getAsConfValue().toString();


        LOGGER.info("setOneQinqServiceStatus : ps=" + ps + " :  path=" + path);

        Pattern pattern = Pattern.compile(
            "\\/ncs\\:services\\/ncs\\:service\\[ncs\\:object\\-id='(.+?)'\\]");
        Matcher matcher = pattern.matcher(path);

        if (matcher.find()) {
            String serviceName = matcher.group(1);
            SessionWriterStatus.QinqServiceStatus status =
                SessionWriterStatus.QinqServiceStatus.alarm;

            LOGGER.info("setOneQinqServiceStatus : MATCH : service=" +
                    serviceName);

            if (ps == PerceivedSeverity.CLEARED) {
                status = SessionWriterStatus.QinqServiceStatus.cleared;
            }

            SessionWriterStatus sws = new SessionWriterStatus();
            sws.setQinqServiceStatus(address, serviceName, status);


        } else {
            LOGGER.info("setOneQinqServiceStatus : NO MATCH");
        }

    }

}
