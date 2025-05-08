SRv6 VPN Services with NSO Demo & Lab
=====================================


This demo showcases orchestrating multiple VPN services in an NSO-managed
network. Following a step-by-step walkthrough, you configure a layer-2 and
a layer-3 VPN instance in the sample Service Provider (SP) network.

At the same time, you can use this setup to explore and test the solution
on your own. You could, for example, add or remove VPN sites, or change
the VPN type, and observe the resulting network changes.

If you are not familiar with service provider technologies, such as SRv6,
the overview section provides a short introduction and some pointers for
further reading.

Two variants of the sample network are supported:

  - XRd: using containerized Cisco IOS XR and Docker Compose.
  - netsim: simulating only device configurations.

The XRd variant is recommended but requires a working Docker Compose with
an XRd container image preinstalled. The scripts in lab/ will configure
and start the network.

Alternatively, the scripts in labsim/ will start a netsim-based network,
which requires fewer resources but lacks all control and data plane
functionality except emulating device configuration.

It is also possible to use the NSO instance with your own IOS XR devices,
either physical or virtual, but you must provide the connectivity and
initial configuration yourself (we also strongly encourage you to install
a production-grade NED in nso/packages/ in this case).


Start & Connect Devices (XRd)
-----------------------------

This variant requires Docker Compose and an XRd container image preinstalled.
The scripts expect **docker-compose** command in PATH and image name
`xrd-control-plane:latest-24.4`. If you are using a different image, set
and export the `XRD_IMAGE` environment variable accordingly.

Start the network:

    $ make -C lab/ start

This could take a few (actual) minutes. You can verify the devices are up
by attaching to the console (e.g. `docker attach pe-01`) and checking for
a login prompt (exit the device console by Ctrl+p Ctrl+q).

Start the NSO instance from the nso/ directory:

    $ cd nso/
    $ make start

Connect to the NSO CLI as `admin:admin`:

    $ ssh -p 2024 admin@localhost

Enter configuration mode, invoke `lab onboard` action, and commit:

    admin@ncs# config
    admin@ncs(config)# lab onboard
    admin@ncs(config)# commit and-quit

The commit takes some time (up to a minute) as NSO onboards each device.
After the commit, NSO performs an additional sync, which takes a few
seconds more. Once complete, you are ready to start the walkthrough.


Start & Connect Devices (netsim)
--------------------------------

Note: If you are using XRd devices, skip this part.

Start the simulated network:

    $ make -C labsim/ start

Start the NSO instance from the nso/ directory:

    $ cd nso/
    $ make start

Tell NSO about the netsim devices:

    $ make add-labsim

Connect to the NSO CLI as `admin:admin`:

    $ ssh -p 2024 admin@localhost

You are now ready to start the walkthrough.


Walkthrough
-----------

Sample network uses the following topology:

```
                      core-5 (RR)
                       /       \
                   core-1 === core-2
  ce-1-1 ---\     /   ||       ||   \     /--- ce-1-2
             pe-01    ||       ||    pe-02
  ce-1-4 ---/     \   ||       ||   /     \=== ce-1-3
                   core-4 === core-3
```

Using the NSO CLI, first verify the devices were onboarded successfully:

    admin@ncs# show devices device * last-in-sync

The output should show `core` and `pe`, as well as some of the `ce` devices,
all with a recent time. If that is not the case, invoke `devices sync-from`.

Before provisioning services, the network requires initial configuration to
enable SRv6 services:

    admin@ncs# config
    admin@ncs(config)# core-network provision
    admin@ncs(config)# commit

The `core-network provision` action configures srv6-node services for the
core and PE devices. You can inspect the relevant configuration through
`core-network services srv6-node pe-01 get-modifications outformat cli-c`
or similar command, if you wish.

Configure a layer-2 point-to-point VPN link (also called E-Line) between
ce-1-4 and ce-1-3, and inspect the resulting network configuration:

    admin@ncs(config)# load merge terminal
    eline sample-eline
     customer Tail-f
     ports    [ pe-01-3 pe-02-4 ]
    !
    ^D
    admin@ncs(config)# commit dry-run outformat native
    admin@ncs(config)# commit

Note: `^D` in the above printout denotes Ctrl+d keystroke that signals the
      end of input to `load merge terminal` command.

You could also create a multipoint layer-2 VPN (EPVN ELAN) between ce-1-1,
ce-1-2 and ce-1-3. You can inspect the network configuration but do not
commit the changes, as we will instead use these ports for a layer-3 VPN.

    admin@ncs(config)# load merge terminal
    l2vpn sample-l2vpn
     customer Tail-f
     ports    [ pe-01-2 pe-02-2 pe-02-3 ]
    !
    ^D
    admin@ncs(config)# commit dry-run outformat native
    admin@ncs(config)# no l2vpn

