SRv6 Multivendor VPN Services with NSO Netsim
=============================================

This example demonstrates NSO orchestration of SRv6 services in a multivendor
service-provider network built entirely with `ncs-netsim`. It is a netsim-only
multivendor variant of the `srv6-demo` example.

Unlike `srv6-demo`, this example does not have the option to use XRd, Docker,
or a real forwarding/control plane. The focus here is multivendor service
modeling, package structure, and realistic device configuration rendering for
Cisco IOS-XR, Juniper Junos, and Nokia SR OS.


Multivendor Topology
--------------------

The sample network contains eight netsim devices:

  - `core-1`: Nokia SR OS SRv6 core router
  - `pe-01`: Juniper Junos SRv6 PE router
  - `core-2`, `core-3`, `core-4`, `core-5`: Cisco IOS-XR core routers
  - `pe-02`: Cisco IOS-XR SRv6 PE router
  - `ce-1-3`: Cisco IOS-XR managed CE router

The topology is:

```
                 core-5 (IOS-XR RR)
                  /             \
     pe-01 --- core-1 ======== core-2 --- pe-02 --- ce-1-3
    (Junos)   (SR OS)         (IOS-XR)   (IOS-XR)
                  \             /
                 core-4 ==== core-3
                (IOS-XR)    (IOS-XR)
```

`pe-01` is dual-homed to `core-1` and `core-4`.
`pe-02` is dual-homed to `core-2` and `core-3`.
`ce-1-3` is the only managed CE in the inventory. The other customer attachment
points are modeled as inventory ports only.


What the Example Shows
----------------------

The example contains the following service packages:

  - `core-network`: shared topology and addressing data
  - `inventory`: customer, port, and CE inventory data
  - `srv6-node`: base SRv6 transport configuration for core and PE devices
  - `eline-service`: point-to-point Ethernet service
  - `l2vpn-service`: multipoint Ethernet VPN service
  - `l3vpn-service`: layer-3 VPN service

The multivendor mapping is:

  - `srv6-node` renders device-specific SRv6 transport configuration on all
    core and PE devices, including Nokia SR OS on `core-1`, Junos on `pe-01`,
    and IOS-XR on the remaining core and PE routers.
  - `eline`, `l2vpn`, and `l3vpn` render service configuration on the PE
    devices that host customer-facing ports: Junos `pe-01` and IOS-XR `pe-02`.
  - `l3vpn` can also generate managed CE configuration when a selected
    inventory port is connected to `ce-1-3`.


Build and Start
---------------

From the example directory:

    $ make stop clean all start

Startup can take a while because the Junos netsim NED is relatively large.
That is expected for this example.

This will:

  - build the IOS-XR, Junos, and Nokia netsim NED packages
  - build the example service packages
  - create `nso-run/`
  - create the eight-device netsim network
  - initialize the devices with the example startup configuration
  - start netsim and NSO
  - perform an initial `sync-from`

Connect to NSO:

    $ ncs_cli -u admin -C

Useful initial checks:

    # show devices device * last-in-sync
    # show packages
    # show running-config devices device core-1 device-type
    # show running-config devices device pe-01 device-type
    # show running-config devices device pe-02 device-type


Run the Demo
------------

For an interactive walkthrough:

    $ make demo

For a non-interactive run:

    $ make demo-nonstop

The demo script resets the lab, starts the netsim network, verifies the
multivendor device types, provisions the SRv6 transport, creates sample
`eline` and `l3vpn` services, shows an `l2vpn` dry-run, and then inspects
the resulting Junos, Nokia, and CE configuration.


Manual Walkthrough
------------------

Provision the base SRv6 transport:

    # config
    (config)# core-network provision
    (config)# commit

Inspect the generated multivendor SRv6 configuration:

    (config)# core-network services srv6-node core-1 get-modifications \
    outformat xml
    (config)# core-network services srv6-node pe-01 get-modifications \
    outformat xml
    (config)# core-network services srv6-node pe-02 get-modifications \
    outformat xml

Create an E-Line service between Junos `pe-01` and IOS-XR `pe-02`:

    (config)# eline sample-eline customer Tail-f ports [ pe-01-3 pe-02-4 ]
    (config)# commit dry-run outformat native
    (config)# commit

Preview a multipoint L2VPN service without committing it:

    (config)# l2vpn sample-l2vpn customer Tail-f ports \
    [ pe-01-2 pe-02-2 pe-02-3 ]
    (config)# commit dry-run outformat native
    (config)# no l2vpn sample-l2vpn

Create a basic L3VPN between a Junos PE access port and an IOS-XR PE access
port:

    (config)# l3vpn sample-l3vpn customer Tail-f link 1 port pe-01-2
    (config)# l3vpn sample-l3vpn customer Tail-f link 2 port pe-02-2
    (config)# commit dry-run outformat native
    (config)# commit

