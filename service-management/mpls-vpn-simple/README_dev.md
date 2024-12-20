
Simple MPLS Layer3 VPN Example
==============================

This example illustrates Layer3 VPNs in a service provider MPLS network.

Example Network
---------------

The example network consists of Cisco ASR 9k core routers (P and PE) and Cisco
IOS-based CE routers.

Service Development Process
---------------------------

This section outlines the development process that was used when
creating this example.

1. Define correct device configurations. Start by creating/defining the device
   configurations you want in your network for a correctly provisioned service.
   This is typically done together with the network engineers. Look in
   directory `STEP1` for config snippets.
2. Create the service model. Analyze the device configurations to see what
   parameters can be abstracted to a service model and use that together with
   the knowledge of how requests will come to NSO to create the service. Create
   your service YANG model. See `packages/l3vpn/src/yang/l3vpn.yang`.
3. Create a service template. Use the config snippets created in step 1 to
   configure either a `ncs-netsim` simulated device or a real one. If you use a
   netsim simulated device, `show running-config | display xml` to get the
   template output. If you use a real device, connect it to NSO, run device
   `sync-from`, and do a
   `show running-config devices device <mydevice> | display xml`. Add all the
   XML snippets to the file: `packages/l3vpn/templates/l3vpn.xml`. Replace the
   values you configured with reference to the service model's parameters.