Configure a basic layer-3 VPN between ce-1-1 and ce-1-2, requiring
preconfigured static routes on CEs:

    admin@ncs(config)# load merge terminal
    l3vpn sample-l3vpn
     customer Tail-f
     link 1
      port pe-01-2
     !
     link 2
      port pe-02-2
     !
    !
    ^D
    admin@ncs(config)# commit dry-run outformat native
    admin@ncs(config)# commit

Now add another link towards ce-1-3 to the layer-3 VPN instance, this time
using BGP to exchange routes with the CE device. Since the link connects
to a managed CE, the service also generates the configuration for the CE:

    admin@ncs(config)# load merge terminal
    l3vpn sample-l3vpn
     link 3
      port pe-02-3
      bgp-peering enabled
      bgp-peering peer-as 65010
     !
    !
    ^D
    admin@ncs(config)# commit dry-run outformat native
    admin@ncs(config)# commit

The ce-1-3 device now has two interfaces configured for VPN; one as a
layer-2 link and one as a layer-3 link.


Verification (XRd only)
-----------------------

Since ce-1-1 is part of the layer-3 VPN, it can reach ce-1-2 and ce-1-3
via the service provider SRv6 network. What is more, ce-1-3 has the network
to ce-1-4 attached and is announcing its reachability to pe-02.

Inspect the BGP routing information on ce-1-3:

    RP/0/RP0/CPU0:ce-1-3#show bgp ipv4 unicast
       Network            Next Hop            Metric LocPrf Weight Path
    *> 10.1.1.0/24        10.1.3.2                               0 65000 ?
    *> 10.1.2.0/24        10.1.3.2                 0             0 65000 ?
    *> 10.1.3.0/24        0.0.0.0                  0         32768 ?
    *                     10.1.3.2                 0             0 65000 ?
    *> 10.1.4.0/24        0.0.0.0                  0         32768 ?
    *> 10.10.20.0/24      0.0.0.0                  0         32768 ?

Therefore, both ce-1-1 and ce-1-2 can reach ce-1-4 too via ce-1-3:

    $ docker attach ce-1-1
    / # traceroute 10.1.4.4
    traceroute to 10.1.4.4 (10.1.4.4), 30 hops max, 46 byte packets
     1  pe-01.lab_ce-1-1-pe-01 (10.1.1.2)  1.429 ms  0.528 ms  0.408 ms
     2  10.1.3.2 (10.1.3.2)  6.361 ms  3.453 ms  2.754 ms
     3  10.1.3.3 (10.1.3.3)  6.572 ms  *  4.545 ms

Additionally, you can verify the routing information on the PE routers.
The first part is routing information exchanged via BGP. For layer-3 VPNs:

    RP/0/RP0/CPU0:pe-02#show bgp vpnv4 unicast vrf L3VPN-2 received-sids
       Network            Next Hop                            Received Sid
    Route Distinguisher: 65000:2 (default for vrf L3VPN-2)
    Route Distinguisher Version: 12
    *>i10.1.1.0/24        fd00::11                            5f00:0:11:e003::
    *> 10.1.2.0/24        0.0.0.0                             NO SRv6 Sid
    *> 10.1.3.0/24        0.0.0.0                             NO SRv6 Sid
    *                     10.1.3.3                            NO SRv6 Sid
    *> 10.1.4.0/24        10.1.3.3                            NO SRv6 Sid

And for layer-2 VPNs:

    RP/0/RP0/CPU0:pe-01#show bgp l2vpn evpn rd 10.10.20.112:1 received-sids
       Network                                     Next Hop                                    Received Sid
    Route Distinguisher: 10.10.20.112:1
    Route Distinguisher Version: 3
    *>i[1][0000.0000.0000.0000.0000][1]/120        fd00::12                                    5f00:0:12:e002::

    RP/0/RP0/CPU0:pe-01#show bgp l2vpn evpn rd 10.10.20.112:2 received-sids
    Route Distinguisher: 10.10.20.112:2
    Route Distinguisher Version: 13
    *>i[2][0][48][0242.0a01.0203][0]/104           fd00::12                                    5f00:0:12:e004::
    *>i[2][0][48][0242.ac50.0602][0]/104           fd00::12                                    5f00:0:12:e004::
    *>i[2][0][48][0eb7.55b5.70ce][0]/104           fd00::12                                    5f00:0:12:e004::
    *>i[3][0][32][10.10.20.112]/80                 fd00::12                                    5f00:0:12:e005::

> Note: The second part of output is not present if you follow the
>       walkthrough. It shows what would be there if you provisioned
>       l2vpn service instead of l3vpn.

