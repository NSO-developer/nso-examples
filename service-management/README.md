Service Management Examples
===========================

Create, deploy, and manage services.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### implement-a-service
Demonstrate building a new service from scratch, incrementally evolving the
service to cover more complex use cases. The `dns-*` set of examples deals with
device DNS configuration, while the `iface-*` set performs interface
configuration. These examples complement the step-by-step walk-through in the
Implementing Services chapter of the NSO Development Guide.

### rfs-service
Illustrates how to write a user-defined Resource Facing Service (RFS) using
variants of Java service callbacks to implement service-to-device mappings of
data, shared data between service instances, and FASTMAP. The example is used
by the NSO Development Guide.

### cfs-rfs-stacked service
A stacked service Python variant of the Java-based RFS VLAN service example.
Demonstrates a simple stacked services design that applies to bigger,
more complex services where there are many reasons why you prefer stacked
services to a single monolithic one.

### rfs-service-erlang
Subset Erlang variant of the Java-based RFS service example. Used by the NSO
Development Guide chapter Embedded Erlang Applications.

### srv6-demo
A fully-fleshed-out Segment Routing over IPv6 VPN services example that can,
in addition to netsim devices, be used with actual IOS XR routers, such as
XRd, to demonstrate working data plane connectivity. It features different
service types, as well as a simple inventory system to prevent misconfiguration
from provisioning incompatible services on the same interface.

### mpls-vpn-java
An example that illustrates how services are created and used in NSO by
managing Layer3 VPNs in a service provider MPLS network. Used by the NSO
Operation & Usage Guide Guide to describe NSO service management features, NSO
Operation & Usage Guide chapters The NSO Device Manager, Managing Network
Services, The Alarm Manager, and Compliance Reporting, and the
NSO Development Guide chapters Using CDB, Service and Action Callbacks, Service
Mapping: Putting Things Together, and Templates Applied from an API.

### mpls-vpn-python
Subset Python variant of the above Java-based `mpls-vpn-java` example.

### mpls-vpn-template
Illustrates template-centric implementation where a service template drives the
main logic while the Java code performs auxiliary computations.
Functionality-wise, it is the same example as the `mpls-vpn-java` example.

### mpls-vpn-simple
Implements a template-only-based L3VPN service. Used by the NSO Operation &
Usage Guide chapter Alarm Handling.

### datacenter-qinq
Illustrates how to create a Q-in-Q tunnel in a data center using NSO managing
different devices using a Java, Java and template combo, or a template-only
based service. Also includes must statement and alarm examples.

### datacenter-connectivity
Implements a service to create connectivity between several access switches in
the same or different data centers. The services configure multiple different
devices. Mapping between the service and devices is done with Java code.
Implements a YANG `tailf:cli-completion-actionpoint` with an Erlang callback. A
script for generating alarms is included.

### pre-modification
Illustrates how to write and use a `pre-modification` callback to alter device
data outside of the FASTMAP algorithm so that, for example, the configuration
data set by the `pre-modification` callback application is not removed when the
service is removed. Used by the NSO Development Guide chapter Services Deep
Dive.

### iface-postmod-python and -java
These examples include an `iface` service that uses the `post-modification`
callback functionality to deploy a default configuration to an interface once
the service is removed. Used by the NSO Development Guide chapter Services Deep 
Dive.

### shared-delete
This example includes a `bgp-routing` service that configures a new BGP
neighbor. However, this configuration is incompatible with the pre-existing
configuration (a different BGP neighbor), which must be removed as part of
service provisioning. As a best practice, the service implements "shared
delete" through another helper service. See the NSO Development Guide chapter
Services Deep Dive for documentation.

### discovery
This example includes an `iface` service that deploys configuration to an
interface. However, network devices already contain existing configurations,
such as those provisioned by an older automation system, requiring service
reconciliation. See the NSO Development Guide chapter Services Deep Dive for
service discovery documentation.

### confirm-network-state
The example demonstrates out-of-band changes handling with a service policy.
The managed devices have confirm-network-state processing configured and have
out-of-band changes related to the provisioned services, which NSO processes
during a related change or a sync-from operation.

### upgrade-service
Based on the `rfs-service` example's `vlan` service package. Performs a package
upgrade where instance data in the NSO CDB is changed and migrated with the
help of Java or Python code that connects to NSO, reads old config data using
the CDB API, and writes the adapted config data using MAAPI while adjusting
service meta-data. Used by the NSO Development Guide under Writing an Upgrade
Package Component.

### access-lists
Illustrates how to create a simple service with NSO. Implements a basic
firewall service that configures a Cisco IOS and Juniper router. Mapping
between the service and devices is done with Java code.

### data-kicker
Illustrates how to write a reactive FASTMAP application using a data-kicker
help construct. Shows a legacy way of implementing a reactive FASTMAP
application that has since evolved into reactive FASTMAP nano services. See
`examples.ncs/nano-services` for nano services using reactive FASTMAP examples.

### web-site-service
Demonstrate the service manager functionality within NSO. Also shows how to set
up data- and notification-kickers used by the Development Guide chapter
Kickers. For the kicker part, it shows a legacy way of implementing kickers
that has since evolved into reactive FASTMAP nano services. See
`examples.ncs/nano-services` for nano services using reactive FASTMAP examples.

### service-progress-monitoring
Illustrates how Service Progress Monitoring (SPM) interacts with plans and
services and how Python user code invokes SPM actions depending on plan
progression. Shows a legacy way of implementing service progress monitoring
that has since evolved into reactive FASTMAP nano services. See
`examples.ncs/nano-services` for nano services using reactive FASTMAP examples.
