
Simple MPLS Layer3 VPN Example
==============================

This example illustrates Layer3 VPNs in a service provider MPLS network.

Example Network
---------------

The example network consists of Cisco ASR 9k core routers (P and PE) and Cisco
IOS-based CE routers.

Known Issues/Simplifications
----------------------------

* The `GigabitEthernet` interface type for Cisco routers is hard coded.
* Netmasks of link networks are hardcoded to /30 and /24 for local networks.
* The service provider as-number is hardcoded to 100.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script.

Make sure you start clean, i.e., no old configuration data is present. If you
have been running this or some other example before, make sure to stop any NSO
or simulated network nodes, `ncs-netsim`, that you may have running. Output
like 'connection refused (stop)' means no previous NSO was running, and
`DEVICE ce0 connection refused (stop)...` no simulated network was running,
which is good.

Setup the environment and start the simulated network:

    make stop clean all start
    ncs_cli -u admin -C

Before creating a new L3VPN service, we must sync the configuration from all
network devices and then enter config mode:

    devices sync-from

VPN Service Configuration
-------------------------

Let's start by configuring a VPN network:

    autowizard false
    config
    vpn l3vpn volvo
     endpoint c1
      as-number 65001
      ce device ce0
      ce local interface-name GigabitEthernet
      ce local interface-number 0/9
      ce local ip-address 192.168.0.1
      ce link interface-name GigabitEthernet
      ce link interface-number 0/2
      ce link ip-address 10.1.1.1
      pe device pe2
      pe link interface-name GigabitEthernet
      pe link interface-number 0/0/0/1
      pe link ip-address 10.1.1.2
     !
     endpoint c2
      as-number 65001
      ce device ce2
      ce local interface-name GigabitEthernet
      ce local interface-number 0/3
      ce local ip-address 192.168.1.1
      ce link interface-name GigabitEthernet
      ce link interface-number 0/1
      ce link ip-address 10.2.1.1
      pe device pe2
      pe link interface-name GigabitEthernet
      pe link interface-number 0/0/0/2
      pe link ip-address 10.2.1.2
    !

Before we send anything to the network, let's see what would be sent if we
committed:

    commit dry-run outformat native

The output is too large to include here, but as you can see, each CE device and
the PE router it is connected to will be configured. Let's commit the
configuration to the network.

You can give the CLI pipe flag `debug template` to get detailed information on
what configuration the output will affect, the result of XPath evaluations,
etc. A good way to figure out if the template is doing something wrong:

    commit dry-run | debug template
    commit
    exit
    exit

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------
+ NSO Development Guide: Templates
+ The `demo.sh` script
