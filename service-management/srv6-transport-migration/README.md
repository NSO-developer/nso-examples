SRv6 Transport Migration Multivendor Netsim Example
===================================================

This example demonstrates how Cisco NSO can own a service lifecycle across a
changing multivendor transport network. It starts from a realistic brownfield
state, where the provider still operates MPLS/RSVP-TE or SR-MPLS, then shows
how selected services can move to SRv6 later without turning the migration into
a collection of one-way device playbooks.

If you are evaluating NSO for brownfield transport automation, the story to
look for is:

  - operate today's MPLS/RSVP-TE or SR-MPLS transport first
  - enable SRv6 underlay when the network is ready
  - migrate selected customer services by changing service intent, not by
    hand-editing device configuration
  - use native dry-runs, `check-sync`, service `re-deploy`, rollback, and
    assurance gates to keep NSO in control of the lifecycle
  - see Cisco IOS XR, Juniper Junos, and Nokia SR OS participating in one
    transport migration story

What this demo helps you evaluate:

  - NSO keeps a service-level source of truth while the transport underlay
    changes underneath it.
  - The same customer service can run over RSVP/MPLS-TE, SR-MPLS, or
    SRv6 transport profiles.
  - NSO can explain the next safe migration step before it commits anything.
  - NSO can show native device diffs, detect out-of-band drift, reconcile live
    device state, and repair service-owned intent.
  - NSO can use assurance and performance monitoring (PM) health as gates, so
    an unsafe migration is blocked or rolled back instead of blindly pushed.
  - Standard IETF models are used where they fit the northbound story, while
    local NSO services handle executable lifecycle and vendor rendering.

This README is written for two audiences:

  - If you are evaluating NSO for your network, start with
    `Customer Evaluation Story` and `Run The Demo`.
  - NSO developers who want to reuse the example should use
    `NSO Developer Reference Path`, `YANG Model Inventory`, `Packages`,
    `Manual Walkthrough`, and `Payload Files`.

Vendor role matrix:

| Vendor / NED family | Lab devices | What this demo shows |
| --- | --- | --- |
| Cisco IOS XR CLI netsim | `pe-02`, `core-3` through `core-5`, managed CEs | SRv6 underlay, RSVP/MPLS-TE scaffolding, SR policy and ODN color steering, IOS XR PM delay-measurement, model-driven telemetry, DIA, L2VPN, L3VPN, and NSS service rendering. |
| Cisco IOS XR NETCONF netsim | `core-2` | NETCONF-managed IOS XR core participation, discovered `ietf-yang-push` capability, and southbound datastore subscription lifecycle with telemetry kickers. |
| Juniper Junos NETCONF netsim | `pe-01` | SRv6 underlay, RSVP/MPLS-TE, IETF TE tunnel head-end rendering, source-packet-routing policy/template rendering, L3VPN color tagging and resolution-map steering, RPM probes, analytics telemetry, and L2/L3 VPN service participation. |
| Nokia SR OS CLI netsim | `core-1` | Multivendor core participation, RSVP/MPLS-TE and SRv6 transport topology, RFC 8795 TE topology export with SRLG/metric context, OAM-PM/TWAMP-light endpoint probe scaffolding, and telemetry rendering. |

The current netsim lab intentionally gives IOS XR and Junos the strongest
SR policy/ODN head-end roles because this reference example includes
validated renderer templates for the IOS XR and Junos netsim NED shapes used
by the lab. SR OS is still visible as a realistic transport/core participant,
but it is not yet implemented as an equivalent SR policy/ODN head-end in this
reference example.


Why This Example Exists
-----------------------

Service providers often need to keep operating an existing MPLS/RSVP-TE or
SR-MPLS transport network while they prepare SRv6 underlay and service
steering. This example keeps the multivendor netsim foundation from
`srv6-multivendor-netsim`, then adds a practical, testable transport migration
slice that can be used as a reference implementation.

The goal is to help you recognize your own migration problem:

  - You have more than one vendor in the transport domain.
  - You already have working MPLS/RSVP-TE or SR-MPLS services.
  - You want SRv6, but not as a disruptive overnight replacement.
  - You need service ownership, auditability, and rollback while the network
    is in transition.
  - You want assurance evidence to influence migration decisions.

This example turns that migration problem into a service lifecycle exercise:
describe the customer service intent once, let NSO render the right
multivendor device changes, and keep using the same service model to plan,
preview, migrate, validate, repair, or roll back as the transport network
evolves.


Customer Evaluation Story
-------------------------

If you are evaluating NSO, the strongest story is not "SRv6 provisioning" by
itself. The stronger story is whether NSO can own the service lifecycle while
the transport network changes underneath it.

The recommended approach is:

  - Phase 1: adopt today's transport network. Start with an MPLS/RSVP-TE or
    SR-MPLS service and verify that no SRv6 locator or SRv6 VPN realization is
    required yet.
  - Plan before changing the network. Run the migration planner/advisor and
    verify that NSO explains why the service is not ready for SRv6 yet,
    including missing underlay, path, SRLG, latency, PM, and assurance context.
  - Phase 2: enable SRv6 selectively. Add SRv6 underlay only where the service
    needs it, then observe the planner changing from "stage underlay first" to
    "ready."
  - Migrate service intent, not individual device snippets. Change the
    customer slice from `rsvp-te` or `sr-mpls` to `srv6`, then inspect the
    native device dry-run before execution.
  - Keep NSO accountable after the change. Simulate an out-of-band device
    change, use device `check-sync` and `sync-from dry-run` to see the live
    drift, then use service `check-sync` and `re-deploy` to repair the
    service-owned intent.
  - Use assurance as a migration gate. In the canary demo, impaired health
    input blocks or rolls back unsafe migration, then allows migration once
    the evidence is clean.
  - Close the loop. In the telemetry/nano-service demo, NSO reacts to streamed
    device drift for the lifetime of the service and verifies that the service
    returns to operational health.

