Aggregate State Data from Devices
=================================

This example illustrates how to:

- Write a user-defined data provider that displays user-defined runtime data.
- How to aggregate runtime data from the network and display it at a
  "higher level".

The example uses two NSO packages. The `router` package, introduced by the
`../router-network` example, and a package called `stats` that includes Java code
examples.

    ls -1 ./packages
    stats
    router

To run the steps below in this README from a demo shell script:

    make demo

The demo script implements the below steps.

Running the Example
-------------------

Build the two packages and create the netsim network:

    make all

Start the netsim network:

    ncs-netsim start
    DEVICE ex0 OK STARTED
    DEVICE ex1 OK STARTED
    DEVICE ex2 OK STARTED

All the code for this example resides in the `./packages/stats` package.

Start NSO:

    ncs

NSO will load the two packages, load the data models, and start the Java code
from the packages.

The example is initially configured with device groups. See the init file under
`./ncs-cdb/ncs_init.xml` and from the CLI after being loaded into CDB at
startup:

    ncs_cli -u admin
    > show configuration devices device-group
    device-group g1 {
        device-name [ ex0 ex1 ];
    }
    device-group g2 {
        device-name  [ ex0 ex2 ];
        device-group [ g3 ];
    }
    device-group g3 {
        device-name [ ex1 ];
    }

Invoke the example code that aggregates the device interface statistics:

    > show aggregate-stats
    device-group g1 {
        packet-errors  0;
        packet-dropped 4;
    }
    device-group g2 {
        packet-errors  0;
        packet-dropped 6;
    }
    device-group g3 {
        packet-errors  0;
        packet-dropped 2;
    }

The Java application log output can be found in the `logs/ncs-java-vm.log`
file.

To show the data on the devices that the Java code aggregates from:

    > show devices device ex0..3 live-status sys interfaces interface status \
      receive

The `packages/stats/package-meta-data.xml` file defines one component:

    <component>
      <name>stats</name>
      <callback>
        <java-class-name>com.example.stats.Stats</java-class-name>
      </callback>
    </component>

The Java class `Stats` has callbacks that register with NSO. The YANG files in
the `stats` package are found under `./packages/stats/src/yang`. The file
`packages/stats/src/yang/aggregate.yang` defines a list called `device-group`.
This list maps to the built-in list `/devices/device-group`. The Java code from
`packages/stats/src/java/src/com/example/stats/Stats.java` uses the keys from
`/devices/device-group`.

The Java code illustrates:

- How to define user code that aggregates runtime data from the network and
  displays it at a higher level. The YANG and Java code collects interface
  error counters and displays them per device group.

- How to use NAVU (Navigation Utilities), an API that provides
  increased accessibility to the NSO populated data model tree: NAVU tree. NAVU
  caches data, so once we've read data into the NAVU tree, implicitly using
  MAAPI, the data is cached and will not be re-read. The data can become stale
  if a transaction is long-lived, which could be the case for a CLI session or
  WebUI session. The code in this example has a timeout of 5 seconds, after
  which it throws away the NAVU tree and creates a new empty one.

Cleanup
-------

To have NSO re-initialized from the ncs-cdb/*.xml files when restarted:

    ncs --stop
    ncs-setup --reset
    ncs

To reset and restart the netsim network:

    ncs-netsim stop
    ncs-netsim reset
    ncs-netsim start

Or:

    ncs-netsim restart

To clean all created files after stopping NSO and the simulated devices:

    make clean

Further Reading
---------------

+ NSO Development Guide: DP API
+ NSO Development Guide: NAAVU API
* NSO Development Guide: The `package-meta-data.xml` File
+ The `demo.sh` script