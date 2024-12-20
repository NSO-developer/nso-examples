Submit Alarms to NSO
====================

This example illustrates how to submit alarms to NSO using an
`AlarmSinkCentral`. The `AlarmSinkCentral` is started within the NSO Java VM.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start clean:

    ncs --stop
    ncs-netsim stop
    make clean

The example uses NSO packages called `router-nc-1.1` and `alarms`. Build the
packages:

    make all

Start the `ncs-netsim` network:

    ncs-netsim start

Start NSO:

    ncs

Sync the configuration from the devices:

    ncs_cli -u admin
    > request devices sync-from

Create a second interface on all routers to generate alarms on different
interfaces on the same device:

    > configure
    % set devices device ex0..2 config r:sys interfaces interface eth1
    % commit dry-run
    % commit
    % exit

Create a minor alarm on device `ex0` interface `eth0`:

    > request example generate alarm { device ex0 object eth0 alarm-type \
      link-down perceived-severity minor specific-problem AIS alarm-text \
      "Interface has sync problems" }

View the alarm list:

    > show alarms alarm-list

Issue a major alarm of the same type and on the same device/object:

    > request example generate alarm { device ex0 object eth0 alarm-type \
      link-down perceived-severity major specific-problem AIS alarm-text \
      "Interface has sync problems" }

View the alarm list:

    > show alarms alarm-list

We still have only one alarm as the same event happened again, but note how
`last-perceived-severity` has been raised to major.

Create a clear event:

    > request example generate alarm { device ex0 object eth0 alarm-type \
      link-down perceived-severity cleared  specific-problem AIS alarm-text \
      "Interface has sync problems" }

View the alarm list:

    > show alarms alarm-list

Note the alarm state is still there even though the managed object has flagged
the alarm as cleared.

Take the time to experiment in the CLI, creating alarms with different
combinations of `device`, `object`, `alarm-type`, and  `perceived-severity`.

The Software
------------

Start by looking at the YANG models. One YANG model models the action and its
parameters. The other YANG model shows how to extend the built-in alarm types
hierarchically.

To study this example, first, take a look at the file metadata in the file
`packages/alarms/package-meta-data.xml`

Note the package consists of two components. One component is of type
`application`, and the other is of type `callback`.  If a component is of type
`application`, we can run any code we want inside that component.

The NSO classloader will load the `jar` files from the package and then
instantiate each component according to component type. A component of type
`application` must implement the Java interface called
`com.tailf.ncs.ApplicationComponent`

The other component is of type 'callback'. This component annotates a simple
Java class, `AlarmActionSubmitter`, with
`com.tailf.dp.annotations.ActionCallback`, means the class registers itself to
an action point defined in `submit-alarm.yang` and will answer to action
requests located under `/submit-al:example/generate-alarm`.

The two components:

- An alarm producer with a simple action that submits an alarm through an
  `AlarmSink`. The `AlarmSink` attaches to the `AlarmSinkCentral`, which
  executes within the NSO Java VM. Both the `AlarmSink` and `AlarmSinkCentral`
  are part of the NSO Alarm API.
- An alarm consumer that uses `AlarmSource` to read alarms on the other
  end.

See the comments in the corresponding Java source file for further instructions
on how to run the three different components. The files are:

    ./packages/cdb/src/java/src/com/example/alarm/producer/AlarmActionSubmitter
    ./packages/cdb/src/java/src/com/example/alarm/consumer/AlarmConsumer

Cleanup
-------

Stop NSO and the simulated network, and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Developing Alarm Applications
+ The `demo.sh` script
