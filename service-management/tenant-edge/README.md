Tenant Edge Closed-Loop Multivendor Example
===========================================

Enterprise automation is not just about pushing config. It is about delivering
an intent-driven service with guardrails, then keeping that service correct as
the network changes. This example builds and operates a multivendor Tenant Edge
Service with NSO across Cisco IOS-XR and Arista EOS.

The service provisions a tenant VRF, BGP routing, and export policy on both
platforms. After deployment, the example walks through two Day-2 workflows:

- standard drift detection, sync-from, and re-deploy remediation
- confirm-network-state handling of out-of-band changes with
  `sync-from-device`, `sync-to-device`, and `manage-by-service`

The result is a compact NSO example that shows both closed-loop remediation and
the more nuanced out-of-band interoperation workflow described in the NSO
Operation & Usage guide.


What The Example Covers
-----------------------

- Model-first service intent for a tenant VRF with BGP and simple prefix
  control
- Multivendor rendering to Cisco IOS-XR and Arista EOS
- Safe update workflow with preview/diff before apply
- Rollback-ready Day-2 operations
- Closed-loop drift detection and remediation
- `confirm-network-state` enabled by default for the lab
- Service out-of-band policy for the `tenant-edge-service` servicepoint
- `sync-from-device` handling for IOS-XR interface MTU changes
- `sync-to-device` handling for IOS-XR interface IPv4 address changes
- `manage-by-service` handling for IOS-XR prefix-set entries

The package contains two top-level objects:

- `tenant-edge-catalog/site`: brownfield site inventory that maps a logical
  site to a device, interface, local interface address/prefix length, and
  BGP peer
- `tenant-edge`: the service intent that references those sites


Topology
--------

The lab uses two netsim devices:

- `xr-pe-1`: Cisco IOS-XR tenant edge
- `eos-leaf-1`: Arista EOS tenant edge

The seeded catalog provides two pre-modeled attachment sites:

- `xr-site` on `xr-pe-1`
- `eos-site` on `eos-leaf-1`

The service then targets those sites with a single intent instance.


Service Model
-------------

`tenant-edge-service.yang` models:

- `customer`
- `tenant-id`
- `vrf-name`
- `local-as`
- `max-prefix`
- `export-prefix` list
- Two catalog-backed `site` references

Built-in guardrails include:

- Exactly two sites are required
- `tenant-id` is range-limited
- `max-prefix` is range-limited
- Exporting `0.0.0.0/0` is blocked
- The selected sites must terminate on different devices
- Each peer address must live inside its site subnet


Build And Start
---------------

Start the example with:

    $ make stop clean all start

This will:

- Build the IOS-XR and Arista netsim NEDs
- Build the `tenant-edge-service` package
- Create the `nso-run/` run-time directory.
- Create the two-device netsim network
- Seed the site catalog
- Initialize both devices
- Start netsim and NSO
- Do an initial `sync-from`
- Load the `confirm-network-state` and `services out-of-band policy`
  configuration used by the example

Connect with:

    $ ncs_cli -u admin -C

Useful initial checks:

    # show devices device * last-in-sync
    # show running-config tenant-edge-catalog
    # show running-config devices global-settings confirm-network-state
    # show running-config services out-of-band policy tenant-edge-service


Run The Demo
------------

Interactive walkthrough:

    $ make demo

Non-interactive run:

    $ make demo-nonstop

The demo walks through:

- Site catalog and policy inspection
- Service creation and dry-run rendering
- Multivendor deployment verification
- Safe update preview and apply
- Rollback of an undesired change
- Plain multivendor drift on IOS-XR and EOS
- Drift detection, remediation, and verification
- IOS-XR out-of-band changes handled by confirm-network-state policy
- Inspection of accepted, rejected, and preserved out-of-band outcomes

The example uses `nso-run/logs/out-of-band-policy.log` to make the policy
outcomes explicit:

- The out-of-band MTU is accepted into CDB
- The interface IP is restored to the service value
- The extra prefix-set entry is preserved.

The manual walkthrough below includes an optional
`tenant-edge acme-blue re-deploy dry-run` step that shows the accepted MTU
would be removed by re-deploy while the extra prefix-set entry is retained.


