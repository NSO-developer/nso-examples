
MPLS Layer3 VPN Example
=======================

This version of the MPLS VPN example illustrates template-centric
implementation where the main logic is driven by the template, while the Java
code performs auxiliary computations. It is functionality-wise the same as the
`mpls-vpn-java` example.

Example Network
---------------

The example network consists of Cisco ASR 9k and Juniper core routers (P and
PE) and Cisco IOS-based CE routers.

![network](network.jpg)

The Layer3 VPN service configures the CE/PE routers for all endpoints in the
VPN with BGP as the CE/PE routing protocol. Layer2 connectivity between CE and
PE routers is done through a Layer2 Ethernet access network, which is out of
scope for this example.

The Layer3 VPN service includes VPN connectivity, bandwidth, and QOS
parameters.

External Policies
-----------------

This example makes use of two different external policies. The external
policies in this example are modeled in YANG and stored in NSO but not as part
of the actual service data model.

Having policy information that many service instances can reference can be very
powerful. Changes in the network topology or a QOS policy could now be done in
one place. NSO can then re-deploy all affected service instances and
reconfigure the network. This will be shown later in this example.

Topology
--------

The service configuration only references CE devices for the endpoints in the
VPN. The service mapping logic reads from a simple topology model that is
configuration data in NSO outside the actual service model and derives what
other network devices to configure.

The topology information has two parts. The first part lists connections in the
network and is used by the service mapping logic to determine which PE router
to configure for an endpoint.

The snippets below show the configuration output in the C-style NSO CLI:

    topology connection c0
     endpoint-1 device ce0 interface GigabitEthernet0/8 ip-address \
     192.168.1.1/30
     endpoint-2 device pe0 interface GigabitEthernet0/0/0/3 ip-address \
     192.168.1.2/30
     link-vlan 88
    !
    topology connection c1
     endpoint-1 device ce1 interface GigabitEthernet0/1 ip-address \
     192.168.1.5/30
     endpoint-2 device pe1 interface GigabitEthernet0/0/0/3 ip-address \
     192.168.1.6/30
     link-vlan 77
    !

The second part lists devices for each role in the network and can, for
example, be used to dynamically render a network map by adding a Web UI
customization:

    topology role ce
     device [ ce0 ce1 ce2 ce3 ce4 ce5 ]
    !
    topology role pe
     device [ pe0 pe1 pe2 pe3 ]
    !

QOS
---

QOS configuration in service provider networks is complex and often requires a
lot of different variations. It is also usually desirable to be able to
deliver various levels of QOS. This example shows how a QOS policy
configuration can be stored in NSO and referenced from VPN service instances.

Three levels of QOS policies are defined: `GOLD`, `SILVER`, and `BRONZE`, with
different queueing parameters:

    qos qos-policy GOLD
     class BUSINESS-CRITICAL
      bandwidth-percentage 20
     !
     class MISSION-CRITICAL
      bandwidth-percentage 25
     !
     class REALTIME
      bandwidth-percentage 20
      priority
     !
    !
    qos qos-policy SILVER
     class BUSINESS-CRITICAL
      bandwidth-percentage 25
     !
     class MISSION-CRITICAL
      bandwidth-percentage 25
     !
     class REALTIME
      bandwidth-percentage 10
     !
    !
    qos qos-policy BRONZE
     class BUSINESS-CRITICAL
      bandwidth-percentage 20
     !
     class MISSION-CRITICAL
      bandwidth-percentage 10
     !
     class REALTIME
      bandwidth-percentage 10
     !
    !

Three different traffic classes are also defined with a DSCP value that will be
used inside the MPLS core network, and default rules that will match traffic to
a class.

    qos qos-class BUSINESS-CRITICAL
     dscp-value af21
     match-traffic ssh
      source-ip      any
      destination-ip any
      port-start     22
      port-end       22
      protocol       tcp
     !
    !
    qos qos-class MISSION-CRITICAL
     dscp-value af31
     match-traffic call-signaling
      source-ip      any
      destination-ip any
      port-start     5060
      port-end       5061
      protocol       tcp
     !
    !

