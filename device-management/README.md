Device Management Examples
==========================

Learn the concepts of NSO device management.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### router-network
Used by the NSO Operation & Usage Guide chapter The Network Simulator to
describe the `ncs-netsim` program. `ncs-netsim` is a helpful tool to simulate a
network of devices to be managed by NSO, which makes it easy to test NSO
packages towards simulated devices, here NETCONF enabled devices. Besides
setting up a simulated network, the example README and demo script showcase
controlling NSO and managing devices.

### netconf-device
A README and shell script showcasing how to use the built-in NETCONF network
element driver (NED) to manage NETCONF-enabled devices using only the devices
YANG models to create a NETCONF NED representing the device.

### netconf-ned
This example shows how to create and install a NETCONF NED. The example set up
a NETCONF-enabled ConfD device that simulates a hardware system without netsim
to simulate configuring an actual device. See the NSO Development Guide chapter
NETCONF NED Development for documentation.

### simulated-cisco-ios
A README and demo script showcasing how to set up a simulated network of Cisco
IOS routers and how to manage these with NSO. Various NSO Operation & Usage
Guide chapters use the example to get going with the NSO basics, network
element drivers (NED), services, compliance reports, and administrative tasks.
In addition, the NSO Development Guide uses the example as a reference
in the Developing NSO Services chapter.

### real-device-cisco-ios
A README and demo script showcasing how to manage Cisco IOS routers using a CLI
network element driver (NED). Used by the NSO Operation & Usage Guide chapter
NEDs and Adding Devices to describe using a CLI NED.

### real-device-juniper
A README and demo script showcasing how to manage NETCONF-enabled Juniper
routers using the built-in NETCONF network element driver (NED). Used by the
NSO Operation & Usage Guide Network Element Drivers and Adding Devices chapter
to describe using a NETCONF NED.

### cli-ned
An example of a CLI NED implementation for a device that provides a Cisco-style
CLI interface where Java code is used to enable NSO to manage it. Used by the
NSO Development Guide chapter CLI NED development.

### generic-restconf-ned
An example of a generic NED implementation for a device that provides a
RESTCONF interface where Java code is used to enable NSO to manage it.

### generic-xmlrpc-ned
An example of a generic NED implementation for a device that provides a
proprietary interface where Java code is used to enable NSO to manage it. Used
by the NED Development Guide chapter Generic NED Development.

### snmp-ned
An example showcasing how to manage SNMP-enabled devices with NSO. Used by the
NSO Development Guide chapter Developing NEDs for using MIBs as input to
creating an SNMP NED.

### web-server-basic
An example that showcases the NSO device manager using a README and a script.
Used by the NSO Operation & Usage Guide chapter Notifications to showcase how
NSO can receive NETCONF notifications from devices.

### device-templates
Showcase setting device config using static templates, templates with
variables, templates with expressions, and templates combined.

### aggregated-stats
Implements a Java application that maps operational state data from devices
to an aggregated high-level representation. Also used by the NSO Development
Guide chapter NSO Packages.

### netconf-call-home
Demonstrates the NSO built-in support for the NETCONF SSH Call Home client
protocol operations over SSH as defined in RFC 8071 (section 3.1) to enable
NSO to communicate with the device after it calls home.

### ned-upgrade
Demonstrates adding a new NED package to NSO without performing a complete
package reload. The NED in the example contains backward incompatible changes
relating to an already provisioned service. This requires a change to the
service, applying different configurations through an XML template for each
version of a NED. See the NSO Administration Guide chapter NED Migration for
documentation.

### ned-migration
A step-by-step guide to migrating devices between different NED versions
with the `/devices/device/migrate` and `/devices/migrate` actions. As the NED
may contain backward-incompatible changes, the example shows how you can
enumerate those related to existing configurations in the NSO. See the
NSO Administration Guide chapter Adding NED Packages and NED Migration for
details.

### ned-yang-revision
A scripted NED upgrade example showcasing a backward and non-backward
compatible YANG model upgrade. In the former case, using the NSO revision merge
feature, and in the latter, creating a new NED relying on the NSO CDM feature
to separate the old and new NED and migrate the configuration as in the
`common-tasks/ned-migration` example. Demonstrates how to use the revision
merge functionality, described in the NSO Administration Guide chapter Revision
Merge Functionality.

### snmp-notification-receiver
Showcases the NSO SNMP notification receiver (v1, v2c, v3) that can be used
with devices managed by NSO or external sources. The Java handler example
raises an alarm whenever it receives an SNMP notification. Used by the NSO
Development Guide chapter SNMP Notification Receiver.
