Service Operational Data
========================

This example includes a service, iface, that exposes some operational data.
The intent of the service is to set the IP address on a selected interface
of a managed Cisco IOS-type device.

Start the example by running:

    make demo

The operational leaf in this service simply tracks the last result of the
test-enabled service action. You can display its value using the show
command.

You can either configure service instances on your own or use a predefined
one, named instance1, to test:

    admin@ncs# iface instance1 test-enabled
    admin@ncs# show iface instance1 last-test-status

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
