SNMP Notification Receiver Example
==================================

This example illustrates an SNMP notification receiver package that uses Java
code to raise an alarm whenever it receives an SNMP notification.

The NSO instance runs from this directory and contains the following files:

    ncs-cdb/                  - NSO's database directory
    ncs-cdb/ncs_init.xml      - Initial configuration data for this example
    logs/                     - Directory for log files
    packages/snmp-notif-recv  - Package containing an application component
                                which constitutes the example code
    state/                    - Directory where NSO stores state information

After `make all`, the following files are created:

    packages/snmp-notif-recv/private-jar - the Java archive ex-notif-rec.jar
                                           containing the compiled example code

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Running the Example
-------------------

Build the example:

    make all

Start NSO:

    make start

NSO, including the NSO Java VM, is started.

Start a NSO CLI:

    make cli

Check the current alarms:

    > show alarms alarm-list

The list should be empty.

The SNMP notification receiver is configured to listen at UDP port `8000`:

  > show configuration snmp-notification-receiver
  enabled;
  listen {
      udp 0.0.0.0 8000;
  }
  > exit

The notification receiver will listen to notifications from any managed device
configured for NSO. If the notification sender address differs from the IP
address used for management, the address can be configured explicitly with the
`/ncs/managed-device/snmp-notification-address` leaf.  Next, simulate that a
managed device sends an SNMP notification:

    ./sendnotif.sh 127.0.0.1 8000 1

The `snmp-notif-recv` package Java handler receives this notification and
generates a random alarm.  Note that this requires us to have a managed device
at address `127.0.0.1`. If not, the notification will be dropped.

Check the alarm list from the NSO CLI:

    make cli
    > show alarms alarm-list

This list should now contain an alarm.

Tools
-----

Note that we could have used a netsim device and sent SNMP traps and informs
from the device, but to simplify the example, we used the `sendnotif.sh`
script.

The `sendnotif.sh` script uses the Snmp4j SnmpRequest console to generate and
send traps. The sent trap will be of type `IF-MIB::linkDown`.

The usage of the script is as follows:

    sh sendnotif.sh <ip> <port> <data>

as in:

    sh sendnotif.sh 127.0.0.1 8000 1

It is also possible to use the Net-SNMP `snmptrap` application to send
notifications. Examples:

    export SNMP_PERSISTENT_FILE=/dev/null

Send a v1 trap:

    snmptrap -v1 -c foo 127.0.0.1:8000 1.3.6.1.4.1.3.1.1 10.0.0.1 1 1 100

Send a v2c notification:

    snmptrap -v2c -c foo 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1

Send an `authPriv` v3 notification:

    snmptrap -v3 -u ncs -l authPriv -a SHA -A authpass -x aes -X privpass\
        127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1

Send a v2c inform using the Net-SNMP `snmpinform` application:

    snmpinform -v2c -c foo 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1

Send an `authPriv` v3 inform:

    snmpinform -v3 -u ncs -l authPriv -a SHA -A authpass -x aes -X privpass\
        127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1

Check the alarm list from the NSO CLI:

    make cli
    > show alarms alarm-list

Five more alarms are in the list with the "test alarm" alarm text.

Mapping SNMP Notifications (Traps) to NSO Alarms
------------------------------------------------

NSO comes with a pre-defined Alarm Manager. The Alarm Manager can be fed with
alarms from various sources including SNMP traps. See the NSO User Guide for
details on the Alarm Manager. It is important to realize that there is no
one-to-one mapping between traps and alarms. Not all traps are alarms, and the
trap information (var-binds), in most cases, needs enrichment to present useful
alarms to the users. This is done in Java.  See
`packages/snmp-notif-recv/src/java/src/com/example/snmpnotificationreceiver`.

NSO maps individual notifications to state changes on alarms. The device, an
object within the device, and an alarm type identify an alarm. NSO uses
hierarchical YANG identities to identify alarm types.

Creating these three keys is the first task of the Java code:
    Alarm al = new Alarm(new ManagedDevice(...),
                         new ManagedObject(...),
                         new ConfIdentityRef(...)
                         ...)

The mapping code also needs to define alarm state change information like
severity and alarm text:

    Alarm al = new Alarm(...
                         inc.status_changed.severity,
                         inc.status_changed.alarmText,
                        ...)

Finally, send the alarm state change to the NSO Alarm Manager:

    AlarmSink = new AlarmSink()
    sink.submitAlarm(al)

Cleanup
-------

When you finish this example, make sure all daemons are stopped. Stop NSO:

    make stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Development Guide: SNMP Notification Receiver
+ The `demo.sh` script
