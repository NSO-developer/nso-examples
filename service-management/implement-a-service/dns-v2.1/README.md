Implementing Service Model
==========================

The YANG service model specifies the input parameters a service takes.
This example includes an XML template service, dns, in the first stages
of development. The intent of the service is to set the DNS configuration
on a managed device (each service instance configures one device),
extending the functionality provided in the version 1 of the service.

Start the example by running:

    make demo

This version adds validation and dns-server-ip to the model, showing how
the service model evolves during development.

You can either configure service instances on your own or load a predefined
file using the `load merge` command in the config mode, e.g.:

    admin@ncs(config)# load merge example.cfg

and then observe the output it produces with `commit dry-run`.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
