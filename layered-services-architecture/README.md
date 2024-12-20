Layered Services Architecture Examples
======================================

Design large and scalable NSO applications using LSA.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### lsa-single-version-deployment
Deployment of an LSA cluster where all the nodes have the same major version of
NSO running is called a single version deployment. This example implements a
slight variation on the `examples.ncs/service-management/rfs-service` example
where the YANG code has been split into an upper-layer and a
lower-layer implementation. Used by the NSO Administration Guide chapter LSA
Examples under Greenfield LSA Application.

### lsa-scaling
Illustrates how to easily write a user-defined RFS "Resource Facing Service" in
an LSA cluster to move the device between lower NSO nodes. It also illustrates
how a package can be used to replicate device configuration to some external
store. This allows the lower LSA nodes to be run in non-HA mode. Used by the
NSO Administration Guide chapter LSA Examples under Greenfield LSA Application
Designed for Easy Scaling.

### mpls-vpn-lsa
Implementation of the `/example.ncs/service-management/mpls-vpn-java` example
as a "Layered Service Architecture". Used by the NSO Administration Guide
chapter Rearchitecting an Existing VPN Application for LSA.

### lsa-multi-version-deployment
If the LSA cluster node versions are different, it is called a multi-version
deployment since the packages on the CFS node must be managed differently. Used
by the NSO Administration Guide chapter Example Walkthrough.

### upgrade-cfs-single-version
Extends the `lsa-single-version-deployment` example and implements a simple
shell script to show how an NSO version upgrade of the upper NSO instance can
be performed. This example upgrades NSO for an LSA single-version deployment.

### upgrade-cfs-multi-version
Extends the `lsa-single-version-deployment` example and implements a simple
shell script to show how an NSO version upgrade of the upper NSO instance can
be performed while at the same time switching from a single version to a
multi-version deployment.

### upgrade-multi-version-nso
Extends the `lsa-multi-version-deployment` example and implements a simple
shell script to show how an NSO version upgrade of the upper NSO instance and
one of the lower NSO instances can be performed.

### examples.ncs/scaling-performance/perf-lsa
*Note*: this example is located in the `scaling-performance` directory. The
example implements stacked services, a CFS (customer-facing service)
abstracting the RFS (resource-facing service), and allows for easy migration to
an LSA set up to scale with the number of devices or network elements
participating in the service deployment. Builds on the `perf-stack` example and
showcases an LSA setup using two RFS NSO instances with a CFS NSO instance.
Used by the NSO Development Guide chapter Scaling and Performance Optimization.