Running The Example from the CLI
--------------------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script.

Ensure you start clean, i.e, no old configuration data is present. If you have
been running this or some other example before, stop any NSO or simulated
network nodes, `ncs-netsim`, you may have running. Outputs like 'connection
refused (stop)' mean no previous NSO was running, and 'DEVICE ce0 connection
refused (stop)...' no simulated network was running, which is good.

    make stop clean all start
    ncs_cli -u admin -C

This will set up the environment and start the simulated network and NSO.

VPN Service Configuration in the CLI
------------------------------------

Before creating a new L3VPN service, we must sync the configuration from all
network devices and then enter config mode:

    devices sync-from

Let's start by configuring a VPN network:

    config
    !
    vpn l3vpn volvo
    route-distinguisher 999
    endpoint main-office
    ce-device    ce6
    ce-interface GigabitEthernet0/11
    ip-network   10.10.1.0/24
    as-number    65101
    bandwidth    12000000
    !
    endpoint branch-office1
    ce-device    ce1
    ce-interface GigabitEthernet0/11
    ip-network   10.7.7.0/24
    as-number    65102
    bandwidth    6000000
    !
    endpoint branch-office2
    ce-device    ce4
    ce-interface GigabitEthernet0/18
    ip-network   10.8.8.0/24
    as-number    65103
    bandwidth    300000
    !

Before we send anything to the network, let's see what would be sent if we
committed:

    commit dry-run outformat native

The output is too large to include here, but as you can see, each CE device and
the PE router it is connected to will be configured.

You can give the CLI pipe flag `debug template` to get detailed information on
what configuration the output will affect, how, the result of XPath
evaluations, etc. A good way to figure out if the template is doing something
wrong:

    commit dry-run | debug template

Let's commit the configuration to the network:

    commit

Let's add a second VPN.

    top
    !
    vpn l3vpn ford
    route-distinguisher 777
    endpoint main-office
    ce-device    ce2
    ce-interface GigabitEthernet0/5
    ip-network   192.168.1.0/24
    as-number    65201
    bandwidth    10000000
    !
    endpoint branch-office1
    ce-device    ce3
    ce-interface GigabitEthernet0/5
    ip-network   192.168.2.0/24
    as-number    65202
    bandwidth    5500000
    !
    endpoint branch-office2
    ce-device    ce5
    ce-interface GigabitEthernet0/5
    ip-network   192.168.7.0/24
    as-number    65203
    bandwidth    1500000
    !

And commit the configuration to the network:

    commit

Adding New Devices
------------------

A common use case is, of course, to add a new CE device and add that as an
endpoint to an existing VPN. Below is the sequence to add two new CE devices
and add them to the VPNs. First, we add them to the topology:

    top
    !
    topology connection c7
    endpoint-1 device ce7 interface GigabitEthernet0/1 ip-address \
    192.168.1.25/30
    endpoint-2 device pe1 interface GigabitEthernet0/0/0/5 ip-address \
    192.168.1.26/30
    link-vlan 103
    !
    topology connection c8
    endpoint-1 device ce8 interface GigabitEthernet0/1 ip-address \
    192.168.1.29/30
    endpoint-2 device pe1 interface GigabitEthernet0/0/0/5 ip-address \
    192.168.1.30/30
    link-vlan 104
    !
    commit dry-run
    commit

Then we add them to the VPNs:

    top
    !
    vpn l3vpn ford
    endpoint new-branch-office
    ce-device    ce7
    ce-interface GigabitEthernet0/5
    ip-network   192.168.9.0/24
    as-number    65204
    bandwidth    4500000
    !
    vpn l3vpn volvo
    endpoint new-branch-office
    ce-device    ce8
    ce-interface GigabitEthernet0/5
    ip-network   10.8.9.0/24
    as-number    65104
    bandwidth    4500000
    !

