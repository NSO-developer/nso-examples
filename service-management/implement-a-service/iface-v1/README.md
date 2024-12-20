Template-first Service Design
=============================

This example includes an XML template service, iface. The intent of the
service is to set the IP address on a selected interface of a managed Cisco
IOS-type device. It serves as a starting point for more complex service
logic.

Start the example by running:

    make demo

You can either configure service instances on your own or load a predefined
file using the `load merge` command in the config mode, e.g.:

    admin@ncs(config)# load merge example.cfg

and then observe the output it produces with `commit dry-run`.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
