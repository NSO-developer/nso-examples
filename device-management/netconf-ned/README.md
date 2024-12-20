Create and Install a NETCONF NED
================================

Creating and installing a NETCONF NED consists of the following steps:
- Make the device YANG data models available to NSO
- Build the NED package from the YANG data models using NSO tools
- Install the NED with NSO
- Configure the device connection and notification events in NSO

This example runs the above steps for a device running ConfD that simulates a
hardware system controller. See the `devsim/README` for details on the
simulated device.

See the NSO Developer Guide chapter NED Development for details on creating and
installing a NETCONF NED and this example.

Running the Example
-------------------

A shell script runs the example and the steps for creating and installing a
NETCONF NED described above using the NSO `netconf-console` tool. Run the shell
script to create a NED and provision a simulated hardware system controller
using the `netconf-console` tool by typing:

      make demo

There is also a shell script variant that uses the NSO NETCONF NED builder tool.
Run the shell script to create a NED and provision a simulated hardware system
controller using the `netconf-ned-builder` NSO CLI tool by typing:

      make demo-nb

The `demo.sh` script uses the NSO CLI as the northbound interface and performs
the following steps:

0. Reset the demo
1. Setup the simulated device
2. Use the NETCONF <get-schema> operation to get the YANG models
3. Setup the NSO run-time directory using the `ncs-setup` script
4. Create and build the NETCONF NED package using the device YANG models using
   the `ncs-make-package` script
5. Start NSO
6. Configure the NSO device connection. For NED identity, select the identity
   created when building the package
7. Fetch the public SSH host key from the device and sync the configuration
   covered by the `ietf-hardware.yang` model from the device
8. Setup NETCONF notifications for the `hardware_state` NETCONF stream as
   defined by the `ietf-hardware.yang` model

From here, the NED is installed with NSO, and the demo script proceeds to show
how NSO can receive notifications, read operational state data, and configure
the device through the NED.

Further Reading
---------------

+ NSO Development Guide: NETCONF NED Development
+ The `demo.sh` script
