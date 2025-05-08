Confirm Network State
=====================

This example includes a service, iface, that sets IP and DHCP snooping
configuration on an interface. The service has an out-of-band policy defined
under `services out-of-band policy` and a few sample instances preconfigured.
The managed devices have confirm-network-state handling configured and have
out-of-band changes, some related to these services and some not.

Start the example by running:

    make demo

To view the out-of-band changes, run:

    admin@ncs# devices device * compare-config

NSO processes the out-of-band changes during a related change or a sync-from
operation. You can observe the effects on service get-modifications, for
example:

    admin@ncs# devices device c1 sync-from
    admin@ncs# iface instance1 get-modifications forward { with-out-of-band }

You can then further explore the re-deploy reconcile variants or modify the
service to see the difference between service out-of-band policy actions:

    admin@ncs(config)# iface instance1 interface 0/4
    admin@ncs(config)# commit dry-run

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Operation & Usage Guide: Out-of-band Interoperation
