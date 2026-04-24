NSO SNMP Alarm MIB
==================

This example shows how to integrate the NSO northbound SNMP alarm agent with an
SNMP-based alarm management system. It also introduces how to use Net-SNMP
tools to inspect the alarm interface. Note, for SNMP trap southbound
integration to NSO, see the `examples.ncs/snmp-notification-receiver` example.

Overview
--------

NSO contains an alarm manager. The Alarm Manager manages the life cyle of
alarms, such as alarm raise, alarm clear, alarm acknowledgment, etc. The source
of alarms can be any NSO package. NSO also generates system-related alarms.

The alarms can be viewed and manipulated over any northbound interface like
the NSO CLI or NETCONF. NSO also generates NETCONF notifications for all alarm
changes.

To ease integration towards SNMP-based alarm monitoring systems, NSO ships a
dedicated SNMP alarm MIB. It contains a table of all active alarms and SNMP
notifications for alarm changes. The NSO MIBs are located under
`$NCS_DIR/src/ncs/snmp`. The rest of this example will show how to use the SNMP
alarm interface.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    ./demo.sh

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Setup the `website-server` example simulated network, build the packages, and
start the website-server example netsim network and NSO:

    make -C ../../service-management/website-service all start

See the `website-server` example README for additional details.

Set environment for Net-SNMP:

    export MIBDIRS=$NCS_DIR/src/ncs/snmp/mibs
    export MIBS=TAILF-TOP-MIB:TAILF-ALARM-MIB

Start the NSO CLI:

    ncs_cli --user=admin
    > request devices sync-from
    > configure
    % show snmp agent
    enabled;
    ip               0.0.0.0;
    udp-port         4000;
    version {
            v3;
    }
    ...

The NSO northbound agent is set up by default to support read operations. The
agent listens to UDP port 4000, configurable from the `ncs.conf` file. This
example uses the built-in SNMPv3 user `initial` with SHA authentication and
AES privacy.

Check the USM and VACM settings with the following:

    > show snmp usm
    > show snmp vacm

At the shell prompt, verify that you can view the alarm list:

    snmpwalk -v3 -u initial -l authPriv -a SHA -A GoTellMom \
      -x AES -X GoTellMom localhost:4000 enterprises tfAlarmTable
    TAILF-ALARM-MIB::tfAlarmNumber.0 = Gauge32: 0

As seen above (`tfAlarmNumber.0 = 0`), there are no alarms in NSO.

Start a trap receiver to listen for NSO alarms:

    snmptrapd -C -c ./snmptrapd.conf -Lf traplog.txt -p trapd.txt \
      localhost:27777

The example ships with a `snmptrapd.conf` file that configures the same NSO
engine ID and SNMPv3 `authPriv` user (`initial`) as the northbound agent. The
trap receiver will listen on UDP port `27777`, log notifications to
`traplog.txt`, and print the trapd PID to the `trapd.txt` file.

The trap receiver part of this demo requires a recent Net-SNMP `snmptrapd`
that supports the `authUser` directive in `snmptrapd.conf`. If `snmptrapd`
prints `Unknown token: authUser`, you are likely running an older binary that
does not support the configuration used by this example.

Generate Some Alarms
--------------------

The simplest way is to stop some devices and then ask NSO to connect to them:

    ncs-netsim stop lb0
    ncs-netsim stop www0
    ncs_cli --user=admin
    > request devices connect
    connect-result {
      device lb0
      result false
      info Failed to connect to device lb0: connection refused
    }
    ...

Show the alarm list:

    > show alarms
    alarms summary indeterminates 0
    alarms summary criticals 0
    alarms summary majors 2
    alarms summary minors 0
    alarms summary warnings 0
    alarms alarm-list number-of-alarms 2
    alarms alarm-list last-changed 2012-09-10T09:55:09.238225+00:00
    alarms alarm-list alarm lb0 connection-failure \
    "/devices/device[name=\"lb0\"]" ""

You may prefer the layout of:

    > show status alarms
    > exit

View that alarm list in NSO over SNMP:

    snmpwalk -v3 -u initial -l authPriv -a SHA -A GoTellMom \
      -x AES -X GoTellMom localhost:4000 enterprises tfAlarmTable
    TAILF-ALARM-MIB::tfAlarmNumber.0 = Gauge32: 2
    TAILF-ALARM-MIB::tfAlarmLastChanged.0 = STRING: 2012-9-10,9:55:9.2,+0:0
    TAILF-ALARM-MIB::tfAlarmType.1 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmType.2 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmDevice.1 = STRING: www0
    TAILF-ALARM-MIB::tfAlarmDevice.2 = STRING: lb0
    TAILF-ALARM-MIB::tfAlarmObject.1 = STRING: \
    /ncs:devices/ncs:device[ncs:name="www0"]
    TAILF-ALARM-MIB::tfAlarmObject.2 = STRING: \
    /ncs:devices/ncs:device[ncs:name="lb0"]
    ...
    TAILF-ALARM-MIB::tfAlarmCleared.1 = INTEGER: false(2)
    TAILF-ALARM-MIB::tfAlarmCleared.2 = INTEGER: false(2)

