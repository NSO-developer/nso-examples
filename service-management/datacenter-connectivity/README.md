Data Center Connectivity Example
===============================

This example illustrates how to create connectivity between several access
switches in the same or different data centers. An NSO service will configure
corresponding Aggregation and Core equipment to generate the interconnection if
the connectivity service spans multiple data centers.

A VLAN and an IP Network define each connection. Any number of access switches
may participate in the connection.

The simulated network comprises of Cisco XR, Cisco XE, and Force 10
netsim/ConfD devices. The network is configured in a mesh setup, where all
access switches connect to all aggregation switches in the same data center.
The same goes for all aggregation switches connected to all core routers in the
same data center. This is described in a topology model under `/topology` where
all equipment in each data center is described.

This guide uses the following annotations for command lines:

    > - NCS J-style CLI operational mode
    % - NCS J-style CLI configuration mode
    No prompt for shell commands

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the service package:

    make all

Start the simulated network and NSO:

    make start

Sync the configuration from the devices:

    ncs_cli -u admin
    > request devices sync-from

Now, the NSO configuration database (CDB) has a complete replication of the
network, the parts covered by the device YANG models, in its memory.

Create a connectivity service instance:

    > configure
    % set datacenter connectivity connection1 vlan 777 ip-network 10.1.1.0/24
    % edit datacenter connectivity connection1
    % set connectivity-settings preserve-vlan-tags

Add endpoints (access switches):

    % set endpoint catalyst0 GigabitEthernet0/5
    % set endpoint dell0 GigabitEthernet0/2
    % set endpoint catalyst2 GigabitEthernet0/7
    % set endpoint catalyst3 GigabitEthernet0/7
    % top

Review the configuration that will be sent to the devices once the service is
committed:

    % commit dry-run outformat native

Update the VLAN of an existing service.

Deploying new services is easy, but what about changes? Updating the
VLAN parameter of the service will have an impact on all devices participating:

    % set datacenter connectivity connection1 vlan 234
    % commit dry-run outformat native

As seen in the dry-run output, all devices are affected by the above change.
There is no code written to handle modification. After creating a service, NSO
FASTMAP logic manages all changes to a service and calculates the diff that
needs to be applied to the network.

Cleanup
-------

Stop all daemons and clean all created files:

    ncs-netsim --async stop
    ncs --stop
    make clean

Further Reading
---------------

+ NSO Development Guide: Services
+ The `demo.sh` script
