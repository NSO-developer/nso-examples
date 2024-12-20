/*    -*- Java -*-
 *
 *  $Id$
 *
 */
package com.example.snmpnotificationreceiver;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.smi.VariableBinding;

import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfDatetime;
import com.tailf.conf.ConfIdentityRef;
import com.tailf.ncs.alarmman.common.Alarm;
import com.tailf.ncs.alarmman.common.ManagedDevice;
import com.tailf.ncs.alarmman.common.ManagedObject;
import com.tailf.ncs.alarmman.common.PerceivedSeverity;
import com.tailf.ncs.alarmman.producer.AlarmSink;
import com.tailf.ncs.ns.NcsAlarms;
import com.tailf.ncs.snmp.snmp4j.EventContext;
import com.tailf.ncs.snmp.snmp4j.HandlerResponse;
import com.tailf.ncs.snmp.snmp4j.NotificationHandler;

/**
 * This class is an example of a NotificationHandler used in the NCS Snmp4j
 * NotificationReceiver. As such it serves as a starting point for developing
 * SNMP Notification handlers. It is expected to be copy/pasted instead of
 * inherited (which would serve no purpose).
 *
 * There is a main method in App.java that illustrates how the
 * NotificationReceiver is deployed and launched. The same comment holds for
 * the main method, i.e., add relevant method calls to the Ncs Service manager
 * main (where NcsServiceMux is launched).
 */
public class ExampleHandler implements NotificationHandler {

    private static Logger LOGGER = LogManager.getLogger(ExampleHandler.class);

    /**
     * This callback method is called when a notification is received from
     * Snmp4j.
     *
     * @param event
     *            a CommandResponderEvent, see Snmp4j javadoc for details
     * @param opaque
     *            any object passed in register()
     */
    public HandlerResponse
        processPdu(EventContext context,
                   CommandResponderEvent event,
                   Object opaque)
    throws Exception {

        String alarmText = "test alarm";

        PDU pdu = event.getPDU();
        for (int i = 0; i < pdu.size(); i++) {
            VariableBinding vb = pdu.get(i);
            LOGGER.info(vb.toString());

            if (vb.getOid().toString().equals("1.3.6.1.6.3.1.1.4.1.0")) {
                String linkStatus = vb.getVariable().toString();
                if ("1.3.6.1.6.3.1.1.5.3".equals(linkStatus)) {
                    alarmText = "IF-MIB::linkDown";
                }
            }
        }

        String device = context.getDeviceName();
        String managedObject = "/devices/device{"+device+"}";
        ConfIdentityRef alarmType =
            new ConfIdentityRef(new NcsAlarms().hash(),
                                NcsAlarms._connection_failure);
        PerceivedSeverity severity = PerceivedSeverity.MAJOR;
        ConfDatetime timeStamp = ConfDatetime.getConfDatetime();

        Alarm al = new Alarm(new ManagedDevice(device),
                             new ManagedObject(managedObject),
                             alarmType,
                             severity,
                              new ConfBuf(alarmText),
                              null,
                              null,
                              null,
                              timeStamp);

        AlarmSink sink = new AlarmSink();
        sink.submitAlarm(al);

        return HandlerResponse.CONTINUE;
    }
}
