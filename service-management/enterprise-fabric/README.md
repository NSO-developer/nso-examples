Enterprise Fabric NSO Example
=============================

This example turns multiple network automation tasks into a single NSO
service-management example for enterprise network engineers, automation
engineers, NetOps, and SRE teams.

Instead of separate host files, variables, ad-hoc commands, and playbooks, NSO
uses a model-driven catalog plus one intent service, split into two parts:

- `enterprise-fabric-catalog`: brownfield inventory for devices, links,
  multivendor NTP endpoints, and core iBGP peers
- `enterprise-fabric`: service intent for the full IOS and Junos enterprise
  fabric, including Cisco core iBGP

The example also highlights what NSO adds beyond a playbook flow:

- Service dry-run previews before change
- Service updates and rollback history
- Closed-loop drift detection and remediation
- `confirm-network-state` plus `services out-of-band policy`
- Compliance reporting in text format for demo-friendly audit output


What The Example Covers
-----------------------

- Brownfield onboarding with a seeded device and link catalog
- Cisco IOS and Juniper Junos rendering from one service model
- Simple banner, loopback, and interface rollout
- Edge static routing
- Multivendor NTP rollout
- Enterprise Day-0 management profile for domain naming, DNS, syslog,
  NTP sourcing, and IOS config archive
- WAN link metadata with carrier, circuit ID, bandwidth, and
  primary/backup semantics
- Cisco core OSPF configuration and deployment
- Hybrid core setup with Junos OSPF and Cisco core iBGP
- Drift detection with `check-sync`, `sync-from`, and service `re-deploy`
- Out-of-band handling with `sync-from-device` and `sync-to-device`
- Compliance reports for service and device out-of-sync state before and after
  remediation


Topology
--------

The example uses ten netsim devices, representing these enterprise platforms:

- Cisco IOS XE WAN or core aggregation routers:
  `lon-core1`, `man-core1`, `nyc-core1`, `sgp-core1`
- Cisco IOS XE branch or regional edge routers:
  `lon-br1`, `man-br1`, `sgp-br1`, `nyc-br1`
- Juniper Junos transit routers:
  `lon-tr1`, `nyc-tr1`

```
    lon-br1 ---- lon-core1 ----- man-core1 ---- man-br1
                    |  \         /  |
                    |    lon-tr1    |
                    |       |       |
                    |    nyc-tr1    |
                    |  /         \  |
    sgp-br1 ---- sgp-core1 ----- nyc-core1 ---- nyc-br1
```

The topology is intentionally enterprise-like rather than provider-like:

- Each edge router connects to one branch router
- The Cisco core forms a primary direct core ring:
  `lon-core1 -> man-core1 -> nyc-core1 -> sgp-core1 -> lon-core1`
- The Junos routers `lon-tr1` and `nyc-tr1` form a transit layer between the
  Cisco core routers
- The direct core ring links are modeled as primary WAN paths
- The Junos transit path is modeled as backup WAN capacity with higher
  OSPF cost

Service Intent
--------------

The service renders a single intended enterprise end state:

- banner or login message
- management profile with domain name, DNS, syslog, NTP source, and
  IOS config archive
- loopback router-id addressing
- physical interface addressing and descriptions
- edge static default routes
- NTP servers on IOS and Junos
- Cisco core OSPF in area 0
- Junos transit OSPF in area 0 with backup-path cost and BFD
- Cisco core iBGP over loopbacks


Build and Start
---------------

Build and start the example with:

    $ make stop clean all start

This will:

- Build the Cisco IOS and Junos example NEDs
- Build the `enterprise-fabric-service` package
- Create the NSO run-time directory `nso-run/`
- Create and rename the ten-device netsim network
- Seed the brownfield catalog
- Initialize the startup state for each netsim device
- Start netsim and NSO
- Do an initial `sync-from`
- Load `confirm-network-state` and the example OOB policy
- Load a focused config compliance report definition


Run The Demo
------------

Interactive walkthrough:

    $ make demo

Non-interactive run:

    $ make demo-nonstop

The demo walks through:

- Inventory and policy inspection
- Full enterprise fabric intent creation with an enterprise management profile
- Preview, commit, rollback, and audit history
- Configuration drift detection and remediation
- IOS out-of-band changes processed under confirm-network-state
- Compliance report runs rendered as plain text during drift and OOB handling


Manual Walkthrough
------------------

Inspect the seeded catalog and OOB policy:

    $ ncs_cli -u admin -C
    # show running-config enterprise-fabric-catalog
    # show running-config devices global-settings confirm-network-state
    # show running-config services out-of-band policy \
      enterprise-fabric-service
    # show running-config compliance reports report enterprise-fabric-audit

