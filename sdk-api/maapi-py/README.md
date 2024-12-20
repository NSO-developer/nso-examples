The Python MAAPI API
====================

This example shows an introduction to the Python MAAPI API

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script.

Build the packages:

    make all

Start the `ncs-netsim` network:

    ncs-netsim start

Start NSO:

    ncs

Sync the configuration from the devices:

    ncs_cli -u admin -C
    # devices sync-from

Low Level MAAPI Python API
--------------------------

The low level read example prints name, address, and port for the three
devices.

    python3 low-level-read.py
    llr: ex0 127.0.0.1 12022
    llr: ex1 127.0.0.1 12023
    llr: ex2 127.0.0.1 12024

High Level MAAPI Python API
---------------------------

The high level read example is similar to the low-level read example, but uses
"with contexts" to simplify the resource handling.

The device names are read from the device list with a cursor instance.

    python3 high-level-read.py
    hlr: ex0 127.0.0.1 12022
    hlr: ex1 127.0.0.1 12023
    hlr: ex2 127.0.0.1 12024

Cleanup
-------

Stop all daemons and clean all created files:

    ncs-netsim stop
    ncs --stop
    make clean

Further Reading
---------------

+ NSO Development Guide: RESTCONF API
+ The `high-level-read.py` and `low-level-read.py` scripts



