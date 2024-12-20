NED Migration
=============

A step-by-step guide to migrating devices between different NED versions
with the `/devices/device/migrate` and `/devices/migrate` actions. As the NED
may contain backward-incompatible changes, the example shows how you can
enumerate the ones that relate to existing configurations in the NSO.

Using a sample `acme-dns` service, which provisions DNS configuration
on a netsim device, the example showcases the recommended procedure
for migrating devices to a different, usually newer, NED.

An important step in the migration process is identifying the affected
configuration and services, which may need to be updated and redeployed.

This example complements the `ned-upgrade` example in the parent folder.

Running the Example
-------------------

To start the example, run:

    make demo

The demo script will guide you through the procedure step by step,
providing explanations along the way. Alternatively, you can use
`make demo-nonstop` to run the example straight through.

If you wish to simply use this example as a sandbox for your own
exploration or testing, you can start with a clean state:

    make stop clean
    make all start

Cleanup
-------

After you are finished, run:

    make stop clean

to clean up and release used resources.


Further Reading
---------------

+ NSO Administration Guide: NED Migration
+ ../ned-upgrade example