The key evaluation point is that the same service can survive a transport
technology transition, an out-of-band change, and a failed canary without
turning the operator workflow into a set of disconnected playbooks.


NSO Developer Reference Path
----------------------------

For an NSO developer, the value of this example is that the evaluation story
is implemented as ordinary, inspectable NSO packages, YANG models,
templates, Python callbacks, and payloads. Use it as a reference
when building a service that must combine brownfield adoption, multivendor
rendering, service migration, and operational guardrails.

Start here:

  - Run `make demo-nonstop` for the broad technical walkthrough that exercises
    the model inventory, topology export, SRv6 underlay, RSVP/MPLS-TE, SR
    policy/ODN, PM/assurance, DIA, L2VPN, L3VPN, and NSS composition.
  - Run `make demo-brownfield-nonstop` or
    `make demo-brownfield-sr-mpls-nonstop` to focus on service lifecycle:
    Phase 1 brownfield operation, Phase 2 SRv6 enablement, guarded migration,
    device drift, `sync-from dry-run`, service `check-sync`, and repair.
  - Run `make demo-brownfield-canary-nonstop` to study assurance-aware
    migration gates, OOB abort, XML compliance templates, compliance report
    `re-run`, and post-check behavior.
  - Run `make demo-southbound-closed-loop-nonstop` to study a nano-service
    based closed loop using NETCONF/YANG-Push, telemetry kickers,
    service-owned OOB policy, and `re-deploy confirm-network-state`.
  - Read `YANG Model Inventory` before reusing northbound models, because the
    example intentionally separates IETF modules from local lifecycle wrappers
    and vendor rendering hints.
  - Read `Packages` and `Payload Files` to map each demo behavior back to the
    package, template, Python code, and XML payload that implements it.

The developer takeaway should be: the demo is not only a script. It is a
reference layout for splitting customer intent, transport topology, vendor
rendering, resource allocation, assurance input, and lifecycle actions into
small NSO packages that can be studied independently.


YANG Model Inventory
--------------------

Where practical, this example uses standard IETF YANG models for northbound
intent and topology views, while keeping NSO service lifecycle, vendor
rendering hints, and demo-only orchestration in local Tail-f example modules
instead of patching servicepoints directly into standard modules.

The model set is split into four groups:

  - active IETF draft modules used as northbound models where no RFC exists
    yet
  - published RFC/IANA/IEEE modules vendored as support or northbound models
  - local NSO wrapper and extension modules that add service lifecycle,
    validation, actions, and renderer hints around the standard models
  - inherited/local executable service modules from `srv6-multivendor-netsim`
    and this transport migration example

Active IETF draft modules:

  - `ietf-te@2026-03-26` is taken from
    `draft-ietf-teas-yang-te-44`. It is the northbound TE tunnel model used
    by `payload/ietf-te-rsvp.xml`.
  - `ietf-te-types@2026-02-06` is taken from
    `draft-ietf-teas-rfc8776-update-22` and is used by `ietf-te` and the TE
    topology models.

Published RFC/IANA/IEEE modules:

  - `ietf-te-topology@2020-08-06` from RFC 8795 models the exported TE
    topology view.
  - `ietf-network@2018-02-26` and
    `ietf-network-topology@2018-02-26` from RFC 8345 provide the generic
    topology base used by RFC 8795.
  - `ietf-l2vpn-ntw@2022-09-20` from RFC 9291 is the L2NM northbound model
    composed into the inherited `eline` service.
  - `ietf-l3vpn-ntw@2022-02-14` from RFC 9182 is the L3NM northbound model
    composed into the inherited `l3vpn` service.
  - `ietf-vpn-common@2022-02-11` from RFC 9181 provides common VPN types
    used by the L2NM and L3NM modules.
  - `ietf-routing-types@2017-12-04`, `ietf-interfaces@2018-02-20`,
    `ietf-key-chain@2017-06-15`, `ietf-packet-fields@2019-03-04`,
    `ietf-ethertypes@2019-03-04`, and `ietf-netconf-acm@2018-02-14` are
    vendored support modules required by the VPN and TE model graph.
  - `iana-bgp-l2-encaps@2022-09-20`,
    `iana-pseudowire-types@2022-09-20`, and
    `ietf-ethernet-segment@2022-09-20` are RFC 9291 support modules.
  - `ieee802-dot1q-types@2018-03-07` provides VLAN and dot1q types used by
    the L2VPN model.

The model graph also imports standard base modules such as `ietf-inet-types`
and `ietf-yang-types` from the NSO installation instead of vendoring local
copies in the example.

Local wrapper, annotation, and extension modules:

  - `ietf-te-ann` contains Tail-f annotations for the IETF TE module. The TE
    service lifecycle remains outside the IETF module itself.
  - `te-tunnel-service` adds the NSO lifecycle wrapper for TE tunnels modeled
    under `/te:te/te:tunnels`.
  - `te-tunnel-extensions` adds local `te-ext` augment data for head-end,
    tail-end, traffic steering, fast-reroute, and PM rendering intent.
  - `l2vpn-nm-service` and `l3vpn-nm-service` are local wrappers that bind
    IETF L2NM/L3NM intent to the inherited `eline` and `l3vpn` services.
  - `te-topology-bridge` exports the local `core-network` graph into the RFC
    8345/RFC 8795 topology tree.

Local executable service and support modules:

  - `core-network`, `inventory`, `srv6-node`, `rsvp-underlay`,
    `transport-te`, `transport-pm`, `service-assurance`,
    `transport-slice-service`, `transport-advisor`,
    `internet-access-service`, `eline-service`, `l2vpn-service`, and
    `l3vpn-service` are Tail-f example modules.
  - `transport-slice-service` is an executable local Network Slice Service
    model. It keeps the demo-friendly `network-slice-services` CLI vocabulary,
    but intentionally does not use an IETF module name or namespace because it
    composes local inventory, transport, PM, and assurance behavior.

