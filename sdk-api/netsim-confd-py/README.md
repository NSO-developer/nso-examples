Create CDB and DP Python Applications
=====================================

This example shows how to extend a ConfD netsim device with Python applications
to simulate device behavior in NSO.

It demonstrates how to implement:

* A CDB subscriber for configuration data changes
* A CDB subscriber for operational data changes
* An action handler that sends a notification to NSO and returns a result

These components allow you to simulate a device that reacts to configuration changes, updates operational data, and generates notifications.

The YANG module `package-repository/dummy/src/yang/dummy.yang` defines:

* Configuration data (name)
* Operational data (state)
* An action (do-something)
* A notification (something-done)

The ConfD Python application (device side)
`package-repository/dummy/netsim/dummy_app.py` implements:

* Configuration CDB subscriber
* Operational data CDB subscriber
* Action handler that sends a NETCONF notification to NSO

The application is started by the
`package-repository/dummy/netsim/start.sh` shell script.

The `dummy/netsim/confd.conf.netsim` configuration file:

* Adds a `something_done_notifications` notification stream
* Enable the NETCONF notification capability

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    ./demo.sh

The below steps are similar to the demo script using the NSO J-style CLI
instead of the C-style CLI.

Initialize an NSO runtime directory and build the package:

    ncs-setup --package package-repository/dummy --use-copy --dest nso-rundir
    make -C nso-rundir/packages/dummy/src/

Create a single simulated device:

    ncs-netsim --dir nso-rundir/netsim create-network \
        nso-rundir/packages/dummy 1 d

Generate initial device configuration:

    ncs-netsim --dir nso-rundir/netsim ncs-xml-init d0 > \
        nso-rundir/ncs-cdb/device-init.xml

Start NSO and the simulated device:

    ncs-netsim --dir nso-rundir/netsim start
    ncs --cd ./nso-rundir

Verify the package is loaded and sync the configuration from the netsim device
to NSO:

    ncs_cli -u admin
    > show packages
    > request devices sync-from

Change the device configuration to trigger the configuration subscriber:

    > configure
    % set device device d0 config dummy name test-device
    % commit dry-run outformat native
    % commit
    % exit
    > exit

Check the netsim ConfD device log to confirm the configuration subscriber
application was triggered by the changes:

    cat nso-rundir/netsim/d/d0/logs/dummy.log

Make operational data changes over MAAPI using the `ncs_cmd` tool to trigger
the operational data subscriber:

    ncs_cmd -o \
        -p $(ncs-netsim --dir nso-rundir/netsim get-port d0 ipc) \
        -c 'mset /dummy:dummy/state "new-state"'

Check the netsim ConfD device log to confirm the operational data subscriber
application was triggered:

    cat nso-rundir/netsim/d/d0/logs/dummy.log

Configure NSO to subscribe to NETCONF notifications for the
`something_done_notifications` stream:

    ncs_cli -u admin
    > show devices device d0 notifications stream
    > configure
    % set devices device d0 notifications subscription something-done-notif \
    stream something_done_notifications local-user admin
    % commit
    % exit

Invoke the the `d0` simulated device action using the NSO CLI:

    > request devices device d0 live-status dummy do-something
    > exit

Check the device log to confirm the action handler was invoked:

    cat nso-rundir/netsim/d/d0/logs/dummy.log

Show received device `something-done` notification that was sent from the
action application:

    ncs_cli -u admin
    > show devices device d0 notifications received-notifications
    > exit

Cleanup
-------

Stop all daemons and clean all created files:

    ncs --stop
    ncs-netsim --dir nso-rundir/netsim stop
    rm -rf ./nso-rundir

Further Reading
---------------

+ The demo.sh shell script
+ NSO Operation & Usage Guide chapter Network Simulator
+ NSO ncs-netsim(1) man page











