CDB API Python Subscribers
==========================

This example shows a few different ways to subscribe to changes in the CDB
configuration database.

The CDB package `packages/cdb/package-meta-data.xml` file shows that the
package has two components, where each component is of type `application`:

- A CDB configuration data subscriber that subscribes to the
  `/devices/device{ex0}/config` path.
- A CDB operational data subscriber that subscribes to the `/test/stats-item`
  path

Whenever a change occurs there, the code iterates through the change and prints
the changed values.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the packages:

    make all

Start the `ncs-netsim` network:

    ncs-netsim start

Start NSO:

    ncs

Sync the configuration from the devices:

    ncs_cli -u admin
    > request devices sync-from

Trigger the CDB configuration data subscriber:

    > configure
    % set devices device ex0 config sys syslog server 4.5.6.7 enabled
    % commit
    % no devices device ex0 config sys syslog server 4.5.6.7
    % exit
    > exit

Get the resulting log entries:

    cat logs/ncs-python-vm-cdb.log | grep "4.5.6.7"

Trigger the CDB operational data subscriber:

    ncs_cmd -o -dd -c 'mcreate /test/stats-item{dawnfm};
                       mset /test/stats-item{dawnfm}/i 42;
                       mset /test/stats-item{dawnfm}/inner/l boogaloo'
    ncs_cmd -o -dd -c 'mdel /test/stats-item{dawnfm}'

Get the resulting log entries:

    cat logs/ncs-python-vm-cdb.log | grep "dawnfm"

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Using CDB
+ Python API reference documentation: `ncs.cdb`
+ The `demo.sh` script
+ `packages/cdb/python/plaincdbsub/plaincdbsub.py`
+ `packages/cdb/python/opercdbsub/opercdbsub.py`


