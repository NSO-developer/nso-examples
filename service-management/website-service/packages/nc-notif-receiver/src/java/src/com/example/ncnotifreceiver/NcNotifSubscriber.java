package com.example.ncnotifreceiver;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;

import com.tailf.conf.ConfValue;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfDatetime;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIdentityRef;
import com.tailf.conf.ConfPath;

import com.tailf.navu.NavuContainer;

import com.tailf.ncs.ns.Ncs;

import com.tailf.ncs.ApplicationComponent;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.NavuEventCallback;
import com.tailf.ncs.NavuEventHandler;
import com.tailf.ncs.NcsException;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Scope;

import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;

import com.tailf.ncs.alarmman.common.Alarm;
import com.tailf.ncs.alarmman.common.ManagedObject;
import com.tailf.ncs.alarmman.common.PerceivedSeverity;
import com.tailf.ncs.alarmman.common.ManagedDevice;
import com.tailf.ncs.alarmman.producer.AlarmSink;
import com.tailf.ncs.alarmman.consumer.AlarmSource;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.websiteservice.webserver.namespaces.*;
import com.example.websiteservice.websiteservice.namespaces.*;


//     To make this code trigger we must tell NCS to actually subscribe to
//     netconf notifications. In this example the 3 www1-3 devices actually
//     send netconf notifs northbound.
//     To see that, do i.e

// admin@zoe> show status devices device www1 notifications stream
// stream NETCONF {
//     description    "default NETCONF event stream";
//     replay-support false;
// }
// stream interface {
//     description              "Example notifications";
//     replay-support           true;
//     replay-log-creation-time 2012-03-02T13:15:19Z;
//     replay-log-aged-time     2012-03-02T13:37:22.48864Z;
// }


//     To tell NCS to start subscribing, do
//     > configure
//     > set devices device www2-3 notifications \
//           subscription if stream interface
//     > commit

//     It's also the case that SNMP traps get sent northbound in this example,
//     see snmp.log

/**
 * This class implements the AplicationComponent which defines
 * the init() , run() and finish() methods to be overwritten.
 * The NCS JVM calls these 3 methods in the the order stated.
 *
 * This class implements the NavuEventCallback which defines
 * the method notifRecieved(NavuContainer event). The NavuEventHandler needs
 * to be started in order to call the registered NavuEventCallback's
 * when notification is received.
 *
 * Before we start the NavuEventHandler and start to receive notifications
 * we configure a logger that will send logs msgs to a different log file
 * to logs/REC-NOTIFS-<date-today>.log so we don't flood the ncs-java.log
 * with log msgs that is related to notifications.
 *
 * In this example when the callback receive a notification it
 * creates an alarm and submit it to the alarm sink which in turns
 * send an alarm to NCS. The alarm should be seen in the same
 * log file as the notifications.
 *
 */
