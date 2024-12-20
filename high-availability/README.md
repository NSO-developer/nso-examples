High Availability Examples
==========================

Implement redundancy in your deployment.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### raft-cluster
The example shows the steps to set up an HA Raft cluster initially. It includes
securing the cluster by provisioning node certificates and how it behaves when
nodes go down and come up again.

### rule-based-basic
A scripted example that uses the NSO rule-based HA manager to set up and manage
two NSO nodes, one primary, and one secondary node, implements a single
dummy service package. The high-availability configuration enables automatic
start-up and failover. In addition to a shell script using the NSO CLI, a
Python script variant using the NSO RESTCONF interface is also available.

### rule-based-cluster
A scripted example that uses the NSO rule-based HA manager to set up and manage
three NSO nodes, one primary and two secondary nodes, that implement a single
dummy service package. The high-availability configuration enables automatic
start-up and failover. In addition to a shell script using the NSO CLI, a
Python script variant using the NSO RESTCONF interface is also available.

### hcc
A README providing a link to three examples each for HA Raft and rule-based
setups with the Tail-f HCC package:

- *raft-l2 and rule-l2*:
  Scripted example implementations for the example setup described by the NSO
  Administration Guide chapter Tail-f HCC Package. In addition to a shell
  script using the NSO CLI, a Python script variant using the NSO RESTCONF
  interface is also available.

- *raft-l3bgp and rule-l3bgp*:
  Scripted example implementations for the example setup described by the NSO
  Administration Guide chapter Tail-f HCC Package. In addition to a shell
  script using the NSO CLI, a Python script variant using the NSO RESTCONF
  interface is also available.

- *raft-upgrade-l2 and rule-upgrade-l2*:
  Scripted example implementations of the setup described by the NSO
  Administration Guide chapter NSO Deployment showcasing installation of NSO,
  the initial configuration of NSO, upgrade of NSO, and upgrade of NSO packages
  on the two NSO-enabled nodes. In addition to a shell script using the NSO
  CLI, a Python script variant using the NSO RESTCONF interface is also
  available.

### load-balancer
A load balancer example that listens on the VIP address and routes connections
to the primary HA node. Used by the NSO Administration Guide under Setup with
an External Load Balancer.

### upgrade-basic
A scripted example that uses the NSO rule-based HA manager to set up and manage
two NSO nodes, one primary and one secondary node. The NSO and the example
package versions are upgraded on both nodes. In addition to a shell script
using the NSO CLI, a Python script variant using the NSO RESTCONF interface is
also available.

### upgrade-cluster
A scripted example that uses the NSO rule-based HA manager to set up and manage
three NSO nodes, one primary and two secondary nodes. The NSO and the example
package versions are upgraded on all three nodes. In addition to a shell script
using the NSO CLI, a Python script variant using the NSO RESTCONF interface is
also available.

### external-ha-framework
Showcase how to implement an external high availability framework (HAFW) where
NSO nodes only replicate the CDB data and must be told by the HAFW its roles
and what to do when nodes fail.