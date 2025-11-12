Create and Install a generic RESTCONF NED
=========================================

This example runs three devices that provides a RESTCONF interface for
configuration and statistics. The Java code for the NSO generic RESTCONF NED is
provided by a NED package:

    ./packages/router-rc-1.0

The simulated network for this example:

                     -------
                     | NSO |
                     -------
                        |
                        |
    ----------------------------------------------------------
         |                      |                      |
         |                      |                      |
      -------                -------                -------
      | ex0 |                | ex1 |                | ex2 |
      -------                -------                -------

NSO manages three devices using RESTCONF commands through the Java code
implementing the NSO Java NED API:

    ./packages/router-rc-1.0/src/java/src/com/tailf/packages/ned/netsimgen/ \
    NetsimGen.java

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

Stop the demo:

    ncs-netsim stop
    ncs --stop
    make clean

The below steps are similar to the demo script using the NSO J-style CLI
instead of the C-style CLI.

Build the generic RESTCONF NED package, generate netsim devices, and start NSO:

    make all start

Sync the configuration from the RESTCONF devices:

    ncs_cli -u admin
    > request devices sync-from dry-run
    > request device sync-from

Show the device configuration and status:

    > show running-config devices device * config sys
    > show devices device * live-status sys

Add configuration to the devices:

    > configure
    % set devices device ex0..2 config r:sys routes inet route 10.2.0.0 24
    % edit devices device ex0..2 config r:sys routes inet route 10.2.0.0 24
    % set next-hop 10.2.0.254 metric 20
    % commit dry-run
    % commit
    % top
    % exit
    > exit

Show the routes configuration logging into the ex1 device:

    ncs-netsim cli-c ex1
     show running-config sys routes
    > exit

Execute the `archive-log` action from NSO:

    ncs_cli -u admin
    > request devices device ex0..2 config sys syslog server 10.3.4.5 \
    archive-log archive-path test compress true
    > exit

Introduce a configuration mismatch by changing the configuration on the ex1
device:

    ncs-netsim cli-c ex1
    # config
    (config)# no sys routes
    (config)# commit
    (config)# exit
    # exit

Check if the devices are in sync with NSO:

    ncs_cli -u admin
    > request devices check-sync

Check the diff between NSO and the device configuration and sync from NSO to
the ex1 device:

    > devices device ex1 sync-to dry-run
    > devices device ex1 sync-to

Check again if the devices are in sync with NSO:

    > devices check-sync

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ The demo.sh shell script
+ NSO Development Guide chapter Generic NED Development
+ NSO Java API `com.tailf.ned` package