Manual Walkthrough
------------------

Inspect the loaded policy:

    $ ncs_cli -u admin -C
    # show running-config devices global-settings confirm-network-state
    # show running-config services out-of-band policy tenant-edge-service

Create the tenant intent:

    # config
    (config)# tenant-edge acme-blue customer ACME tenant-id 101 local-as \
    65010 vrf-name ACME-101 max-prefix 50 export-prefix [ 10.101.0.0/16 \
    10.101.10.0/24 ] site [ xr-site eos-site ]
    (config)# commit dry-run
    (config)# commit

Inspect the generated multivendor config:

    (config)# tenant-edge acme-blue get-modifications outformat xml

Preview a Day-2 update:

    (config)# tenant-edge acme-blue max-prefix 75
    (config)# tenant-edge acme-blue export-prefix 10.101.20.0/24
    (config)# commit dry-run
    (config)# commit

Rollback the last change:

    (config)# rollback-files apply-rollback-file id 0
    (config)# commit
    (config)#exit
    # exit


Introduce plain multivendor drift on EOS and IOS-XR, then remediate it with
`sync-from` plus service `re-deploy`:

    $ ncs-netsim --dir nso-run/netsim cli-c eos-leaf-1
    # config
    (config)# router bgp 65010
    (config)# vrf ACME-101
    (config)# neighbor 172.16.102.2 maximum-routes 5
    (config)# commit
    (config)# exit
    # exit

    $ ncs-netsim --dir nso-run/netsim cli-c xr-pe-1
    # config
    (config)# router bgp 65010
    (config)# vrf ACME-101
    (config)# neighbor 172.16.101.2
    (config)# address-family ipv4 unicast
    (config)# maximum-prefix 5
    (config)# commit
    (config)# exit
    # exit

    $ ncs_cli -u admin -C
    # devices device eos-leaf-1 check-sync
    # devices device xr-pe-1 check-sync
    # devices device eos-leaf-1 sync-from dry-run
    # devices device xr-pe-1 sync-from dry-run
    # devices sync-from
    # tenant-edge acme-blue re-deploy dry-run
    # tenant-edge acme-blue re-deploy
    # exit

Introduce out-of-band IOS-XR changes, then process them with the tenant-edge
policy:

    $ ncs-netsim --dir nso-run/netsim cli-c xr-pe-1
    # config
    (config)# interface GigabitEthernet 0/0/0/1
    (config)# mtu 1600
    (config)# ipv4 address 172.16.101.9 255.255.255.252
    (config)# exit
    (config)# prefix-set TE-101-PS
    (config)# 10.101.0.0/16
    (config)# 10.101.10.0/24
    (config)# 10.101.99.0/24
    (config)# end-set
    (config)# commit
    (config)# exit
    # devices device xr-pe-1 sync-from dry-run
    # devices device xr-pe-1 sync-from
    # show running-config devices device xr-pe-1 config interface \
    GigabitEthernet 0/0/0/1
    # show running-config devices device xr-pe-1 config prefix-set TE-101-PS
    # tenant-edge acme-blue re-deploy dry-run
    $ tail -n 12 nso-run/logs/out-of-band-policy.log

After the `sync-from`:

- The IOS-XR MTU change is accepted into NSO because the policy says
  `sync-from-device`
- The IOS-XR interface IP address change is rejected and restored to the
  service-intended value because the policy says `sync-to-device`
- The extra IOS-XR prefix-set entry is preserved across service re-deploy
  because the policy says `manage-by-service`
- The OOB policy log shows the rule matches and the rejected IP rewrite
- The re-deploy preview shows `no mtu 1600`, demonstrating that
  `sync-from-device` adopted the MTU into CDB but did not turn it into
  service intent, while the extra prefix-set entry is not removed


Cleanup
-------

To stop NSO and the netsim devices and remove generated files:

    $ make stop clean


Further Reading
---------------

- NSO Development Guide: Implementing Services, Services Deep Dive
- NSO Operation & Usage Guide: Out-of-band Interoperation