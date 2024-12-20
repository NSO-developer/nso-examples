Service Pre- and Post-modification
==================================

This example includes a service, `iface`, that uses the post-modification
functionality to deploy a default configuration to an interface, once the
service is removed. This configuration ensures the interface is shut down
and configured with an unused VLAN (here VLAN number 2).

Start the example by running:

    make demo

The reset-to-default functionality is implemented with the help of opaque
properties, since the service instance is gone on delete.

A sample service instance is preconfigured for you. Compare the initial
service modifications with what is left after the service is removed:

    admin@ncs# config
    admin@ncs(config)# iface instance1 get-modifications
    admin@ncs(config)# no iface instance1
    admin@ncs(config)# commit dry-run
    admin@ncs(config)# commit

When you are done with the example, run:

    make stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Development Guide: Services Deep Dive
