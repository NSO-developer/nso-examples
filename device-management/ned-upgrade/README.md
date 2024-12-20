NED Upgrade
===========

Demonstrates a way to add a new NED package to NSO without the need to
perform a full packages reload. The NED in the example contains backward
incompatible changes relating to an already provisioned service. This
requires a change to the service, specifically applying different
configuration through an XML template for each version of a NED.

The sample service used is the `acme-dns` service, which provisions DNS
configuration on a netsim device. In the new NED, the configuration must
now be done through the 'search' leaf-list, instead of the old 'domain'
leaf.

To allow per-device migration, the service uses an XML template with the
`if-ned-id` processing instruction, so it can be applied to a device with
either version of the NED.

To avoid potentially time-consuming packages reload, the code uses the
`packages add` action to add a new NED package, alongside all the existing
ones.

Because of the significant changes in the NED, the service requires an
updated XML template. NSO will start using the new template (or service
code) once you redeploy the service package, using the
`/packages/package/re-deploy` action, again avoiding the full packages reload.

This example complements the `ned-migration` example in the parent folder.

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
+ NSO Administration Guide: Adding NED Packages
+ ../ned-migration example