public class NcNotifSubscriber implements ApplicationComponent ,
                                          NavuEventCallback
{
    private static Logger log =
        LogManager.getLogger( NcNotifSubscriber.class );

    private static Logger ncReceivedLog =
        LogManager.getLogger ( "NC-Notif-Reciver" );

    private final NcsMain main;

    public NcNotifSubscriber(NcsMain main) {
        this.main = main;
    }

    //As default we are listening to all the device names.
    //private final String deviceName = "*";
    //As default we are listening on all subscription names.
    //private final String subscriptionName = "*";

    @Resource(type=ResourceType.MAAPI, qualifier="read-maapi" ,
              scope=Scope.INSTANCE)
    private Maapi maapi;

    private String logString;
    //As events come in NavuHandler is responsible for
    //dispatching to registered callbacks.
    private NavuEventHandler navuEventHandler;
    private AlarmSink sink;

    /**
     * This method is called by the NCS JVM before the run()
     * method. The init() method is called by a helper thread
     * "init".
     */
    public void init ()  {
        log.info (" init() => ");
        try {

            navuEventHandler = new NavuEventHandler(
                    main.getAddress(), this.getClass().getSimpleName());


            String[] props = readSubscriptionProps ( );

            log.info("Registering callback for notification ["
                 + props[0] + ":" + props[1] + "]");

            navuEventHandler
                .registerInterfaceCallback( props[0], props[1] ,
                                            this );

            navuEventHandler.start();
            sink = new AlarmSink();

            log.info (" init() => ok");
        } catch ( IOException e ) {
            log.info("" , e );
        } catch ( ConfException e ) {
            log.info("", e );
        }
    }

    private String[] readSubscriptionProps ( ) throws ConfException ,
                                                      IOException
    {
        String deviceName = "*";
        String subscriptionName = "*";

        maapi.startUserSession("admin", "maapi", new String[] {"admin"});

        int th = maapi.startTrans ( Conf.DB_RUNNING,
                                    Conf.MODE_READ );

        ConfPath devSubPath =
            new ConfPath ("/ncprops:nc-common-properties/device-subscription");


        ConfValue devName =  maapi.getElem (th, devSubPath );

        if ( devName != null ) {
            ConfEnumeration devEnum =
                (ConfEnumeration)devName;

            deviceName = ConfEnumeration
                .getLabelByEnum ( devSubPath , devEnum );

            if ( deviceName.equals("ALL") )
                deviceName = "*";
        }

        ConfPath subNamePath =
            new ConfPath ("/ncprops:nc-common-properties/stream-name");

        ConfValue subName  =   maapi.getElem (th , subNamePath );

        if ( subName != null )
            subscriptionName  = subName.toString();

        maapi.endUserSession();

        return new String [] { deviceName , subscriptionName };
    }

    /**
     * This method is called by the NCS JVM after the init()
     * method has been called. The run() method is called by a "run" thread
     * which is not the same thread as "init"-thread.
     */
    public void run ( ) {
        log.info(" run() =>");
        //Grab the alarm source for listening for incoming alarms
        //no AlarmSourceCentral needs to be started , there is
        // already a AlarmSourceCental started in NCS JVM already.
        try (AlarmSource alarmSource = new AlarmSource()) {
            Alarm recivedAlarm = null;
            try {
                do {
                    recivedAlarm = alarmSource.takeAlarm() ;

                    ncReceivedLog.info ("Got Alarm " + recivedAlarm );
                } while ( !recivedAlarm.isLastAlarm () );
            } catch ( InterruptedException e ) {
                // when redeploying packages AlarmSourceCentral
                // calls interrupt on all the AlarmSources so
                // we must be ready for it
                log.info("Got Interrupted! AlarmSource Stop Listening.");
                ncReceivedLog.warn ( " == No more alarms received == ");
            } catch ( Exception e ) {
                log.error("", e );
            }
        }
        log.info(" run() => ok");
    }

    public void finish () {
        log.info(" finish() =>");
        ResourceManager rsMgr =
            ResourceManager.getResourceManager();

        try {
            //Relese all the Maapi references
            // injections made by the ResourceManager
            rsMgr.unregisterResources( this );
        } catch ( Exception e ) {
            log.error("",e);
        }
        //Stop the NavuEventHandler
        navuEventHandler.stop();

        log.info(" finish() => ok");
    }



    /**
     * The implementation of the NavuCallback interface. When
     * the NavuEvent handler receives a notification the
     * method gets invoked with the NavuContainer event
     * which represents the path:
     *
     * /ncs:devices/device{device}/notifications
     * /received-notifications/notification{event-time sequence-no}
     *
     * @param event The NavuContainer ( or list entry ) of the
     * receiving netconf notification.
     *
     */
    public void notifReceived(NavuContainer event) throws NcsException
    {
        try {
            ncReceivedLog
                .info("Got event with keypath : " + event.getKeyPath() );

            //retrieve the data container
            NavuContainer dataContainer = event.container(Ncs._data_);

            //check if either the container linkUp or linkDown
            //was created which means that either the linkUp or linkDown
            // was received as the data from the device.
            if (dataContainer.namespace(notif.id)
                    .container(notif._linkUp_).exists() ||
                dataContainer.namespace(notif.id)
                        .container(notif._linkDown_).exists()) {

                //Retrieve the managed device from which the
                //notification originated.
                ManagedDevice mdev =
                    getManagedDevice ( event );

                ManagedObject ifName;
                ConfIdentityRef alarmType;

                // Retrieve the interface name and the alarmtype
                // depending on the received notification
                if (dataContainer.namespace(notif.id)
                     .container(notif._linkUp_).exists()) {
                    ifName = getManagedObject(dataContainer
                            .namespace(notif.id).container(notif._linkUp_));

                    alarmType = getAlarmTypeLinkUp ();
                } else {
                    ifName = getManagedObject (
                            dataContainer.container(notif._linkDown_) );

                    alarmType = getAlarmTypeLinkDown ();
                }

                //Submit an alarm to the alarm sink
                submitAlarm ( mdev , ifName , alarmType );

            }

        } catch (Exception e) {
            log.error("", e);
        }
    }

    /**
     * Convenient method for submit an alarm.
     *
     * @param dev The managed device
     * @param managedObject The managed object
     * @param alarmType The alarmtype to be submitted to NCS
     *
     */
    public void submitAlarm ( ManagedDevice dev ,
                              ManagedObject managedObject ,
                              ConfIdentityRef alarmType ) throws Exception
    {
        sink.submitAlarm(dev, managedObject, alarmType,
                         new ConfBuf("none specific"),
                         PerceivedSeverity.MINOR,
                         "Hmmmm",
                         null, null, null,
                         ConfDatetime.getConfDatetime() );

    }

    /**
     *  Convenient methods to retrieve the managed device from which
     *  the notification originated.
     *
     * @param event
     *
     * @return The managed device from which the event originated.
     *
     */
    public ManagedDevice getManagedDevice ( NavuContainer event )
        throws Exception
    {

        NavuContainer dev = (NavuContainer)event.getParent().getParent().
            getParent().getParent();

        String devname = dev.leaf(Ncs._name_).value().toString();
        ManagedDevice mdev =
            new ManagedDevice(dev.leaf(Ncs._name_).
                              value());
        return mdev;
    }


    public ManagedObject getManagedObject ( NavuContainer link )
        throws Exception {

        return new ManagedObject ( link.
                                   leaf(notif._ifName_).value() );

    }

    public ConfIdentityRef getAlarmTypeLinkUp ( ) {
        return new ConfIdentityRef(new myAlarms().hash(),
                                   myAlarms._link_up);
    }

    public ConfIdentityRef getAlarmTypeLinkDown () {
        return new ConfIdentityRef(new myAlarms().hash(),
                                   myAlarms._link_down );
    }
}
