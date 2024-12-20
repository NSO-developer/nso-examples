package com.example.alarm.producer;

import java.io.IOException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.alarm.namespaces.SubmitAlarm;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfDatetime;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIdentityRef;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.navu.NavuException;
import com.tailf.ncs.alarmman.common.ManagedDevice;
import com.tailf.ncs.alarmman.common.ManagedObject;
import com.tailf.ncs.alarmman.common.PerceivedSeverity;
import com.tailf.ncs.alarmman.producer.AlarmSink;

public class AlarmActionSubmitter {
    private AlarmSink sink;
    private static Logger log  =
        LogManager.getLogger(AlarmActionSubmitter.class);

    //The init() method will be the first to be invoked by NCS Java VM
    //when this action has been called
    @ActionCallback(callPoint=SubmitAlarm.actionpoint_generate_alarm,
                    callType=ActionCBType.INIT)
        public void init(DpActionTrans trans) throws DpCallbackException {

        log.info("The method init() on AlarmActionSubmitter called");

        if(this.sink == null) this.sink = new AlarmSink();
    }

    //The action() method will be called when a user issue the
    //request statement in CLI
    @ActionCallback(callPoint=SubmitAlarm.actionpoint_generate_alarm,
                    callType=ActionCBType.ACTION)
        public ConfXMLParam[] action(DpActionTrans trans,
                                     ConfTag name,
                                     ConfObject[] kp,
                                     ConfXMLParam[] params)
        throws DpCallbackException {

        log.info("The method action() on AlarmActionSubmitter called");

        try {
            log.info("Path: " + new ConfPath(kp));

            // Create an XML representation of the parameters from the cli,
            // this is just for illustration
            log.info("Values as XML \n" +
                        ConfXMLParam.toXML(params,
                                           SubmitAlarm._alarm_ ,
                                           SubmitAlarm.uri));

            // Retrieve the action parameters
            String alarmDevice = "";
            String alarmObject = "";
            ConfIdentityRef alarmType = null;
            String alarmSpecificProblem = "";
            String alarmText = "";
            PerceivedSeverity ps = null;

            // params is a flat representation of an XML tree
            // retrieve the values of the desired elements
            for (ConfXMLParam p: params) {
                log.info("hash: "+p.getTagHash()+
                         ", str: "+
                         SubmitAlarm.hashToString(p.getTagHash()));
                switch (p.getTagHash()) {
                case SubmitAlarm._alarm:
                    // <alarm> and </alarm>, ignore this
                    break;
                case SubmitAlarm._device:
                    alarmDevice = p.getValue().toString();
                    break;
                case SubmitAlarm._object:
                    alarmObject = p.getValue().toString();
                    break;
                case SubmitAlarm._perceived_severity:
                    ps = PerceivedSeverity.valueOf
                        (((ConfEnumeration) p.getValue()).getOrdinalValue());
                    break;
                case SubmitAlarm._alarm_type:
                    alarmType = (ConfIdentityRef) p.getValue();
                    break;
                case SubmitAlarm._specific_problem:
                    alarmSpecificProblem = p.getValue().toString();
                    break;
                case SubmitAlarm._alarm_text:
                    alarmText = p.getValue().toString();
                    break;
                default:
                    throw new ConfException("Unknown parameter in action: " +
                        p.getTag());
                }
            }

            ManagedDevice managedDevice = new ManagedDevice(alarmDevice);

            // Path to the managed object, make it an object to reference
            // instance data
            String mo = "/ncs:devices/device[name='"+alarmDevice+"']" +
                "/config/r:sys/interfaces/interface[name='"+alarmObject+"']";
            ManagedObject managedObject = new ManagedObject(mo);

            sink.submitAlarm(managedDevice,
                             managedObject,
                             alarmType,
                             new ConfBuf(alarmSpecificProblem),
                             ps,
                             alarmText,
                             null, /** No impacted objects **/
                             null, /** No related alarms **/
                             null, /** No root cause objects **/
                             ConfDatetime.getConfDatetime());

            // Construct a successful result back to NCS
            ConfXMLParam[]   result =
                new ConfXMLParam[] {
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._result,
                                      new ConfBool(true)),
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._info,
                                      new ConfBuf("OK"))
            };

            return result;

        } catch(NavuException e) {
            log.error("Encountered NavuException from AlarmActionSubmitter",
                      e);
            ConfXMLParam[] returnValues  =
                new ConfXMLParam[]{
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._result,
                                      new ConfBool(false)),
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._info,
                                      new ConfBuf(e.toString()))
            };
            return returnValues;
        } catch(ConfException e) {
            log.error("Encountered ConfException from AlarmActionSubmitter",
                         e);
            ConfXMLParam[] returnValues  =
                new ConfXMLParam[]{
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._result,
                                      new ConfBool(false)),
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._info,
                                      new ConfBuf(e.getMessage()))
            };
            return returnValues;
        } catch(IOException e){
            log.error("",e);
            ConfXMLParam[] returnValues  =
                new ConfXMLParam[]{
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._result,
                                      new ConfBool(false)),
                new ConfXMLParamValue(SubmitAlarm.hash,
                                      SubmitAlarm._info,
                                      new ConfBuf(e.getMessage()))

            };

            return returnValues;
        }
    }
}