The only compatibility edits in standard-model-derived files are the explicit
`NSO-COMPAT` comments in `ietf-te.yang`, where a small number of draft
statements are disabled because `ncsc` cannot compile those imported grouping
paths/list-key forms. The demo does not consume those disabled state-only
paths, and the edits are kept separate from NSO service lifecycle behavior.

In other words, this example is a transport-first bridge from the existing
SRv6 lab toward a more standards-based transport migration story.


Topology
--------

The sample network contains nine netsim devices:

  - `core-1`: Nokia SR OS SRv6 core router
  - `pe-01`: Juniper Junos SRv6 PE router
  - `core-2`: Cisco IOS XR NETCONF core router used for the southbound
    datastore subscription demo
  - `core-3`, `core-4`, `core-5`: Cisco IOS XR CLI core routers
  - `pe-02`: Cisco IOS XR SRv6 PE router
  - `ce-1-1`: Cisco IOS XR managed CE router attached to `pe-01`
  - `ce-1-3`: Cisco IOS XR managed CE router attached to `pe-02`

The topology is:

```
                        core-5 (XR CLI RR)
                         /              \
                     core-1 ======== core-2
                    (SR OS)          (XR NC)
                   /   ||              ||   \
ce-1-1 --- pe-01       ||              ||    pe-02 --- ce-1-3
(XR CLI)  (Junos NC)   ||              ||   (XR CLI)  (XR CLI)
                   \   ||              ||  /
                     core-4 ======== core-3
                    (XR CLI)        (XR CLI)
```

`pe-01` is dual-homed to `core-1` and `core-4`.
`pe-02` is dual-homed to `core-2` and `core-3`.
`core-5` is the top IOS XR core/route-reflector node connected between
`core-1` and `core-2`.
The brownfield NSS payloads use attachment-circuit IDs `pe-01-2` and
`pe-02-2`. These are inventory port IDs on devices `pe-01` and `pe-02`, mapped
to managed CEs `ce-1-1` and `ce-1-3` so the L3VPN/NSS story has concrete
customer endpoints on both sides.


Packages
--------

The example contains:

  - `core-network`: shared topology and addressing data
  - `inventory`: customer, port, and CE inventory
  - `srv6-node`: multivendor SRv6 transport configuration
  - `transport-common-models`: shared IETF, IANA, and IEEE support modules
    vendored with the example so it builds as a self-contained lab
  - `ietf-te-topology`: RFC 8795 TE topology model plus a
    `core-network export-te-topology` bridge action that exports TE metrics
    and SRLGs from the local graph; it uses the RFC 8345 base topology
    modules from `transport-common-models`
  - `rsvp-underlay`: helper service that enables RSVP/MPLS-TE on IOS XR,
    Junos, and SR OS nodes
  - `te-tunnel`: IETF TE tunnel northbound, Tail-f annotations, local
    lifecycle wrapper, local `te-ext` augment data, and IOS XR/Junos RSVP
    renderers; the sample RSVP-TE tunnel uses a Junos PE head-end
  - `transport-te`: SR policy and ODN services for IOS XR and Junos
  - `ietf-l2vpn-nm`: IETF L2NM model plus local wrapper that composes into
    the inherited E-Line service
  - `ietf-l3vpn-nm`: IETF L3NM model plus local wrapper that composes into
    the inherited L3VPN service
  - `transport-slice-service`: executable Network Slice Service wrapper that
    composes local L3VPN/L2VPN, ODN, PM, and service-assurance services
  - `transport-advisor`: brownfield transport migration planning,
    assessment, health-aware canary gating, and guarded execution actions for
    NSS services
  - `service-assurance`: telemetry and assurance monitor service
  - `closed-loop-srv6`: NETCONF/YANG-Push closed-loop nano service
    that provisions SRv6 on `core-2`, owns a lifetime datastore subscription
    and telemetry kicker, and uses service out-of-band policy plus
    `confirm-network-state` re-deploy to automatically repair and verify
    service-owned SRv6 locator drift from streamed device diffs
  - `transport-pm`: PM profiles and attachment services
  - `internet-access-service`: DIA service
  - `eline-service`: point-to-point Ethernet service
  - `l2vpn-service`: multipoint Ethernet VPN service
  - `l3vpn-service`: L3VPN service with optional ODN steering on IOS XR and
    Junos PEs

`transport-te` renders IOS XR SR policy and ODN CLI objects on `pe-02`, and
Junos `protocols source-packet-routing` policy/template plus BGP color steering
objects on `pe-01`. PM delay-measurement remains IOS XR-focused for
interface, SR policy, ODN color, and RSVP-TE attachment points. Endpoint PM is
now multivendor in the netsim: IOS XR uses `performance-measurement`, Junos
uses `services rpm`, and SR OS uses `oam-pm` delay templates with
TWAMP-light sessions. Service-assurance telemetry renders XR model-driven
telemetry, Junos `services analytics`, and SR OS `system telemetry`
configuration so all three transport vendors visibly participate in the
assurance story.


Build And Start
---------------

From this directory:

    $ make stop clean all start

The startup flow:

  - builds the multivendor netsim NEDs
  - builds the example service packages
  - creates `nso-run/`
  - uses `ncs_conf_tool` to set the NSO Python VM start timeout
  - creates the nine-device netsim network
  - loads the startup device data
  - starts netsim and NSO
  - performs an initial `sync-from`


Run The Demo
------------

