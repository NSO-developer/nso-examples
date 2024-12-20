Simulate a NETCONF Device
=========================

In this example, a YANG model, `host.yang`, describing something a device can
do, is made into an NSO NETCONF NED package using only the YANG file. `netsim`
devices are created to simulate a device implementing the `host.yang` YANG
model.

Ensure you have sourced the `ncsrc` file in `$NCS_DIR` to set up paths and
environment variables to NSO. This must be done before running NSO, and adding
that to your profile is recommended.

To run the steps below in this README from a demo shell script:

    ./demo.sh

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Create a Package and Setup NSO
------------------------------

Create a package called `host` from the YANG file and build the YANG module in
the package:

    ncs-make-package --netconf-ned . --dest packages/host --build host

If we want to add a couple of simulated devices:

    ncs-netsim create-network packages/host 2 h

Setup an NSO instance:

    ncs-setup --dest .

Start NSO and the Simulated Devices
-----------------------------------

Start and sync the configuration from the netsim devices to NSO:

    ncs-netsim start
    ncs
    ncs_cli -u admin

    > show packages
    > request devices sync-from
    > exit

The simulated devices will already be added to the config. For real devices add
them using, for example, the CLI. See the `netconf-ned` example.

Cleanup
-------

Stop NSO and the simulated devices before moving on:

    ncs --stop
    ncs-netsim stop

To restore the example to the initial configuration:

    ncs-setup --reset

or reset the example to its original files:

    rm -rf README.ncs README.netsim logs ncs-cdb ncs.conf netsim packages \
    scripts state

Further Reading
---------------

+ The `demo.sh` script
+ NSO Development Guide: NETCONF NED Development
+ Man-pages: ncs-make-package(1) ncs-netsim(1) ncs-setup(1)