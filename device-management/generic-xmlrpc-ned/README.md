Create and Install a Generic NED
================================

This example runs three devices that use a proprietary XML-RPC interface for
configuration. The Java code for the NSO generic NED is provided as two
packages:

    ./packages/xml-rpc - the generic NED code
    ./packages/common  - common Java archives used by the generic NED

The Java code for the simulated managed devices is found separately under the
`devices` directory:

    ./devices/device-common - common code for the devices
    ./devices/device-x1     - device 1 code and working dir
    ./devices/device-x2     - device 2 code and working dir
    ./devices/device-x3     - device 3 code and working dir

Thus, in this example, we have an architecture that looks like:

                         ---------
                         |  NSO  |
                         ---------
                             |
                             |
    _________________________|________________
        |                |               |
        |                |               |
        x1               x2              x3


NSO manages three devices over the XML-RPC protocol through a generic NED.
The XML-RPC calls are defined in:

    ./devices/device-common/src/com/example/xmlrpcdevice

The NED code, which executes in the NSO service manager, resides in:

    ./packages/xml-rpc/src/java/src/com/example/xmlrpcdevice/xmlrpc

The example is self-contained, and the Apache XML-RPC code is part of it.
It resides in:

    ./packages/common/shared-jar

A small YANG model represents the devices and can be found under:

    ./packages/xml-rpc/src/yang/interfaces.yang

XML-RPCs are defined as manipulating such data in each XML-RPC server.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

To build the example:

    make stop clean all

- The managed device YANG modules are compiled, and the execution environment
  is created for all three managed devices.
- YANG modules are imported and compiled.

**Note**: The make target REMAKE executes `stop clean all start status`. This
includes cleaning the CDB database, which removes all changes made by running
the example.

    make start

The command starts all three XML-RPC server instances and the Java applications
needed to configure the devices. The NSO server is initialized with managed
device data, and the NSO device manager is populated with managed device
configuration used to connect and authenticate the devices over SSH.

Start configuring the devices:

    make cli
    admin@zoe% configure
    Entering configuration mode private
    [ok][2011-02-23 13:56:31]

    [edit]

Sync with the devices:

    admin@zoe% request devices sync-from
    sync-result {
        device x1
        result true
    }
    sync-result {
        device x2
        result true
    }
    sync-result {
        device x3
        result true
    }
    [ok][2011-12-12 16:51:40]

    [edit]

Add some configuration to the devices through NSO:

    admin@zoe% set devices device x1..3 config if:interface eth0 mtu 1200
    [ok][2011-12-12 16:52:56]

    [edit]

Commit the changes:

    admin@zoe% commit | details
    entering validate phase for candidate...
      validate: grabbing transaction lock... ok
    entering write phase for candidate...
    entering prepare phase for candidate...
    entering commit phase for candidate...
    entering validate phase for running...
      validate: grabbing transaction lock... ok
      validate: creating rollback file... ok
      validate: pre validate... ok
      validate: run validation dependencies...
      validate: validation dependencies done
      validate: run full validation...
      validate: full validation done
      validate: check configuration policies...
      validate: configuration policies done
    entering write phase for running...
      write_start cdb
    entering prepare phase for running...
      prepare cdb
      ncs: Prepare phase
      ncs: Connecting NED x3

      ncs: Connecting NED x1

      ncs: Connecting NED x2

      ncs: Device: x3 Send NED prepare
      ncs: Device: x2 Send NED prepare
      ncs: Device: x1 Send NED prepare
    entering commit phase for running...
      commit cdb
      ncs: Commit phase
      ncs: Device: x3 Send NED commit
      ncs: Device: x2 Send NED commit
      ncs: Device: x1 Send NED commit
      ncs: Device: x1 Send NED persist
      ncs: Device: x2 Send NED persist
      ncs: Device: x3 Send NED persist
    Commit complete.
    [ok][2011-12-12 16:53:06]

To turn on the NED trace:

    admin@zoe% set devices global-settings trace pretty
    [ok][2011-12-12 16:53:59]

    [edit]

Commit the change:

    admin@zoe% commit
    Commit complete.
    [ok][2011-12-12 16:54:01]

    [edit]

Disconnect the devices to enable the trace when reconnecting:

    admin@zoe% request devices disconnect
    [ok][2011-12-12 16:54:07]

Change some configuration to the devices through NSO:

    admin@zoe% set devices device x1..3 config if:interface eth0 mtu 1300
    [ok][2011-12-12 16:54:28]

    [edit]

Commit the change:

    admin@zoe% commit
    Commit complete.
    [ok][2011-12-12 16:54:30]

Exit the CLI:

    admin@zoe% exit
    admin@zoe> exit

The commands issued by NSO in the NED trace:

    cat logs/ned-genxmlrpc-x1.trace
    >> dd-mm-yyyy::hh:mi:ss.nnn GENERIC CONNECT to x1-127.0.0.1:8045 as admin
    << dd-mm-yyyy::hh:mi:ss.nnn CONNECTED 1
    >> dd-mm-yyyy::hh:mi:ss.nnn PREPARE 1:
    modified   /if:interface[name="eth0"]
    value_set  /if:interface[name="eth0"]/mtu 1300
    << dd-mm-yyyy::hh:mi:ss.nnn PREPARE OK
    >> dd-mm-yyyy::hh:mi:ss.nnn COMMIT 1: (Timeout 0)
    << dd-mm-yyyy::hh:mi:ss.nnn COMMIT OK
    >> dd-mm-yyyy::hh:mi:ss.nnn PERSIST 1:
    << dd-mm-yyyy::hh:mi:ss.nnn PERSIST OK
    >> dd-mm-yyyy::hh:mi:ss.nnn CLOSE 1: (Pool: true)
    << dd-mm-yyyy::hh:mi:ss.nnn CLOSED

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Generic NED Development
+ The `demo.sh` script