The scripts are split by evaluation path:

  - `make demo` is the full technical reference walkthrough. Use it when you
    want to inspect the complete example surface area: packages, IETF
    northbound models, topology export, SRv6 underlay, RSVP/MPLS-TE, SR
    policy/ODN, PM/assurance, DIA, L2VPN, L3VPN, and NSS composition. It is
    useful for NSO developers learning how the example is built, but it is
    longer and broader than a focused evaluation demo.
  - `make demo-brownfield` is the primary evaluation path for an RSVP/MPLS-TE
    brownfield network. Use this first when you want to see how NSO can own a
    service lifecycle across "operate today's MPLS/RSVP-TE network first,
    enable SRv6 later, then migrate selected services."
  - `make demo-brownfield-full-migration` is the RSVP/MPLS-TE executive
    migration story. Use it when the desired final scene is not rollback or
    drift repair, but a network where the demo service intent is SRv6 and the
    RSVP/MPLS-TE scaffolding has been retired.
  - `make demo-brownfield-sr-mpls` is the same evaluation path for an SR-MPLS
    intermediate state. Use it if your network already has SR-MPLS color
    steering and you want SRv6 as the next step rather than a rip-and-replace
    migration.
  - `make demo-brownfield-sr-mpls-full-migration` is the SR-MPLS executive
    migration story. Use it when the desired final scene is SRv6 service
    intent and SRv6 ODN rendering, without returning the service to SR-MPLS.
  - `make demo-brownfield-canary` is the assurance-gated evaluation path. Use it
    when you want to evaluate NSO doing more than a playbook runner: planning
    around health evidence, blocking an unsafe migration, rolling back after a
    failed post-check, and succeeding when the canary data is clean.
  - `make demo-southbound-closed-loop` shows a continuous service-owned
    closed loop. A nano service provisions SRv6 on `core-2`, keeps its
    YANG-Push subscription and telemetry kicker for the lifetime of the
    service, detects out-of-band locator drift, then automatically performs a
    service `re-deploy confirm-network-state`; the service OOB policy repairs
    the locator by pushing NSO-owned intent back to the device without auditing
    the whole network.

Full reference walkthrough:

    $ make demo

Primary brownfield RSVP/MPLS-TE to SRv6 evaluation demo:

    $ make demo-brownfield

Full RSVP/MPLS-TE to SRv6 migration with legacy RSVP/MPLS-TE retirement:

    $ make demo-brownfield-full-migration

Brownfield SR-MPLS to SRv6 evaluation demo:

    $ make demo-brownfield-sr-mpls

Full SR-MPLS to SRv6 migration with final SRv6-only service intent:

    $ make demo-brownfield-sr-mpls-full-migration

Brownfield PM/assurance canary-gated migration variant:

    $ make demo-brownfield-canary

Closed-loop SRv6 nano service variant:

    $ make demo-southbound-closed-loop

For non-interactive a variants of the above, add `-nonstop`, e.g.,
`make demo-nonstop`.

The full reference walkthrough focuses on:

  - provisioning the multivendor SRv6 underlay
  - exporting the local topology into a standards-based RFC 8345/RFC 8795 TE
    topology view with TE metrics and SRLG values
  - loading `payload/rsvp-underlay.xml`
  - loading `payload/ietf-te-rsvp.xml`
  - verifying the rendered IOS XR, Junos, and SR OS RSVP/MPLS-TE underlay
  - verifying the rendered Junos RSVP-TE LSP, explicit path, and IS-IS LSP
    advertisement on `pe-01`
  - loading `payload/transport-te.xml`
  - verifying the rendered IOS XR SR policy/ODN configuration on `pe-02` and
    Junos source-packet-routing configuration on `pe-01`
  - verifying the PM and assurance lifecycle actions and the multivendor XR,
    Junos, and SR OS PM/telemetry renderings
  - provisioning an `l3vpn` that uses the `tailf-odn` template
  - verifying the XR route-policy/extcommunity and Junos
    community/resolution-map color objects created for ODN steering
  - loading `payload/ietf-l2vpn-nm.xml` and verifying the composed E-Line
    service
  - removing the temporary ODN demo `l3vpn` so the shared access ports are
    free again
  - loading `payload/ietf-l3vpn-nm.xml` and verifying the composed L3VPN,
    PE VRF/BGP, and CE BGP configuration

The brownfield migration demo focuses on:

  - discovering and syncing the multivendor network before changing service
    intent
  - establishing a Phase 1 RSVP/MPLS-TE and IETF TE transport estate with no
    SRv6 locators anywhere
  - exporting the same Phase 1 topology as RFC 8795 TE topology data before
    SRv6 is enabled
  - deploying an IETF NSS customer slice with `transport-profile rsvp-te`
    bound to the adopted IETF TE tunnel id `1234`
  - proving the baseline VPN realization uses managed CEs on both PEs but
    does not yet render XR VRF SRv6 configuration
  - running `core-network services plan-transport-migration` so NSO computes
    the PE-to-PE primary path, SRLG-disjoint backup path, SLO fit, readiness
    score, and staged wave/action recommendation before any migration is
    executed
  - enabling SRv6 as a Phase 2 underlay change across the selected PE/core
    transport domain
  - rerunning the planner after SRv6 turn-up so the recommendation changes
    from `stage-underlay` to wave `1` `migrate`
  - using `core-network services migrate-transport` to change only the service
    transport intent to `transport-profile srv6` plus color `3400`
  - showing the guarded action's readiness gates, native multivendor dry-run,
    explicit commit, and post-check before the SRv6 ODN steering is accepted
  - showing the services and devices NSO owns for the NSS realization
  - running the NSS `migration-readiness` action and PM/assurance `self-test`
    actions to make service ownership visible
  - running the transport advisor before and after SRv6 underlay enablement so
    the demo shows NSO recommending a staged migration instead of blindly
    replaying a playbook
  - simulating operator drift and using `check-sync` plus `re-deploy` to heal
    the service
  - rolling the service transport intent back to `rsvp-te` while keeping the
    VPN service itself in place