Add a third L3VPN link toward the managed CE using BGP peering:

    (config)# l3vpn sample-l3vpn link 3 port pe-02-3 bgp-peering enabled \
    peer-as 65010
    (config)# commit dry-run outformat native
    (config)# commit


Optional: Resource Manager 5
----------------------------

This example can optionally use the NSO Resource Manager 5 package for
resource allocation. No service code changes are needed for that. The Python
callbacks already try to import `resource_manager.service.Allocator` and use
it automatically when the `resource-manager` package is present.

With RM5 enabled:

  - `eline`, `l2vpn`, and `l3vpn` allocate VNI values from a Resource Manager
    ID pool instead of the local fallback allocator.
  - `l3vpn` allocates automatic customer link subnets from a per-customer
    Resource Manager IP pool instead of relying on the deterministic
    `10.1.<link-id>.0/24` fallback.

Without RM5:

  - VNI allocation falls back to the local inventory-backed algorithm in
    `shared-code/python/vpn.py`. It keeps VNI values stable across redeploys,
    but it does not reuse released VNIs.
  - `l3vpn` falls back to `10.1.<link-id>.0/24` when `link/subnet` is not set.
    That is simple and predictable, but the same customer can end up with
    overlapping subnets if it has multiple separate L3VPN instances.

To add RM5 persistently to this example, unpack or copy the separately
downloaded `resource-manager` package (version 5.0 or later) into the example
`package-store/` directory before building:

    $ cp -R /path/to/resource-manager ./package-store/
    $ make stop clean all start

That is the recommended approach for this example because `make all` copies
everything from `package-store/` into `nso-run/packages/`.

If you already have the lab running, you can either rebuild as above or copy
the package directly into `nso-run/packages/` and reload packages:

    $ cp -R /path/to/resource-manager ./nso-run/packages/
    $ ncs_cmd -u admin -c 'maction /packages/reload'

The `nso-run/packages/` approach is convenient for a running instance, but it
is not persistent across `make clean`.

Verify that RM5 is loaded:

    # show packages package resource-manager


RM5 VNI Allocation
------------------

Before provisioning `eline`, `l2vpn`, or `l3vpn` with RM5, configure an ID
pool named `customer-vni`:

    # config
    (config)# load merge terminal
    resource-pools id-pool customer-vni
     range start 1000
     range end 2000
    !
    ^D
    (config)# commit

After that, new or redeployed VPN services request VNIs from this pool and
RM5 stores the allocation under the service instance. In a clean walkthrough,
separate services consume distinct values from the configured range, for
example `1000` and `1001`.

You can inspect the result with:

    # show running-config resource-pools id-pool customer-vni

You should see allocations such as `eline-sample-eline` or
`l3vpn-sample-l3vpn` tied to the corresponding service instances.


RM5 L3VPN Subnet Allocation
---------------------------

The `l3vpn` service can also use RM5 for automatic IPv4 subnet allocation.
No extra pool bootstrap is required for that. When a customer link needs an
automatically assigned subnet, the service creates a per-customer pool named
`customer-vpn-subnets-<customer-cid>` on demand and seeds it with
`10.1.0.0/16`.

RM5 then allocates a unique `/24` from that customer-specific pool for each
automatic L3VPN link. For example, a two-link L3VPN can receive
`10.1.0.0/24` and `10.1.1.0/24`. That avoids subnet collisions when the same
customer has multiple L3VPN services.

If a link explicitly sets a subnet outside the managed `10.1.0.0/16` range,
the service keeps that explicit subnet and does not ask RM5 to allocate a
replacement for that link.

When manually repeating a test, deleting and immediately recreating a service
with the same name can temporarily hit an RM5 cache entry for that old
allocation name. If that happens, wait briefly or use a new service name.


What To Inspect
---------------

Because this is a netsim-only example, verification is configuration-oriented.
You inspect the configuration rendered by NSO rather than a live control or
data plane.

Useful checks after provisioning:

    # show running-config devices device pe-01 config configuration \
    routing-options | display xml
    # show running-config devices device pe-01 config configuration \
    routing-instances | display xml
    # show running-config devices device core-1 config router Base \
    segment-routing segment-routing-v6 | display xml
    # show running-config devices device core-1 config router Base \
    isis 0 | display xml
    # show running-config devices device pe-02 config | display xml
    # show running-config devices device ce-1-3 config | display xml

These commands highlight the main multivendor result:

  - Junos `pe-01` receives SRv6 PE configuration and Junos-native EVPN/L3VPN
    service rendering.
  - Nokia SR OS `core-1` receives SRv6 locator and IS-IS advertisement
    configuration appropriate for a transit SRv6 core node.
  - IOS-XR `pe-02` receives the corresponding XR service configuration.
  - Managed CE `ce-1-3` receives service-driven CE configuration for the
    `l3vpn` BGP peering use case.


Cleanup
-------

To stop NSO and the netsim devices and remove generated files:

    $ make stop clean


Further Reading
---------------

+ NSO Development Guide: Implementing Services, Services Deep Dive
