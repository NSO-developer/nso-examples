Supporting Different Device Types
=================================

This example includes a service, iface, using code to apply XML templates.
The intent of the service is to set the IP address on a selected interface
of a managed device, using configuration specific to this device type.

Start the example by running:

    make demo

The service supports two types of devices, using cisco-ios and router-nc
NEDs. Additionally, it provisions DHCP snooping configuration if the device
uses the required NED version.

You can either configure service instances on your own or load a predefined
file using the `load merge` command in the config mode, e.g.:

    admin@ncs(config)# load merge example.cfg

and then observe the output it produces with `commit dry-run`.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