Here, we see two active (`tfAlarmCleared` = false) alarms of type
`connection-failure` for `www0` and `lb0`.

This example uses SNMPv3 `authPriv` throughout:

    snmpwalk -v3 -u initial -l authPriv -a SHA -A GoTellMom \
      -x AES -X GoTellMom \
      localhost:4000 enterprises tfAlarmTable

Start the devices and connect to them to clear the alarms:

    ncs-netsim start lb0
    ncs-netsim start www0
    ncs_cli --user=admin
    > request devices connect
    connect-result {
        device lb0
        result true
        info (admin) Connected to lb0 - 127.0.0.1:12022
    }
    ...

    snmpwalk -v3 -u initial -l authPriv -a SHA -A GoTellMom \
      -x AES -X GoTellMom localhost:4000 enterprises tfAlarmTable
    TAILF-ALARM-MIB::tfAlarmNumber.0 = Gauge32: 4
    TAILF-ALARM-MIB::tfAlarmLastChanged.0 = STRING: 2012-9-10,9:57:51.9,+0:0
    TAILF-ALARM-MIB::tfAlarmType.1 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmType.2 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmDevice.1 = STRING: www0
    TAILF-ALARM-MIB::tfAlarmDevice.2 = STRING: lb0
    TAILF-ALARM-MIB::tfAlarmObject.1 = STRING: \
    /ncs:devices/ncs:device[ncs:name="www0"]
    TAILF-ALARM-MIB::tfAlarmObject.2 = STRING: \
    /ncs:devices/ncs:device[ncs:name="lb0"]
    TAILF-ALARM-MIB::tfAlarmObjectOID.1 = OID: SNMPv2-SMI::zeroDotZero
    TAILF-ALARM-MIB::tfAlarmObjectOID.2 = OID: SNMPv2-SMI::zeroDotZero
    TAILF-ALARM-MIB::tfAlarmObjectStr.1 = STRING:
    TAILF-ALARM-MIB::tfAlarmObjectStr.2 = STRING:
    TAILF-ALARM-MIB::tfAlarmSpecificProblem.1 = STRING:
    TAILF-ALARM-MIB::tfAlarmSpecificProblem.2 = STRING:
    TAILF-ALARM-MIB::tfAlarmEventType.1 = INTEGER: communicationsAlarm(2)
    TAILF-ALARM-MIB::tfAlarmEventType.2 = INTEGER: communicationsAlarm(2)
    TAILF-ALARM-MIB::tfAlarmProbableCause.1 = Gauge32: 0
    TAILF-ALARM-MIB::tfAlarmProbableCause.2 = Gauge32: 0
    TAILF-ALARM-MIB::tfAlarmOrigTime.1 = STRING: 2012-9-10,9:55:9.2,+0:0
    TAILF-ALARM-MIB::tfAlarmOrigTime.2 = STRING: 2012-9-10,9:55:9.2,+0:0
    TAILF-ALARM-MIB::tfAlarmTime.1 = STRING: 2012-9-10,9:57:51.9,+0:0
    TAILF-ALARM-MIB::tfAlarmTime.2 = STRING: 2012-9-10,9:57:51.9,+0:0
    TAILF-ALARM-MIB::tfAlarmSeverity.1 = INTEGER: major(4)
    TAILF-ALARM-MIB::tfAlarmSeverity.2 = INTEGER: major(4)
    TAILF-ALARM-MIB::tfAlarmCleared.1 = INTEGER: true(1)
    TAILF-ALARM-MIB::tfAlarmCleared.2 = INTEGER: true(1)
    TAILF-ALARM-MIB::tfAlarmText.1 = STRING: Failed to connect to device www0:\
    connection refused
    TAILF-ALARM-MIB::tfAlarmText.2 = STRING: Failed to connect to device lb0:\
    connection refused
    > exit

Notifications (traps)
---------------------

As a preparation, you started the Net-SNMP trap receiver in the preparations
sections.

To have NSO send alarm notifications northbound over SNMP, you configure
notification targets according to the SNMPv3 framework MIBs. In this example
we update the default monitor target to use the same SNMPv3 `authPriv`
credentials as the SNMP walk commands and the supplied `snmptrapd.conf`. Remember
to tie `notify` and `target` using the same `tag`:

    ncs_cli --user=admin
    > configure
    % set snmp target monitor udp-port 27777
    % set snmp target monitor usm user-name initial
    % set snmp target monitor usm sec-level auth-priv
    % commit
    % show snmp notify
    notify foo {
      tag  monitor;
      type trap;
    }
    % show snmp target monitor
    target monitor {
      ip       127.0.0.1;
      udp-port 27777;
      tag      [ monitor ];
      timeout  1500;
      retries  3;
      usm {
        user-name initial;
        sec-level auth-priv;
      }
    }
    % exit
    > exit

