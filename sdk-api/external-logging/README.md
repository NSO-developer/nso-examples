Sending Log Data to an External Application
===========================================

This example demonstrates using an external application to filter
NED trace log data. The external logging functionality is intended as
a development feature.

This example uses a Python script to filter CLI trace data. However, the
feature is not limited to CLI and can filter any NED trace output by reading
the log data from standard input and then processing the required data. The
external log configuration settings are added to `ncs.conf` to have NSO call
the Python script.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script.

Enable external logging in `ncs.conf`:

    make all

Start NSO:

    make start

Sync the devices with NSO

    ncs_cli -u admin -g admin -C
    # devices sync-from

Enable trace logging:

    # config
    (config)# devices device ios* trace pretty
    (config)# devices device ios1 trace-output external
    (config)# commit

Set filtered field on the device:

    (config)# devices device ios* config ios:banner motd "secret motd"
    (config)# commit

Compare the trace log with the filtered trace log and notice that the
`secret` from the `motd` content is replaced with `********`:

    make grep-logs

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: External Logging
+ The `demo.sh` script
+ The `Makefile` changes to `ncs.conf`.