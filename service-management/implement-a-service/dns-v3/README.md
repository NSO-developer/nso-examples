Configuring Multiple Devices
============================

This example includes an XML template service, dns, where a single service
instance configures multiple devices. The intent of the service is to set
the DNS configuration on a managed device.

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
