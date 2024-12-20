Periodic Compaction Automation with NSO Services
================================================

This example showcases how compaction could be automated using the NSO
scheduler with a simple, dedicated compaction task.

Description
-----------

This example creates an NSO task `compaction-task`. The task uses the CDB API
to fetch current status information about the CDB data stores when triggered.
Based on this information, the task requests journal compaction for those
data stores where it is deemed required.

Furthermore, the example uses the NSO scheduler to set up an automatic trigger
for the compaction task.

Note that the `ncs.conf` used in this example has been prepared with manual
compaction enabled and delayed compaction disabled.

    <compaction>
        <journal-compaction>manual</journal-compaction>
        <delayed-compaction-timeout>PT0S</delayed-compaction-timeout>
    </compaction>

Running the Example
-------------------

A shell script that runs the example is available. Run the script by typing:

    make showcase

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Scheduler
+ NSO Development Guide: Creating a Service
+ NSO Administration Guide: Compaction
+ The `showcase.sh` script