With the above settings, NSO will send notifications to localhost, 127.0.0.1,
on UDP port 27777 using SNMPv3 `authPriv`. This is in line with how we started
the Net-SNMP trap receiver.

View the contents of the trap receiver log:

    cat traplog.txt

    NET-SNMP version 5.6
    2012-09-10 12:02:28 localhost [UDP: [127.0.0.1]:4000->[0.0.0.0]:0]:
    ...
    SNMPv2-MIB::snmpTrapOID.0 = ....tfAlarmMajor
      SNMPv2-SMI::enterprises.tailf....tfAlarmtype.0 = STRING: \
      "connection-failure"

The NSO alarm MIB has specific notifications for the different severity levels.
This simplifies basic integration to alarm managers that can map traps to
different severity levels.

Changing Probable Cause Mapping
-------------------------------

Some alarm managers require mapping of event type and probable cause according
to X.733.

NSO has an alarm model where this kind of mapping can be performed. Assume you
would like to change the mapping of `connection-failure` alarm type to
`probable-cause` value 22, X.733 `connectionEstablishmentError`:

    ncs_cli --user=admin
    > configure
    % set alarms alarm-model alarm-type connection-failure probable-cause 22
    % commit
    % exit
    > exit

Now, if you walk the alarm list, the `probable-case` is 22:

    snmpwalk -v3 -u initial -l authPriv -a SHA -A GoTellMom \
      -x AES -X GoTellMom localhost:4000 enterprises tfAlarmTable
    TAILF-ALARM-MIB::tfAlarmNumber.0 = Gauge32: 4
    TAILF-ALARM-MIB::tfAlarmLastChanged.0 = STRING: 2012-9-10,10:5:18.9,+0:0
    TAILF-ALARM-MIB::tfAlarmType.1 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmType.2 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmType.3 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmType.4 = STRING: connection-failure
    TAILF-ALARM-MIB::tfAlarmDevice.1 = STRING: www0
    TAILF-ALARM-MIB::tfAlarmDevice.2 = STRING: lb0
    TAILF-ALARM-MIB::tfAlarmDevice.3 = STRING: www1
    TAILF-ALARM-MIB::tfAlarmDevice.4 = STRING: www2
    TAILF-ALARM-MIB::tfAlarmObject.1 = STRING: \
    /ncs:devices/ncs:device[ncs:name="www0"]
    TAILF-ALARM-MIB::tfAlarmObject.2 = STRING: \
    /ncs:devices/ncs:device[ncs:name="lb0"]
    TAILF-ALARM-MIB::tfAlarmObject.3 = STRING: \
    /ncs:devices/ncs:device[ncs:name="www1"]
    TAILF-ALARM-MIB::tfAlarmObject.4 = STRING: \
    /ncs:devices/ncs:device[ncs:name="www2"]
    TAILF-ALARM-MIB::tfAlarmObjectOID.1 = OID: SNMPv2-SMI::zeroDotZero
    TAILF-ALARM-MIB::tfAlarmObjectOID.2 = OID: SNMPv2-SMI::zeroDotZero
    TAILF-ALARM-MIB::tfAlarmObjectOID.3 = OID: SNMPv2-SMI::zeroDotZero
    TAILF-ALARM-MIB::tfAlarmObjectOID.4 = OID: SNMPv2-SMI::zeroDotZero
    TAILF-ALARM-MIB::tfAlarmObjectStr.1 = STRING:
    TAILF-ALARM-MIB::tfAlarmObjectStr.2 = STRING:
    TAILF-ALARM-MIB::tfAlarmObjectStr.3 = STRING:
    TAILF-ALARM-MIB::tfAlarmObjectStr.4 = STRING:
    TAILF-ALARM-MIB::tfAlarmSpecificProblem.1 = STRING:
    TAILF-ALARM-MIB::tfAlarmSpecificProblem.2 = STRING:
    TAILF-ALARM-MIB::tfAlarmSpecificProblem.3 = STRING:
    TAILF-ALARM-MIB::tfAlarmSpecificProblem.4 = STRING:
    TAILF-ALARM-MIB::tfAlarmEventType.1 = INTEGER: communicationsAlarm(2)
    TAILF-ALARM-MIB::tfAlarmEventType.2 = INTEGER: communicationsAlarm(2)
    TAILF-ALARM-MIB::tfAlarmEventType.3 = INTEGER: communicationsAlarm(2)
    TAILF-ALARM-MIB::tfAlarmEventType.4 = INTEGER: communicationsAlarm(2)
    TAILF-ALARM-MIB::tfAlarmProbableCause.1 = Gauge32: 22
    TAILF-ALARM-MIB::tfAlarmProbableCause.2 = Gauge32: 22
    TAILF-ALARM-MIB::tfAlarmProbableCause.3 = Gauge32: 22
    TAILF-ALARM-MIB::tfAlarmProbableCause.4 = Gauge32: 22

Cleanup
-------

Stop all daemons and clean all created files:

    make -C ../../service-management/website-service stop clean

Further Reading
---------------

+ NSO Development Guide: Alarm MIB
+ The `demo.sh` script
