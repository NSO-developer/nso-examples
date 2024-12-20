Java Action Callback Application
==================================

This example illustrates how to:

- Define a YANG action and how to register the application code for the action
- Write Java user code that handles action callback invocations

This example uses a package called `actions` with Java application code for
handling actions defined by its YANG model.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the package and start NSO:

    make all start

Run the `reboot` action:

    ncs_cli -u admin
    > action-test system reboot

Run the `restart` action:

    > request action-test system restart mode xx data { debug }
    time 10:54:45

Run the `verify` action (will sleep for 10s and can be aborted):

    > request action-test system verify
    consistent true

Run the `reset` action;

    > configure
    % set action-test server test
    % request action-test server test reset when 10:54:46
    time Fri Nov 08 10:54:46 CET 2016
    % exit
    > exit

Call the 'reboot' action from a `Sync.java` application instead of the CLI:

    ant -f packages/actions/src/java call-sync

View the log output in `logs/ncs-java-vm.log`:

    cat logs/ncs-java-vm.log

To study this example, first, take a look at the file
`packages/actions/package-meta-data.xml` that defines the `Action` component:

    <component>
      <name>actions</name>
      <callback>
        <java-class-name>com.example.actions.ActionCb</java-class-name>
      </callback>
    </component>

From the above, the class `ActionCB` has callbacks of some type, and we want
them registered. The YANG module in the `actions` package resides under
`./packages/actions/src/yang/action-test.yang`. The YANG module defines several
actions, and the code in `ActionCb.java`  and `AbortableVerifyAction.java`
implements the actions.

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Actions
+ The `demo.sh` script
+ Java API reference documentation: `ncs.dp` and `ncs.maagic`.
+ The package in the `./packages/actions` directory
+ More Java action examples in the NSO example set:
`find $NCS_DIR/examples.ncs/ -name "*.java" |xargs grep "ActionCBType.ACTION"`


