Service Delete Best Practice
============================

This example includes a service, `bgp-routing`, that configures a new BGP
neighbor. However, this configuration is not compatible with the pre-existing
configuration (a different BGP neighbor), which must be removed as part of
service provisioning. As a best practice, the service implements "shared
delete" through another helper service, called `bgp-clean`.

Start the example by running:

    make demo

By going through `bgp-clean` service, bgp-routing can rely on NSO tracking
which service instances still require removal of the incompatible BGP neighbor.
You can observe this through service meta data on the `bgp-clean` service
instance. Since there is only ever a single instance of `bgp-clean` service per
device, there is no risk of multiple overlapping or interfering deletes.

A number of sample service instances are preconfigured for you. The `bad1` and
`bad2` instances do not use the helper `bgp-clean`. If you remove the one that
was provisioned first, the `1.2.3.4` neighbor will show up again. Compare this
to `good1` and `good2` instance, where removal of both is required for initial
configuration to be reinstated.

When you are done with the example, run:

    make stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Development Guide: Services Deep Dive