You can cross-reference the SIDs in this last printout with allocations on
the other PE:

    RP/0/RP0/CPU0:pe-02#show segment-routing srv6 sid
    SID                         Behavior          Context                           Owner               State  RW
    --------------------------  ----------------  --------------------------------  ------------------  -----  --
    5f00:0:12::                 uN (PSP/USD)      'default':18                      sidmgr              InUse  Y
    5f00:0:12:e000::            uA (PSP/USD)      [Gi0/0/0/0, Link-Local]:0         isis-1              InUse  Y
    5f00:0:12:e001::            uA (PSP/USD)      [Gi0/0/0/1, Link-Local]:0         isis-1              InUse  Y
    5f00:0:12:e002::            uDX2              1:1                               l2vpn_srv6          InUse  Y
    5f00:0:12:e003::            uDT4              'L3VPN-2'                         bgp-65000           InUse  Y
    5f00:0:12:e004::            uDT2U             2:0                               l2vpn_srv6          InUse  Y
    5f00:0:12:e005::            uDT2M             2:0                               l2vpn_srv6          InUse  Y

The other part are the installed routes or MAC addresses in the VRF/BD.
For layer-3 VPN you can inspect known routes:

    RP/0/RP0/CPU0:pe-02#show route vrf L3VPN-2 ipv4
    B    10.1.1.0/24 [200/0] via fd00::11 (nexthop in vrf default), 00:11:04
    C    10.1.2.0/24 is directly connected, 00:12:27, GigabitEthernet0/0/0/2
    L    10.1.2.2/32 is directly connected, 00:12:27, GigabitEthernet0/0/0/2
    C    10.1.3.0/24 is directly connected, 00:12:27, GigabitEthernet0/0/0/3
    L    10.1.3.2/32 is directly connected, 00:12:27, GigabitEthernet0/0/0/3
    B    10.1.4.0/24 [20/0] via 10.1.3.3, 00:11:04
    B    10.10.20.0/24 [20/0] via 10.1.3.3, 00:11:04

As point-to-point pseudowire (eline) service does not learn MAC addresses,
you can only check the link status:

    RP/0/RP0/CPU0:pe-01#show l2vpn xconnect
    XConnect                   Segment 1                       Segment 2
    Group      Name       ST   Description            ST       Description            ST
    ------------------------   -----------------------------   -----------------------------
    ELINE-TAILF
               1-sample-eline
                          UP   Gi0/0/0/3              UP       EVPN 1,1,::ffff:10.0.0.1
                                                                                      UP
    ----------------------------------------------------------------------------------------

However, for multipoint layer-2 EVPN you could inspect known MAC addresses:

    RP/0/RP0/CPU0:pe-01#show evpn evi mac
    VPN-ID     Encap      MAC address    IP address                               Nexthop                                 Label    SID
    ---------- ---------- -------------- ---------------------------------------- --------------------------------------- -------- ----------------
    2          SRv6       0242.0a01.0103 ::                                       GigabitEthernet0/0/0/2                  0        5f00:0:11:e004::
    2          SRv6       0242.0a01.0203 ::                                       fd00::12                                IMP-NULL 5f00:0:12:e004::
    2          SRv6       0242.ac50.0602 ::                                       fd00::12                                IMP-NULL 5f00:0:12:e004::
    2          SRv6       0eb7.55b5.70ce ::                                       fd00::12                                IMP-NULL 5f00:0:12:e004::

> Note: This output will only be present if you have configured the l2vpn
>       service instead of l3vpn.

The remote MAC addresses are learned through BGP and you should see an
entry for each of them individually in the BGP printout for the remote PE
(the `rd 10.10.20.112:2` listing).

But note that a limitation in the current versions of the virtual IOS XR
router prevents data plane traffic (e.g. ping) from working in the layer-2
multipoint VPN scenario. However, the control plane correctly shows the
learned (remote) MAC addresses as shown here and data plane traffic does
work between interfaces inside the bridge domain (that is, links to the
same PE).


Overview of SRv6
----------------

In addition to their other services, ISPs typically also offer VPN services,
where VPN traffic does not pass over, or rely on, the public internet.
Instead, this traffic gets encapsulated when it enters service provider
network at the Provider Edge (PE) device. Encapsulation serves two purposes;
it separates traffic for this service from other traffic in the SP network
and labels the traffic for traffic engineering purposes. As a result, the
ISP can reduce state in the provider core, such as the size of the routing
tables, and push more of the state out to the edge of the network.

Segment routing is a traffic engineering approach that assigns each link
or router (segment) in a routing domain a unique label (Segment ID or SID).
A router can then select a specific path (set of segments) the packet will
traverse. This allows implementing complex routing policies, as well as
enabling novel solutions, such as Topology-Independent Loop-Free Alternate
(TI-LFA). TI-LFA is a mechanism for quickly rerouting packets to a backup
path when a network element fails, without waiting for routing protocol
to converge. See https://www.segment-routing.net/ for more information
and additional use cases.

