DP API External Database
========================

This example shows how to incorporate data into an NSO system where that data
is stored in another database outside of NSO instead of in CDB. The example
is implemented by an 'extern-db' package, which implements the application
using the Data Provider (DP) API for the external database.

The CDB package `packages/cdb/package-meta-data.xml` file shows that the
package has one component:

  <component>
    <name>External DB</name>
    <callback>
      <java-class-name>com.example.extdb.App</java-class-name>
    </callback>
  </component>

The Java code in `com.example.extdb.App` implements transaction and data
callbacks required for a data provider application.

The YANG model resides in the `external-db` package under
`packages/extern-db/src/yang/work.yang`. The YANG model:

1. Has configuration data
2. Has a callpoint called `tailf:callpoint workPoint`

So, the external data provider code needs to:

1. Register for providing data to the `workPoint` callpoint
2. The data provider application must participate in the transactions and is
   responsible for storing the data.

See the callbacks in `com.example.extdb.App`.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the package:

    make all

Start NSO:

    ncs

Trigger the external data provider application:

    ncs
    ncs_cli -u admin
    > show configuration work
    > configure
    % set work item 4 title "Finish the RFC" responsible martin comment \
      "Do this later"
    % commit
    % show full-configuration work item 4

Get some of the resulting log entries:

    cat logs/devel.log | grep "callpoint"

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: DP API
+ Java API reference documentation
+ The `demo.sh` script
+ `packages/extern-db/src/java/src/com/example/extdb/*.java`

