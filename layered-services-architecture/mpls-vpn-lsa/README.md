MPLS Layer3 VPN Example as Layered Service Architecture
=======================================================

The prerequisite for this example is the companion example under
`examples.ncs/service-manager/mpls-vpn-java`. To grasp this example, read, run,
and understand the `mpls-vpn-java` example first.

In this example, we have implemented the MPLS VPN service as a Layered Service
Architecture (LSA). The problem we address here is that when we build large
NSO installations, we must distribute the data over multiple NSO nodes.

The intended audience for this example is an architect who needs to understand
how to build a large NSO installation.

The code in this example creates a structure that looks like:

                    Service-NSO                      Service node (CFS)
                        |
                        |
      --------------------------------------------
            |           |                   |
            |           |                   |
          NSO-1       NSO-2               NSO-3      Device nodes (RFS)
            |           |                   |
            |           |                   |
      ---------      -------            ---------
      |       |      |     |            |       |
      |       |      |     |            |       |
      ce0...ce7      p0...p3            pe0...pe3    Managed devices


All the devices from the `mpls-vpn-java` example are split over three distinct
device nodes.

- NSO-1 owns all the CE routers
- NSO-2 owns all the P routers
- NSO-3 owns all the PE routers

Example Network
---------------

The example network consists of Cisco ASR 9k and Juniper core routers
(P and PE) and Cisco IOS-based CE routers.

The routers comprise the same network as in the `mpls-vpn-java` example:

![network](network.jpg)

Example Overview
----------------

The idea behind the "Layered Service Architecture" is to:

- Split the managed devices on multiple Resource Facing Service (RFS) NSO
  nodes.

- Arrange a Customer Facing Service (CFS) NSO that manage the RFS nodes that
  manages the `/devices/device tree`, i.e., the CFS NSO node manages the RFS
  NSOs.

- Create the service in a layered approach. This can be done in several
  different ways. This example uses the most common and most straightforward.

One service runs on the NSO node, which is called the Customer Facing Service,
the CFS. The provisioning code on the CFS node must:

- Figure out which devices will participate in the provisioned service.
- Figure out which RFS nodes (device nodes) host the devices.
- Instantiate proper RFS services on those device nodes.

In this example, the CFS service node YANG and RFS device node YANG models are
identical. The provisioning Java code on the service node will simply copy
itself into all its devices, which will be the three RFS NSO device nodes.

The Java provisioning code on the device nodes is identical to the code in the
original mpls-vpn example.

This will then work as follows:

1. A VPN is instantiated at the CFS, for example, `/vpn/l3vpn/[name='v1']`

2. Java `create()` code for the CFS VPN service copies itself to all RFS device
   nodes using Maapi.copyTree()

        String serviceName = service.leaf("name").valueAsString();
        NavuList managedDevices = ncsRoot.
            container("devices").list("device");

        for (NavuContainer device : managedDevices) {
            Maapi maapi = service.context().getMaapi();
            int tHandle = service.context().getMaapiHandle();


            NavuNode dstVpn = device.container("config").
                container("l3vpn", "vpn").
                list("l3vpn").
                sharedCreate(serviceName);
            ConfPath dst = dstVpn.getConfPath();
            ConfPath src = service.getConfPath();
            maapi.copyTree(tHandle, true, src, dst);
        }

   This will then re-create the same VPN three times, i.e., the code will
   create:

        /devices/device[name='nso-1]/config/vpn/l3vpn[name='v1']
        /devices/device[name='nso-2]/config/vpn/l3vpn[name='v1']
        /devices/device[name='nso-3]/config/vpn/l3vpn[name='v1']

3. Java `create()` code runs on the three RFS nodes. The code is identical to
   the code in the `mpls-vpn-java` example. Each RFS node will read the
   topology, which must be replicated on all RFS nodes, and it will try to
   provision the VPN. However, each RFS node will only touch the devices it has
   in its device tree.

4. The net result is that the entire VPN is provisioned, different NSO nodes
   provision different parts of the VPN.

Topology and QoS
----------------

Identical to the `mpls-vpn-java` example, the VPN provisioning code reads
topology and QoS settings. In the `mpls-vpn-java` example, the topology and QoS
data are store under `/qos` and `/topology`. In this example, we want to have
the data in the CFS under `/topology` and `/qos` be replicated and identical on
all RFS NSO nodes.

We implemented this as an NSO service at the CFS NSO layer that copies itself
into an identical YANG model at the RFS NSO nodes.

Thus, at the CFS NSO node, whenever the `/topology` and `/qos` are are
modified, the data gets replicated to `/topology` and `/qos` on all the RFS
nodes.

Running The Example from the CLI
--------------------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Ensure you start clean, i.e., no old configuration data is present. Stop any
NSO or simulated network nodes using the `ncs-netsim` tool if you have been
running this or another example. Outputs like 'connection refused (stop)' mean
no previous NSO was running, and 'DEVICE ce0 connection refused (stop)...' no
simulated network was running, which is good.

    make stop clean all start
    make status
    make cli

Let's start by configuring a VPN network:


    configure
    load merge terminal
    vpn {
      l3vpn volvo {
        route-distinguisher 999;
        endpoint branch-office1 {
            ce-device    ce1;
            ce-interface GigabitEthernet0/11;
            ip-network   10.7.7.0/24;
            bandwidth    6000000;
            as-number    65102;
        }
        endpoint branch-office2 {
            ce-device    ce4;
            ce-interface GigabitEthernet0/18;
            ip-network   10.8.8.0/24;
            bandwidth    300000;
            as-number    65103;
        }
        endpoint main-office {
            ce-device    ce6;
            ce-interface GigabitEthernet0/11;
            ip-network   10.10.1.0/24;
            bandwidth    12000000;
            as-number    65101;
        }
      }
    }
    ^D

Before we send anything to the network, let's see what would be sent if
we committed.

    commit dry-run outformat native
    native {
        device {
            name nso-1
            data <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0"
                      message-id="1">
            <edit-config xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0">
              <target>
                <running/>
              </target>
              <test-option>test-then-set</test-option>
              <error-option>rollback-on-error</error-option>
              <with-inactive xmlns="http://tail-f.com/ns/netconf/inactive/1.0"/>
              <config>
                <vpn xmlns="http://com/example/l3vpn">
                  <l3vpn>
                    <name>volvo</name>
        ...

This shows the NETCONF `edit-config` payload that the CFS NSO node will send to
each of the RFS NSO nodes:

    commit

We can log in to the RFS NSO nodes:

    make cli-nso-1
    request vpn l3vpn volvo get-modifications

We see that the `nso-1` node has only modified the CE devices, whereas:

    make cli-nso-3
    request vpn l3vpn volvo get-modifications

The `nso-3` node has modified the affected `PE` routers.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Rearchitecting an Existing VPN Application for LSA
+ The `demo.sh` script