Implementing Code-based Service
===============================

This example includes a service, iface, using code to apply XML templates.
The intent of the service is to set the IP address on a selected interface
of a managed Cisco IOS-type device.

Start the example by running:

    make demo

The need for code arises because the service takes netmask input in CIDR
notation but the target device requires it in dot-decimal format. This
service is implemented in two variants, one using Python and one using Java,
but they produce the exact same result.

You can either configure service instances on your own or load a predefined
file using the `load merge` command in the config mode, e.g.:

    admin@ncs(config)# load merge example.cfg

and then observe the output it produces with `commit dry-run`.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
