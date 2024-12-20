Service Actions
===============

This example includes a service, iface, that provides a custom action.
The intent of the service is to set the IP address on a selected interface
of a managed Cisco IOS-type device and the action should verify that was
successfully done.

Start the example by running:

    make demo

The action is called test-enabled and is defined on a service instance.
It contains a dummy implementation that always returns 'unknown'.

You can either configure service instances on your own or use a predefined
one, named instance1, to test the action:

    admin@ncs# iface instance1 test-enabled

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Development Guide: Implementing Services
