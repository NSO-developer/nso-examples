Nano Services Examples
======================

Implement staged provisioning using nano services.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### basic-vrouter
A scripted implementation of the NSO Development Guide chapter Nano Services
for Staged Provisioning example with a focus on how to implement a service as
several smaller (nano) steps or stages by using a technique called reactive
FASTMAP (RFM), and provide a framework to execute actions with side effects
safely. In addition to a shell script using the NSO CLI, a Python script
variant using the NSO RESTCONF interface is also available.

### link-migration
Illustrates how to design a reactive FASTMAP service using Nano Services.
Showcased by the NSO Development chapter Guide Graceful Link Migration Example.

### netsim-vrouter
A scripted implementation of the NSO Development Guide chapter Nano Services
for Staged Provisioning. The example extends the `basic-vrouter` example to show
how to implement a nano service vrouter where the "virtual machine", vrouter
instance, components are represented by netsim network elements.

### mpls-vpn-vrouter
An extension to the `mpls-vpn` example describing how virtual routers are
launched using ESC (Elastic Services Controller) using reactive FASTMAP.
Used by the NSO Development Guide under Nano Services for Provisioning with
Side Effects.

### examples.ncs/getting-started/netsim-sshkey
*Note*: this example is located in the `/getting-started` directory. A scripted
implementation of the Development Guide chapter Developing and Deploying a Nano
Service example with a focus on how to develop a service as several smaller
(nano) steps or stages by using a technique called reactive FASTMAP (RFM), and
provide a framework to execute actions with side effects safely. In addition to
a shell script using the NSO CLI, two Python script variants using the NSO
RESTCONF and MAAPI interfaces are also available.