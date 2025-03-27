CDB API Python Two-Phase Subscribers
====================================

This example demonstrates a two-phase CDB mandatory subscriber that will
iterate over the changed configuration during the prepare phase of the
transaction and abort the transaction if the number of devices with
configuration changes exceed a limit.

As can be seen in the `packages/cdb/package-meta-data.xml` file, the
`device-blast-radius` package implements a Python mandatory CDB subscriber
application that prevents a specified number of devices from being modified in
one transaction. The user configures the number of devices limit under
`/devices/blast-radius/max-devices` and prevents a transaction from modifying
more devices than the limit by mistake.

Suppose the CDB subscriber, for some reason, does not handle the subscription
event in the prepare phase. In that case, the transaction will be aborted and
reverted as the subscriber is set to be mandatory.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the packages:

    make all

Start NSO, the subscribers, and the netsim network:

    make start

Sync the configuration from the devices:

    ncs_cli -u admin -C
    > request devices sync-from

Configure the device blast radius to maximum two devices:

    > configure
    % set devices blast-radius max-devices 2
    % commit

Configure two devices:

    % set devices device ex0 config sys interfaces interface eth42 enabled
    % set devices device ex1 config sys interfaces interface eth42 enabled
    % commit dry-run
    % commit

Configure three devices causing the commit to be aborted in the prepare phase:

    % set devices device ex0 config sys interfaces interface eth42 \
    description "test1"
    % set devices device ex1 config sys interfaces interface eth42 \
    description "test2"
    % set devices device ex2 config sys interfaces interface eth42 \
    description "test3"
    % commit dry-run
    % commit
    % exit
    > exit

Show the `logs/ncs-python-vm-device-blast-radius.log` file with the aborted
commit:

    cat logs/ncs-python-vm-device-blast-radius.log

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Using CDB
+ Python API reference documentation: `ncs.cdb` and `_ncs.cdb`
+ The `demo.sh` script
+ `packages/device-blast-radius/python/two_phase_sub.py`