The RSVP/MPLS-TE full migration variant focuses on:

  - using the same two-phase brownfield story as `make demo-brownfield`
  - leaving out the drift and rollback acts so the narrative stays focused on
    a complete migration program
  - migrating the demo NSS/L3VPN service from `transport-profile rsvp-te` to
    `transport-profile srv6` and allocated color `3400`
  - deleting the adopted IETF RSVP-TE tunnel and local `rsvp-underlay` helper
    service after the service no longer depends on them
  - proving the final visible state is SRv6 service intent, SRv6 underlay node
    services, and SRv6 ODN rendering

The SR-MPLS brownfield variant focuses on:

  - discovering and syncing the same multivendor network before changing
    service intent
  - deploying an IETF NSS customer slice with `transport-profile sr-mpls` and
    ODN color `3300` while no SRv6 node services exist
  - exporting the same Phase 1 topology as RFC 8795 TE topology data before
    SRv6 is enabled
  - proving the baseline SR-MPLS realization has IOS XR and Junos ODN color
    steering and VPN color tagging, but does not yet render SRv6 locator or XR
    VRF SRv6 configuration
  - running `core-network services plan-transport-migration` so NSO preserves
    the existing SR-MPLS color, computes candidate paths and SLO fit, and
    stages the service until the SRv6 underlay exists
  - enabling SRv6 as a Phase 2 underlay change across the selected PE/core
    transport domain
  - rerunning the planner after SRv6 turn-up so the recommendation changes
    from `stage-underlay` to wave `1` `migrate`
  - using `core-network services migrate-transport` to change only the service
    transport intent to `transport-profile srv6` while keeping the same ODN
    color
  - showing the guarded action's native multivendor dry-run that adds the SRv6
    locator and VRF SRv6 behavior to the already-steered service
  - running the NSS `migration-readiness` action and PM/assurance `self-test`
    actions to make service ownership visible
  - running the transport advisor before and after SRv6 underlay enablement so
    the SR-MPLS variant shows an explicit readiness decision before migration
  - simulating drift on the SRv6 locator binding and healing it with
    `check-sync` plus `re-deploy`
  - rolling the service transport intent back to `sr-mpls`, leaving SR-MPLS
    color steering in place while removing only the SRv6 artifacts

The SR-MPLS full migration variant focuses on:

  - using the same two-phase brownfield story as
    `make demo-brownfield-sr-mpls`
  - leaving out the drift and rollback acts so the narrative ends in the
    target architecture instead of returning to the intermediate one
  - migrating the demo NSS/L3VPN service from `transport-profile sr-mpls` to
    `transport-profile srv6` while preserving color `3300`
  - showing that SR-MPLS in this lab is service-rendered ODN color steering,
    not a separate helper service like the RSVP underlay scaffold
  - proving the final visible state is SRv6 service intent, SRv6 underlay node
    services, and SRv6 ODN rendering

The canary-gated brownfield variant focuses on:

  - using the same RSVP/MPLS-TE to SRv6 service lifecycle as the main
    brownfield demo
  - enabling dry-run drift detection in strict mode so each reviewed
    `commit dry-run` must still match the actual commit changeset
  - using scoped `confirm-network-state compare write-and-service-read-set`
    on the brownfield commits to catch out-of-band changes in service-owned
    device state without fetching the full validation read-set
  - enforcing a `transport-te` service-level OOB abort rule for the IOS XR
    SRv6 ODN maximum SID depth, so a critical live change behind NSO is
    stopped before the next service transaction can proceed
  - feeding deterministic PM/assurance health into
    `core-network services transport-health`
  - marking the preferred `pe-01` to `core-1` link degraded so the planner
    avoids it and recommends the alternate PE-to-PE path
  - injecting a failed target SRv6 pre-check canary so
    `migrate-transport execute true` refuses to commit
  - replacing that with a failed post-check canary so NSO commits the service
    migration, detects the failed health gate, and rolls the service intent
    back to RSVP-TE
  - clearing the health input and rerunning the guarded migration to show the
    same service safely reaching SRv6 once PM/assurance evidence is clean
  - changing the live IOS XR SRv6 ODN maximum SID depth directly on the
    netsim device, watching the next guarded NSO commit abort, then
    reconciling with `sync-from` and `re-deploy` to restore the service-owned
    intent
  - running a scoped compliance report across the migrated PE pair while the
    OOB drift is present, then using
    `compliance report-results report <time> re-run` after repair so NSO
    verifies only the previously non-compliant PE instead of rechecking the
    whole network
  - running package-loaded XML compliance templates
    `srv6-migration-xr-postcheck` and `srv6-migration-junos-postcheck` after
    migration to verify the IOS XR ODN/SRv6 artifacts and Junos
    source-routing/color-steering artifacts are present


Manual Walkthrough
------------------

Open the CLI:

    $ ncs_cli -u admin -C

Provision the multivendor SRv6 underlay and export the RFC 8795 TE topology
view:

    # config
    (config)# core-network provision
    (config)# core-network export-te-topology
    (config)# commit

Inspect the standards-based topology view:

    # show running-config networks network srv6-transport-migration-te \
      network-types
    # show running-config networks network srv6-transport-migration-te node \
      pe-01
    # show running-config networks network srv6-transport-migration-te link \
      pe-02-Gi0_0_0_0-to-core-2-Gi0_0_0_4

The exported link view includes the local TE metric, delay metric, IGP metric,
and SRLG values, for example `te-srlgs value [ 4200 9001 ]`.

Load the RSVP/MPLS-TE underlay helper:

    (config)# load merge payload/rsvp-underlay.xml
    (config)# commit

