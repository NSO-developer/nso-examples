Service Operational Status
==========================

This example includes a service, iface, that manages an interface of a
selected device. The service first configures the interface with an IP
address and then tracks its link state. This allows NSO to better reflect
the operational state of the service in the network.

Start the example by running:

    make demo

You can either configure service instances on your own or use a predefined
one, such as instance1, to inspect the YANG Push subscription:

    admin@ncs# show running-config devices device r0 telemetry
    admin@ncs# show devices device r0 telemetry

The subscription allows the device to push the updated operational data
to NSO right away. You can simulate the link connect/disconnect events on
the r0 device with `make` commands in a separate terminal:

    make link-connect
    make link-disconnect

If you wish, you can also inspect the YANG Push NETCONF messages these
actions generate on the device by using the `netconf-console` tool in a
separate terminal (requires Python `paramiko` package to be installed):

    make subscribe-on-change

NSO uses these messages to update the device operational data in real time:

    admin@ncs# show devices device r0 live-status sys interfaces

The service configures a telemetry kicker with the service `update-status`
action that recomputes the service operational status when the link status
changes:

    admin@ncs# unhide debug
    admin@ncs# show running-config kickers telemetry-kicker iface-instance1
    admin@ncs# show iface instance1 status

In this example, the service status simply reflects the interface status,
but in your own services the `update-status` action could check a number
of things and even run additional tests.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Operation & Usage Guide: Device Manager
+ NSO Development Guide: Implementing Services
