package com.example.websiteservice.websiteservice;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

import com.example.websiteservice.websiteservice.namespaces.myAlarms;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfObjectRef;
import com.tailf.conf.ConfDatetime;
import com.tailf.conf.ConfIdentityRef;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.maapi.Maapi;
import com.tailf.ncs.alarmman.common.ManagedDevice;
import com.tailf.ncs.alarmman.common.ManagedObject;
import com.tailf.ncs.alarmman.common.PerceivedSeverity;
import com.tailf.ncs.alarmman.producer.AlarmSink;
import com.tailf.ncs.ns.NcsAlarms;

/**
 * The example will show how to create alarms with NAVU. Run this simple
 * java class with
 *
 * > ant alarm
 *
 * This code will simulate the generation of alarms in the alarm list at
 * /al:alarms/alarm-list/alarm. The unique alarm is identified by
 * the triple (managed-device,type,managed-object). The code will randomly
 * select a managed-device from (lb,www{1-3}) and managed-object from
 * ("riba","ruba","ryba","roba")
 * and all loaded types includning our created user defined web-server-on-fire.
 *
 * To see the list in ncs run commmand in CLI
 *
 * > show alarms alarm-list alarm
 *
 * The alarm list will initial be empty so the first times this code will run
 * it will generate some alarms. At time it will generate collisions.
 * The collision will simulate that a specific alarm with the triple
 * (managed-device,type,managed-object) has it state changed.
 *
 * To se the state changed e.g status-change list in
 * /al:alarm/alarm-list/alarm/status-change run in CLI:
 *
 * > show alarms alarm-list alarm 1 status-change
 *
 * where the number 1 is the alarm-id of the alarm with the triple
 *  (managed-device,type,managed-object). Of course you can change the
 * integer 1 with some other alarm-id that you wish to view the status-change
 * for.
 *
 * In this example we will create a NavuContainer with a connection to cdb
 * so that it can make appropriate read and writes to our operation data.
 */

public class WebserverOnFire {



    public static void main( String[] args ) throws Exception {

        try {
            Maapi maapi = new Maapi(
                    new InetSocketAddress("localhost", Conf.NCS_PORT));

            //Create a new Simulated incident ...
            Incident inc = Incident.generate();

            AlarmSink sink = new AlarmSink(maapi);

            sink.submitAlarm(
                    new ManagedDevice(inc.server),
                    new ManagedObject(inc.managedobj),
                    new ConfIdentityRef(inc.type.uri, inc.type.nsName),
                    new ConfBuf("Burning on site"),
                    inc.status_changed.severity,
                    inc.status_changed.alarmText,
                    null,
                    null,
                    null,
                    inc.when);

        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }




    /* The incident class just need for this simulation example */
    static class Incident{
        String server;
        String managedobj;
        AlarmType type;
        ConfDatetime when;
        StatusChangeEntry status_changed;

        ConfKey key(){
            ConfKey k = null;
            try{
                 k = new ConfKey(new ConfObject[]{
                    new ConfBuf(server),
                    new ConfIdentityRef(type.uri,
                                        type.nsName),
                    new ConfObjectRef(managedobj)
                });
            }catch(ConfException e){
                e.printStackTrace();
            }

            return k;
        }


        static List<String> servers =
            Arrays.asList("lb0","www1","www2","www3");
        static List<String> managedobjs =
            Arrays.asList("/ncs:devices/device[name='lb0']",
                          "/ncs:devices/device[name='www1']",
                          "/ncs:devices/device[name='www2']",
                          "/ncs:devices/device[name='www3']");

        static AlarmType alarmtypes[]  = {
            new Incident.AlarmType(new NcsAlarms().hash(),
                                   NcsAlarms._connection_failure),
            new Incident.AlarmType(new NcsAlarms().hash(),
                                   NcsAlarms._revision_error) ,
            new Incident.AlarmType(new NcsAlarms().hash(),
                                   NcsAlarms._out_of_sync) ,
            new Incident.AlarmType(new NcsAlarms().hash(),
                                   NcsAlarms._out_of_sync),
            new Incident.AlarmType( new myAlarms().hash(),
                                    myAlarms._webserver_on_fire)};


        static PerceivedSeverity severitys[] = {PerceivedSeverity.CLEARED,
            PerceivedSeverity.INDETERMINATE,
            PerceivedSeverity.MINOR,
            PerceivedSeverity.WARNING,
            PerceivedSeverity.MAJOR,
            PerceivedSeverity.CRITICAL};

        static String messages[] = {"ohh my good..",
                                    "holy balony..",
                                    "whats up doc..",
                                    "ohh no",
                                    "please no.",
                                    "mamma mia.."};


        /*
         *  Simulates a new generation of a incident.
         *
         */
        static Incident generate() {

            Incident incident = new Incident();

            Random rand = new Random();
            int index  = rand.nextInt(3);
            incident.server = servers.get(index);
            incident.managedobj =
                managedobjs.get(index);

            incident.type = alarmtypes[rand.nextInt(alarmtypes.length-1)];
            incident.when = ConfDatetime.getConfDatetime();
            incident.status_changed = Incident.getStatusChangeEntry();

            return incident;

        }

        /*
         *  Simulates a new generation of a status-changed incident.
         *
         */
        static StatusChangeEntry getStatusChangeEntry() {

            StatusChangeEntry entry = new StatusChangeEntry();
            Random rand = new Random();
            entry.severity = severitys[rand.nextInt(severitys.length-1)];
            entry.alarmText  = messages[rand.nextInt(messages.length-1)];
            return entry;
        }

        //Just for holding some values
        static class StatusChangeEntry{
            PerceivedSeverity     severity;
            String                     alarmText;
        }

        //Just for holding some values
        static class AlarmType{
            AlarmType(Integer uri,Integer nsName) {
                this.uri = uri; this.nsName = nsName;
            };
            Integer uri;
            Integer nsName;
        }
    }
}