Inspect the rendered RSVP/MPLS-TE config on IOS XR, Junos, and SR OS:

    # show core-network services rsvp-underlay default
    # show running-config devices device pe-02 config interface Loopback 0
    # show running-config devices device pe-02 config rsvp interface Gi0/0/0/0
    # show running-config devices device pe-02 config \
      mpls traffic-eng interface Gi0/0/0/0
    # show running-config devices device pe-01 config configuration \
      protocols rsvp interface xe-0/0/0:0.0
    # show running-config devices device pe-01 config configuration \
      protocols mpls interface xe-0/0/0:0.0
    # show running-config devices device core-1 config router Base \
      rsvp interface 1/1/5
    # show running-config devices device core-1 config router Base \
      mpls interface 1/1/5

Load the IETF TE RSVP payload:

    (config)# load merge payload/ietf-te-rsvp.xml
    (config)# load merge payload/te-tunnel-service.xml
    (config)# commit

Inspect the IETF TE service and the rendered Junos RSVP-TE LSP config:

    # show te tunnels tunnel IETF-RSVP-TE
    # show te-tunnel-services tunnel-service IETF-RSVP-TE
    # show running-config devices device pe-01 config configuration \
      protocols mpls label-switched-path IETF-RSVP-TE
    # show running-config devices device pe-01 config configuration \
      protocols mpls path IETF-RSVP-TE-PATH-1-1
    # show running-config devices device pe-01 config configuration \
      protocols isis label-switched-path IETF-RSVP-TE

Expected behavior:

  - `rsvp-underlay default` adds IOS XR Loopback0/RSVP/MPLS-TE enablement,
    Junos RSVP/MPLS interface enablement, and SR OS RSVP/MPLS transit
    interface enablement
  - `IETF-RSVP-TE` creates a Junos `protocols mpls label-switched-path` on
    `pe-01`
  - the explicit primary path renders a Junos `protocols mpls path` with
    loose hops through `core-1` and `core-2`
  - the secondary path renders as a dynamic Junos path option
  - `traffic-steering autoroute` renders Junos IS-IS LSP advertisement for
    the TE LSP

Load the sample transport payload:

    (config)# load merge payload/transport-te.xml
    (config)# commit dry-run outformat native
    (config)# commit

This payload creates:

  - `core-network services sr-policy pe02-to-pe01` on IOS XR `pe-02`
  - `core-network services sr-policy pe01-to-pe02` on Junos `pe-01`
  - `core-network services odn-template tailf-odn` on both head-ends

Create an L3VPN that uses the ODN template on both PEs:

    (config)# l3vpn sample-l3vpn customer Tail-f link 1 port pe-01-2
    (config)# l3vpn sample-l3vpn customer Tail-f link 2 port pe-02-2
    (config)# l3vpn sample-l3vpn odn-template tailf-odn
    (config)# commit dry-run outformat native
    (config)# commit

Leave configuration mode and verify the rendered IOS XR and Junos transport and
VPN config:

    # show running-config devices device pe-02 config \
      segment-routing traffic-eng on-demand color 3000
    # show running-config devices device pe-02 config \
      segment-routing traffic-eng policy pe02-to-pe01
    # show running-config devices device pe-01 config configuration \
      protocols source-packet-routing
    # show running-config devices device pe-02 config \
      route-policy L3VPN-sample-l3vpn-ODN-EXP
    # show running-config devices device pe-02 config \
      extcommunity-set opaque COLOR_3000
    # show running-config devices device pe-01 config configuration \
      policy-options resolution-map L3VPN-sample-l3vpn-COLOR-MAP

Expected behavior:

  - `tailf-odn` renders `segment-routing traffic-eng on-demand color 3000`
    on `pe-02`
  - `tailf-odn` renders a Junos `source-routing-path-template` and
    `extended-nexthop-color` on `pe-01`
  - `pe02-to-pe01` renders an SR policy from `pe-02` toward the SRv6
    loopback of `pe-01`
  - `pe01-to-pe02` renders a Junos `source-routing-path` from `pe-01` toward
    the SRv6 loopback of `pe-02`
  - `sample-l3vpn` creates an XR route-policy that sets extcommunity color
    `COLOR_3000`
  - `sample-l3vpn` creates Junos `color:0:3000` community tagging plus a
    `resolution-map` for color-aware VPN route resolution
  - the XR VRF export path references that route-policy

Load the IETF L2NM payload:

    (config)# load merge payload/ietf-l2vpn-nm.xml
    (config)# load merge payload/l2vpn-nm-service.xml
    (config)# commit

Inspect the standard L2NM service and the composed underlying E-Line:

    # show l2vpn-ntw vpn-services vpn-service l2nm-evpn
    # show l2vpn-nm-services vpn-service l2nm-evpn
    # show eline
    # show running-config devices device pe-02 config l2vpn

The local wrapper references this standard IETF service and composes it into
the inherited `eline` implementation, which renders EVPN VPWS config on the
PEs.

Because the sample `sample-l3vpn` and `payload/ietf-l3vpn-nm.xml` both use
the same PE access ports, remove the temporary ODN demo VPN before loading
the IETF L3NM payload:

    (config)# no l3vpn sample-l3vpn
    (config)# commit
    (config)# load merge payload/ietf-l3vpn-nm.xml
    (config)# load merge payload/l3vpn-nm-service.xml
    (config)# commit

Inspect the standard L3NM service and the composed L3VPN:

    # show l3vpn-ntw vpn-services vpn-service l3nm-basic
    # show l3vpn-nm-services vpn-node-service l3nm-basic pe-02
    # show l3vpn
    # show running-config devices device pe-02 config vrf
    # show running-config devices device pe-02 config router bgp 65000
    # show running-config devices device ce-1-3 config router bgp 65010

Expected behavior:

  - `l2nm-evpn` creates an underlying `eline` service
  - `l3nm-basic` creates an underlying `l3vpn` service
  - `pe-02` gets a VRF and CE-facing BGP neighbor for the IETF L3NM service
  - `ce-1-3` gets the matching BGP neighbor toward `pe-02`


