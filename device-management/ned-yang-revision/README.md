Upgrading NED YANG Model Revisions
==================================

If you upgrade a managed device, such as installing a new firmware, the device
data model can change in a backward-incompatible way. If that is the case, and
you have other devices managed by the same NED, you need to create a new NED
with an updated YANG model that uses a different NED ID where the major version
number of the NED package is stepped up.

If the change is backward-compatible, as specified by YANG RFC 6020 and 7950,
where you, for example, just added something to an existing YANG model, you only
need to step up the YANG model revision, update the package version minor
number and rebuild the existing NED.

When managing multiple devices with a single NED, you sometimes want to upgrade
devices A and B but not C. If the change is backward-compatible, you can add
two revisions of the same YANG model to the NED, separating them by revision.
For example, `router@2020-02-27.yang` and `router@2020-09-18.yang`.

When building the package using `ncsc`, `ncsc` will verify that the changes are
backward-compatible and use revision merge to merge the two YANG versions.
Devices that support the older version of the YANG model will then not get
configuration data from NSO that the newer YANG model defines, and vice versa.
Only the package version minor number needs to be stepped up, and the NED ID
can remain the same.

This example shows how a YANG model upgrade to one of two managed devices can
be performed. The changes to the original `router@2020-02-27.yang` YANG model
in a 1.0 version NED are first added using a backward-compatible
`router@2020-09-18.yang`, where the NED version is updated to 1.0.1. When next
upgraded with a non-backward compatible `router@2022-01-25.yang` version, a
separate 1.1 version NED is created with a different NED ID.

Running the Example
-------------------

A shell script that runs the example is available. Run the script and upgrade
the `router.yang` model for the `ex0` device in two steps, first for
backward-compatible changes using the NSO revision merge feature, and then
for non-backward compatible changes by creating a new NED as the NSO CDM
feature allows us to do. Run the demo by typing:

    make demo

The above shell script uses the NSO CLI as the northbound interface and a
Python script variant uses the NSO RESTCONF northbound interface. Run it by
typing:

    make demo-rc

See the `demo.sh` CLI shell script and `demo_rc.py` Python script + the
`Makefile` as called from the scripts for details on the steps to perform the
backward-compatible and non-backward compatible upgrades.

Cleanup
-------

After you are finished, run:

    make stop clean

to clean up and release used resources.

Further Reading
---------------

+ NSO NED Development Guide: NED Migration
+ NSO Administrator Guide: NED Migration
+ NSO ncsc(1) man page: The --ncs-compile-bundle option
+ The demo.sh and demo_rc.py scripts
