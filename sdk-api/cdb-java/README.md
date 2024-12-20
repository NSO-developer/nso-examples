CDB API Java Subscribers
========================

This example shows a few different ways to subscribe to changes in the CDB
configuration database.

The CDB package `packages/cdb/package-meta-data.xml` file shows that the
package has three components, where each component is of type `application` and
must thus implement the Java interface called
`com.tailf.ncs.ApplicationComponent`:

- A simple CDB subscriber which uses the raw lowlevel subscription API.
- A more extensive config subscriber which uses the raw lowlevel subscription
  API.
- A CDB operational data subscriber

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
    % delete devices device ex0 config sys syslog server 4.5.6.7
    % commit
    % exit
    > exit

Get the resulting log entries:

    cat logs/ncs-java-vm.log | grep "4.5.6.7"

Trigger the CDB operational data subscriber:

    ./setoper.sh dawnfm
    ./deloper.sh dawnfm

Get the resulting log entries:

    cat logs/ncs-java-vm.log | grep "dawnfm"

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Using CDB
+ Java API reference documentation
+ The `demo.sh` script
+ `packages/cdb/src/java/src/com/example/cdb/*.java`
+ The `setoper.sh` and `deloper.sh` scripts