Payload Files
-------------

`payload/transport-te.xml` contains the sample transport services used by the
demo:

  - one IOS XR SR policy: `pe02-to-pe01`
  - one Junos SR policy: `pe01-to-pe02`
  - one shared ODN template: `tailf-odn`

The payload is intentionally small so it is easy to read and adapt.

`payload/rsvp-underlay.xml` contains the multivendor RSVP/MPLS-TE helper
service used to make the Phase 1 TE demo concrete in the lab.

`payload/ietf-te-rsvp.xml` contains an `ietf-te@2026-03-26` RSVP tunnel
sample with local `te-ext` augment data for head-end, tail-end,
fast-reroute, PM, and autoroute steering. The sample uses Junos `pe-01` as
the head-end, IOS XR `pe-02` as the tail-end, and loose explicit hops through
the SR OS and IOS XR core.

`payload/te-tunnel-service.xml` contains the local NSO lifecycle wrapper
that tells NSO to render the referenced IETF TE tunnel.

`payload/ietf-l2vpn-nm.xml` contains a standard IETF L2NM VPWS-style sample
that composes into the inherited `eline` service.

`payload/l2vpn-nm-service.xml` contains the local NSO lifecycle wrapper
that tells NSO to render the referenced IETF L2NM VPN service.

`payload/ietf-l3vpn-nm.xml` contains a standard IETF L3NM sample that composes
into the inherited `l3vpn` service and programs the managed CE BGP neighbor on
`ce-1-3`.

`payload/l3vpn-nm-service.xml` contains the local NSO lifecycle wrapper
that tells NSO to render the referenced IETF L3NM VPN node.

`payload/transport-slice-service.xml` contains the local transport slice
sample used by `make demo`.  It keeps slice intent stable while composing
L3VPN, ODN, PM, and assurance objects in the lab.

`payload/transport-resource-pools.xml` contains the default local SR color pool
used by the brownfield demos when Resource Manager 5 is not installed. The pool
is named `transport-sr-colors` and covers colors `3400` through `3499`.

`payload/rm-sr-color-pool.xml` contains the Resource Manager 5 ID pools needed
when RM5 is installed: the inherited `customer-vni` pool used by the VPN
services and the `transport-sr-colors` pool used by the transport advisor. It
is not loaded by default because RM5 is optional and supplied as a separate
package. If a `resource-manager` package is copied into `package-store/` before
`make all`, load this payload to let the transport advisor allocate colors
through RM5 instead of the local fallback:

    # config
    (config)# load merge payload/rm-sr-color-pool.xml
    (config)# commit

The advisor's `color-allocation-backend` input defaults to `auto`. In `auto`
mode it uses RM5 when `resource_manager.service.Allocator` and the named RM ID
pool are available; otherwise it uses the local `transport-resource-pools`
model. Use `color-allocation-backend resource-manager` to require RM5, or
`color-allocation-backend local` to force the self-contained fallback.

`payload/brownfield-migration.xml` contains the NSS service used by
`make demo-brownfield` and `make demo-brownfield-full-migration`. It starts
with `transport-profile rsvp-te` and tunnel `1234`, with SDPs on `pe-01-2` and
`pe-02-2` that terminate on managed CEs. The selected-service demo first runs
with no SRv6 node services, then enables the SRv6 transport domain and migrates
the same customer slice to SRv6 ODN color steering using an allocated color
before rolling back by changing service intent instead of editing device config
directly. The full migration variant uses the same payload but ends by
removing the adopted IETF RSVP-TE tunnel and local RSVP underlay helper after
the service is migrated to SRv6.

After the Phase 1 slice is committed, the transport planner can be run
manually:

    # core-network services plan-transport-migration slice-service bf-gold \
      target-profile srv6 color-pool transport-sr-colors

Before SRv6 underlay enablement this returns `decision stage-underlay-first`
with a `stage-underlay` recommendation. The recommendation includes the
shortest PE-to-PE primary path, an SRLG-disjoint backup path when one exists,
the cumulative path delay, the NSS latency bound, readiness score, recommended
SR color, and color source (`resource-manager`, `local-pool`, `existing`,
`explicit`, or `fallback-base`). After the `srv6-node` services are committed,
the same planner returns `decision ready` with a wave `1` `migrate`
recommendation.
After migration, rerun it with `include-no-op true` to show that the service is
already on the target profile:

    # core-network services plan-transport-migration slice-service bf-gold \
      target-profile srv6 color-pool transport-sr-colors include-no-op true

The per-slice advisor can still be run manually when the operator wants CLI
next steps and readiness detail for one connection group:

    # core-network services assess-transport-migration slice-service bf-gold \
      connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors

Before SRv6 underlay enablement this returns `decision stage-underlay-first`;
after the `srv6-node` services are committed it returns `decision ready`; after
the service is migrated it returns `decision no-op`.

The guarded migration action can be run in plan-only mode first:

    # core-network services migrate-transport slice-service bf-gold \
      connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors

With `execute` left at the default `false`, the action returns a `planned`
status and native dry-run output but does not change the network. With
`execute true`, the same action performs the readiness gates, commits the
service transport intent change, reserves the chosen SR color in the selected
allocator, and post-checks that the advisor now reports `decision no-op`.

`payload/brownfield-migration-sr-mpls.xml` contains the NSS service used by
`make demo-brownfield-sr-mpls` and
`make demo-brownfield-sr-mpls-full-migration`. It starts with
`transport-profile sr-mpls` and ODN color `3300`, with SDPs on the same two
managed-CE access circuits. The demo shows an already color-steered SR-MPLS
service running before any SRv6 locator exists, then being migrated to SRv6 by
changing only the transport profile after the SRv6 underlay is enabled. The
SR-MPLS variant uses the same `migrate-transport` action and reuses the
existing service color `3300` when no explicit `target-color` is supplied. In
the full migration variant there is no separate SR-MPLS helper service to
delete; the service migration replaces the SR-MPLS ODN branch with SRv6 ODN
state and the final checks keep the service on `transport-profile srv6`.