Create the service:

    # config
    (config)# enterprise-fabric corp-lab ntp-server [ ntp-a ntp-b ]
    (config)# enterprise-fabric corp-lab management-profile \
      domain-name corp.example
    (config)# enterprise-fabric corp-lab management-profile \
      dns-server [ 192.0.2.53 192.0.2.54 ]
    (config)# enterprise-fabric corp-lab management-profile \
      syslog-server 192.0.2.200
    (config)# enterprise-fabric corp-lab management-profile \
      syslog-severity informational
    (config)# commit dry-run
    (config)# commit

That initial commit renders the full intended enterprise fabric, including the
management baseline, edge static routing, Cisco core OSPF, Junos transit OSPF,
and Cisco core iBGP.

The management profile is intentionally enterprise-oriented:

- `domain-name corp.example`
- Dual DNS servers at `192.0.2.53` and `192.0.2.54`
- remote syslog at `192.0.2.200`
- NTP sourced from Loopback0 on IOS
- IOS config archive with syslog notification

The seeded brownfield catalog also carries WAN link metadata:

- Provider and circuit ID for each routed link
- Bandwidth in Mbps
- `primary` or `backup` path role
- `routing-role` to separate edge-static, core-primary, and
  transit-backup links
- `bfd-enabled` per link

The service uses those link attributes to:

- Set interface bandwidth on IOS
- Prefer primary links with lower OSPF cost
- De-prefer backup transit links with higher OSPF cost
- Enable BFD on IOS and Junos routed OSPF adjacencies

Preview a small Day-2 update and then roll it back:

    (config)# enterprise-fabric corp-lab banner \
      "Authorized access only. Managed by NSO change window."
    (config)# commit dry-run
    (config)# commit
    (config)# rollback-files apply-rollback-file id 0
    (config)# commit
    (config)# exit
    # exit

Introduce drift on IOS and Junos, then remediate it:

    $ ncs-netsim --dir nso-run/netsim cli-c lon-br1
    # config
    (config)# banner login "Drifted banner outside NSO."
    (config)# commit
    (config)# exit
    # exit

    $ ncs-netsim --dir nso-run/netsim cli-c lon-tr1
    # config
    (config)# configuration system login \
      message "Drifted message outside NSO."
    (config)# commit
    (config)# exit
    # exit

    $ ncs_cli -u admin -C
    # devices device lon-br1 check-sync
    # devices device lon-tr1 check-sync
    # devices device lon-br1 sync-from dry-run
    # devices device lon-tr1 sync-from dry-run
    # compliance reports report enterprise-fabric-audit run \
      title "Enterprise fabric drift audit" outformat text
    # devices device lon-br1 sync-from
    # devices device lon-tr1 sync-from
    # enterprise-fabric corp-lab re-deploy dry-run
    # enterprise-fabric corp-lab re-deploy
    # compliance reports report enterprise-fabric-audit run \
      title "Enterprise fabric post-remediation audit" outformat text
    # exit

Introduce out-of-band IOS changes covered by the service policy:

    $ ncs-netsim --dir nso-run/netsim cli-c lon-core1
    # config
    (config)# interface GigabitEthernet 0/2
    (config-if)# description Emergency direct core path
    (config-if)# exit
    (config)# interface Loopback 0
    (config-if)# ip address 10.255.255.1 255.255.255.255
    (config-if)# exit
    (config)# commit
    (config)# exit
    # exit

    $ ncs_cli -u admin -C
    # devices device lon-core1 sync-from
    # devices device lon-core1 compare-config
    # compliance reports report enterprise-fabric-audit run \
      title "Enterprise fabric OOB audit" outformat text
    # enterprise-fabric corp-lab re-deploy dry-run
    # enterprise-fabric corp-lab re-deploy
    # compliance reports report enterprise-fabric-audit run \
      title "Enterprise fabric post-OOB audit" outformat text
    # exit

After the `sync-from`:

- the link description is accepted into NSO because the policy says
  `sync-from-device`
- the loopback address is rejected and restored because the policy says
  `sync-to-device`

The compliance report files are written under:

    ./nso-run/state/compliance-reports/

The demo cats the latest text result file, typically `.txt`, so the audit
output is easy to show in plain CLI sessions.


Cleanup
-------

To stop NSO and the netsim devices and remove generated files:

    $ make stop clean


Further Reading
---------------

- NSO Development Guide: Implementing Services, Services Deep Dive
- NSO Operation & Usage Guide: Out-of-band Interoperation, Compliance Reporting
