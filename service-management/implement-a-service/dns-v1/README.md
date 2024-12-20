Implementing Service Template
=============================

This example includes an XML template service, dns, in the first stages
of development. The intent of the service is to set the DNS configuration
on a managed device (each service instance configures one device).

Start the example by running:

    make demo

The service uses a static template, which always produces the same
configuration. This is not very useful on its own but serves as a base for
further development.

You can either configure service instances on your own or load a predefined
file using the `load merge` command in the config mode, e.g.:

    admin@ncs(config)# load merge example.cfg

and then observe the output it produces with `commit dry-run`.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