For the SR-MPLS variant, the planner preserves the existing color by default:

    # core-network services plan-transport-migration slice-service bf-srmpls \
      target-profile srv6

`payload/transport-health-link-degraded.xml`,
`payload/transport-health-precheck-bad.xml`, and
`payload/transport-health-postcheck-down.xml` contain deterministic
PM/assurance inputs for `make demo-brownfield-canary`. They model the contract
where a real telemetry or assurance collector would publish health state into
NSO:

  - a degraded/lossy TE link that the planner should avoid
  - a target SRv6 pre-check canary that blocks migration before commit
  - a target SRv6 post-check canary that triggers rollback after commit


Notes And Limits
----------------

  - This is a netsim reference example, not a production-complete transport
    automation product.
  - The `transport-health` model is deterministic lab input that represents
    collector-fed PM/assurance state. The `core-2` IOS XR NETCONF ConfD
    netsim device does generate deterministic YANG-Push datastore
    notifications for `make demo-southbound-closed-loop`, but the bundled
    netsim NEDs do not
    generate realistic TWAMP/RPM/OAM-PM measurement streams for NSO to consume
    directly.
  - The closed-loop SRv6 demo uses a nano service to make the lifecycle
    visible: telemetry is a service-owned component, SRv6 configuration is a
    service component, and the plan only reaches verified once telemetry marks
    service health operational. The demo relies on YANG-Push `sync-on-start`
    for the subscription to observe current device state when it starts or
    reconnects. When telemetry detects service-owned locator drift, the
    service action invokes `re-deploy confirm-network-state` so the package
    OOB policy can repair the device with `sync-to-device`.
  - The transport TE and ODN slice now has IOS XR and Junos renderers. The
    Junos RSVP-TE path supports the numbered-hop explicit and dynamic path
    patterns used by the sample tunnel; label-hop and PCE/external path
    options are rejected with explicit validation errors for Junos because
    those are not cleanly exposed by the bundled Junos netsim NED shape.
  - The RFC 8345 `ietf-network` and `ietf-network-topology` modules are
    vendored in `transport-common-models`, while the `ietf-te-topology`
    package owns the RFC 8795 `ietf-te-topology` module and the local
    `te-topology-bridge` action. `core-network` remains the internal service
    model; the action exports it into the standard topology view for demos
    and northbound inspection, including TE metric, delay metric, IGP metric,
    and SRLG data.
  - The transport planner uses the local `core-network` graph as its
    executable source of truth and reports candidate primary/backup paths,
    SLO fit, SRLG disjointness, and migration waves. It is intentionally a
    deterministic netsim reference planner, not a full PCE replacement.
  - The IETF TE slice uses `ietf-te@2026-03-26` from
    `draft-ietf-teas-yang-te-44` at the northbound, but the lab still uses a
    small local `rsvp-underlay` helper service to provide the
    multivendor RSVP/MPLS-TE scaffolding used by the brownfield phase. SR OS
    participates as a transit underlay node in this lab; an SR OS RSVP-TE
    head-end variant would require adding an SR OS PE-style service attachment
    or a separate core-LSP demo.
  - The bundled `ietf-te` tunnel model is an active IETF draft, not an RFC.
    The package keeps the draft model separate from local NSO lifecycle and
    renderer hints through `ietf-te-ann`, `te-tunnel-service`, and
    `te-tunnel-extensions`.
  - `transport-slice-service` is a local executable Network Slice Service
    model, not a published IETF module.  This avoids using an IETF namespace
    for local NSO lifecycle, inventory, PM, and assurance behavior while still
    showing a slice-oriented customer intent model.
  - TE performance measurement is currently strongest on IOS XR:
    `te-ext:performance-measurement`, SR policy PM, ODN PM, RSVP-TE PM, and
    interface PM map to XR `delay-measurement`. Junos participates through
    RPM endpoint probes and service-assurance analytics. SR OS participates
    through OAM-PM delay templates, TWAMP-light endpoint sessions, and
    service-assurance telemetry. Equivalent Junos/SR OS policy, ODN,
    RSVP-TE, and interface PM attachment mappings remain future work because
    the bundled netsim NEDs do not expose the same PM feature paths as the XR
    CLI NED used here.
  - The bundled IOS XR CLI and NETCONF netsim NED packages expose different
    YANG tree shapes and template entry points. The renderer templates target
    the package paths present in this lab.
  - The example keeps the parent lab's service packages so users can combine
    transport intent with existing E-Line, L2VPN, and L3VPN examples.
  - The shipped sample `sample-l3vpn` ODN demo and the sample
    `payload/ietf-l3vpn-nm.xml` reuse the same PE access ports, so the demo
    runs them sequentially rather than simultaneously.
  - The PM, transport-slice, DIA, and service-assurance areas are executable
    reference subsets for the netsim lab, not production-complete service
    packages.
  - The brownfield migration demo is intentionally service-lifecycle focused:
    it demonstrates NSO native dry-runs, service ownership, drift detection,
    re-deploy repair, and rollback. It does not attempt to model every
    operational step in a real SP SRv6 migration program.
  - Future work should prefer standard IETF northbound models where practical.


Cleanup
-------

To stop NSO and the netsim devices and remove generated files:

    $ make stop clean


Further Reading
---------------

  - NSO Development Guide: Implementing Services
  - NSO Development Guide: Services Deep Dive
  - NSO Development Guide: Templates
  - NSO Development Guide: Actions
  - NSO Development Guide: YANG
  - NSO Development Guide: Nano Services
  - NSO Development Guide: Kicker
