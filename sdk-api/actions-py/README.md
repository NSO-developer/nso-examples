Python Action Callback Application
==================================

This example illustrates how to:

- Define a YANG action and how to register the application code for the action
- Write Python user code that handles action callback invocations

This example uses a package called '`actions` with Python application code for
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

Run the `verify` action:

    > request action-test system verify
    consistent true

Change the `sys-name` to have the `verify` action return `false`:

    > configure
    % set action-test system sys-name please-return-false
    % request action-test system verify
    consistent false

Run the `reset` action:

    > configure
    % set action-test server test
    % request action-test server test reset when 10:54:46
    time 10:54:46

View the log output in `logs/ncs-python-vm-actions.log`:

    cat logs/ncs-python-vm-actions.log

There will be one log file for each Python VM that has started. In this
example, only one VM has been started.

To study this example, first, take a look at the file
`packages/actions/package-meta-data.xml` that defines the `Action` component:

    <component>
      <name>actions</name>
      <callback>
        <python-class-name>Action</python-class-name>
      </callback>
    </component>

From the above, the class `Action` has callbacks of some type, and we want them
registered. The YANG module in the `actions` package resides under
`./packages/actions/src/yang/action-test.yang`. The YANG module defines several
actions, and the code in `action.py` implements the actions.

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Actions
+ The `demo.sh` script
+ Python API reference documentation: `ncs.dp` and `ncs.maagic`.
+ The package in the `./packages/actions` directory
+ More Python action examples in the NSO example set:
`find $NCS_DIR/examples.ncs/ -name "*.py" |xargs grep "@Action.action"`