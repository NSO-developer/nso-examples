Implementing Shared Settings
============================

This example includes an XML template service, dns. The intent of the
service is to set the DNS configuration on a managed device (each service
instance configures one device), using a shared data structure to hold DNS
server settings.

Start the example by running:

    make demo

Each service instance must reference a set of DNS servers defined under the
dns-options container. Each set is given a unique name which is then used
in the service instance to refer to it.

You can either configure service instances on your own or load a predefined
file using the `load merge` command in the config mode, e.g.:

    admin@ncs(config)# load merge example.cfg

and then observe the output it produces with `commit dry-run`.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