Segment Routing over IPv6 (SRv6) encapsulates traffic in IPv6 packets, as
opposed to the more traditional MPLS. A stack of SIDs is encoded in an
extension header or directly in the IPv6 destination address. SIDs are
encoded as IPv6 addresses, which makes it possible for non-SRv6-aware IPv6
routers to route SRv6 packets.


Overview of the Lab Design
--------------------------

Both, core and PE devices in the lab are configured for SRv6. The nodes use
IS-IS routing protocol with link-local IPv6 addressing on the links. IS-IS
provides basic loopback IP (fd00::X) connectivity between nodes and also
carries SID information that can be used for TI-LFA or traffic engineering.

The `/core-network/settings/fast-reroute` setting in NSO controls whether
TI-LFA gets provisioned in the SP network.

Core or P devices are named core-X with a loopback IPv6 address of `fd00::X`.
PE devices are named pe-0Y with a loopback IPv6 address of `fd00::1Y`. Each
also has a management IPv4 of `10.10.20.10X` or `10.10.20.11Y`.

PE routers use BGP to peer with core-5. The core-5 device serves as a BGP
Route Reflector (RR), which makes for a scalable design and simplifies
configuring existing and new PEs. BGP carries the `evpn` and `vpnv4` address
families for layer-2 and IPv4 layer-3 VPNs respectively.

The CE devices represent simple, non-managed clients with preconfigured
addresses, with each ce-1-Z using `10.1.Z.3/24`. The exception is ce-1-3,
which is managed by the SP (and NSO). It also has two separate links towards
its PE.


Overview of the Service Design
------------------------------

The provisioning solution consists of a number of NSO packages.

  - `core-network`: defines the core and PE devices, their roles and topology.
  - `srv6-node`: a service for provisioning core and PE devices with the base
    SRv6/BGP config, one service instance per device, using data from
    `core-network`.

  - `inventory`: inventory of customer-facing resources; managed CEs, access
    ports (attachment circuits), and assigned VNIs. Also contains a list of
    customers to which each of these resources is tied.
  - `eline`: Ethernet pseudowire service, providing layer-2 connectivity
    across WAN between two endpoints (point-to-point).
  - `l2vpn`: Ethernet service, emulating a LAN between multiple endpoints.
  - `l3vpn`: Service providing routing between customer sites. Supports IPv4
    customer networks as currently implemented.

  - `shared-code`: helper functions, consolidated for use in other packages.
  - `onboard-lab`: helper package to simplify lab management, allowing quickly
    onboarding or resetting NSO-managed devices.

The two layer-2 services, `eline` and `l2vpn`, are fairly straightforward to
use; you must define the customer and the ports that belong to each instance.
The service models have a few manual overrides available, in case you want
finer control over the provisioning process, namely manually specifying the
`vni` value or `force`-ing use of the ports (when they are already in use).

Both of these, and `l3vpn` as well, leverage the `inventory` data, which holds
the assigned VNI numbers, as well as available/in-use ports. The CE part is
not used for the layer-2 services. You can inspect the inventory in NSO CLI:

    admin@ncs# show running-config inventory

The services use the concept of a Virtual Network Identifier (VNI), which has
a generalized meaning, not tied to a particular technology. It represents
a unique identifier given to each individual VPN and allows us to simplify
the procedure for deriving other unique identifiers needed for provisioning.
For example, we can use it to uniquely name VRFs.

The `l3vpn` service is somewhat more sophisticated, owning to the fact the
customer must provide information on what networks are connected to each
leg of the VPN. In addition to customer, you must define one or more numbered
links tied to an access port. If you do not provide the subnet, connected
to this port, the system uses `10.1.<link-id>.0/24` and assigns the PE
`10.1.<link-id>.2` IP address, which the clients can use to route packets
across the WAN.

By default, PE acts as the router for the network the link connects to, so
directly attached clients can reach remote destinations. However, if there
is a client (CE) router connecting additional networks, it must propagate
the routing information about these networks to the PE. You must enable
`bgp-peering` on the link and configure the BGP routing process on the CE.

While this can be performed manually by the customer, an SP could provide
the customer with a managed CE device. The provisioning code checks the
inventory to see if a managed CE is connected to the selected access port
and configures the CE accordingly.


Next Steps
----------

We encourage you to investigate the implementation of the services and, as
an exercise, modify the `l3vpn` to support statically-configured routes for
each link as an alternative to BGP peering (requires some NSO knowledge).

Or learn more about NSO at https://cisco-tailf.gitbook.io/nso-docs.
