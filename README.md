The NSO Example Collection
==========================

The NSO example collection contains everything from tiny to medium-sized and
trivial to expert-use examples. The example set has an NSO application
developer focus, but many examples lend themselves well to administrators.

Many examples have been added on request or when a new feature was added, not
in a beginner-to-expert order. Therefore, to assist new and expert NSO
application developers and administrators in navigating the example set, the
list below is in the order a typical new NSO user would consume the examples.
In addition, each example in the list below has a summary to assist users of
all experiences find the example they are looking for. Finally, if applicable,
there are pointers to the example described in the NSO documentation.

All Makefiles in the example collection will check for the $NCS_DIR variable.
Thus, the 'ncsrc' file under the NSO local install root directory, where the
$NCS_DIR variable points to, must be sourced before running the examples.

    . /path/to/nso-<nso-vsn>/ncsrc

Many examples use Netsim to simulate managed devices with northbound interfaces
such as NETCONF, CLI, SNMP, etc.

See each example category README for an overview of each example in that
category.

Suggested Order of Consumption:
-------------------------------

### getting-started
Introduction to NSO.

### device-management
Learn the concepts of NSO device management.

### service-management
Create, deploy, and manage services.

### nano-services
Implement staged provisioning using nano services.

### scaling-performance
Optimize NSO for scaling and performance.

### layered-services-architecture
Design large and scalable NSO applications using LSA.

### northbound-interfaces
Northbound programmatic APIs in NSO: NETCONF, RESTCONF, and SNMP.

### high-availability
Implement redundancy in your deployment.

### sdk-api
Python, Java, and Erlang APIs and other ways to extend NSO.

### aaa
Use NSO's AAA mechanism.

### misc
Examples that do not belong to any of the above categories.

### common
Common items used by the examples.