Before sending anything to the network, look at the device configuration using
a `dry-run`. As you can see, both new CE devices are connected to the same PE
router but for different VPN customers:

    commit dry-run outformat native

And commit the configuration to the network:

    commit

Topology Changes
----------------

Service provider networks constantly change, and migrating or changing hardware
can be time-consuming.

This section will show how we can change the external topology information to
tell NSO that the new CE devices we added (CE7 and CE8) are now connected to
PE0 instead of PE1.

Let's start by changing the topology configuration and commit it. Remember that
this configuration in NSO isn't connected to any service, so nothing will be
sent to the network now:

    top
    !
    topology connection c7 endpoint-2 device pe0
    topology connection c8 endpoint-2 device pe0
    commit dry-run
    commit

NSO has a very powerful tool that lets you re-deploy services. Let's try it and
see what will be sent to the network:

    top
    !
    vpn l3vpn * re-deploy dry-run { outformat native }

As you can see from the output, the configuration on PE1 will be cleaned up,
and PE0 will now be configured with the VPN configuration.

Let's send the configuration to the network:

    vpn l3vpn * re-deploy

QOS Configuration
-----------------

So far, we have only set up basic VPN connectivity in our network. Let's add
QOS to our VPN customers. We will do that by referencing one of the globally
defined QOS policies:

    top
    !
    vpn l3vpn volvo
    qos qos-policy SILVER
    !
    vpn l3vpn ford
    qos qos-policy BRONZE

Let's see what is sent to the network:

    commit dry-run outformat native

As you can see, many configurations are sent to the network. CE and PE devices
are configured with the QOS policies and information on classifying traffic.

And commit the configuration to the network

    commit

Advanced QOS Configuration
--------------------------

The steps above will install our VPN customers' globally defined QOS policies.
However, they may want to add custom rules to classify traffic into the service
provider-defined traffic classes. For example, DNS traffic and SSH traffic
towards a specific server:

    top
    !
    vpn l3vpn volvo
    qos custom-qos-match dns
    qos-class      MISSION-CRITICAL
    source-ip      any
    destination-ip 170.110.10.1/32
    port-start     53
    port-end       53
    protocol       tcp
    !
    exit
    !
    qos custom-qos-match ssh
    qos-class      BUSINESS-CRITICAL
    source-ip      any
    destination-ip 10.10.10.1/32
    port-start     22
    port-end       22
    protocol       tcp
    !

Review the configuration changes:

    commit dry-run outformat native

As you can see, rules for matching traffic will be added, and the class maps
for `MISSION-CRITICAL` and `BUSINESS-CRITICAL` traffic will be updated on CE
routers in the VPN.

External QOS Policy Changes
---------------------------

Let's look at the power of NSO together with external policy information. In
the external QOS information, we have defined a DSCP value for each traffic
class. The DSCP values for each class will be set on all CE routers, matched
against the PE router, and used within the MPLS cloud:

    top
    !
    qos qos-class MISSION-CRITICAL dscp-value af32
    commit

Now let's see what effect that has on the network:

    vpn l3vpn * re-deploy dry-run { outformat native }

As you can see, NSO will calculate the minimal diff to be sent to the network.
Re-deploy to send the configuration to the network:

    vpn l3vpn * re-deploy

Decommissioning VPNs
--------------------

An important aspect of a service provider network is to be able to decommission
a VPN and be sure that all configures associated with that VPN are cleaned up
from the network:

    top
    no vpn l3vpn volvo

Let's test this with one of our VPNs and see what happens to the network:

    commit dry-run outformat native

All is good, and our VPN configuration has been removed from the network. Let's
commit the changes:

    commit

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Templates
+ NSO Development Guide: Layer 3 MPLS VPN Service
+ NSO Operation & Usage Guide: Service Example
+ The `demo.sh` script
