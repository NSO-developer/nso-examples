External High Availability Framework Example
============================================

This small example shows how to use the NSO HA (high availability) framework.
The example builds and starts two NSO nodes, `n1` and `n2`. Both nodes run on
the same host (localhost) using different `NCS_IPC_PORT`s.

The YANG data models are simple. See the `$NCS_DIR/packages/services/manual-ha`
package.

The YANG modules define action points, allowing the operator to set the current
node to primary, secondary, or none. The Java and YANG code,
available in the `$NCS_DIR/packages/services/manual-ha` package, implements the
actions using the Java API for HA and action callbacks.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the example:

    make all

Start the simulated network:

    ncs-netsim start

Start the NSO nodes:

    NCS_IPC_PORT=5757 sname=n1 NCS_HA_NODE=n1 ncs --cd ./n1 -c ncs.conf
    NCS_IPC_PORT=5758 sname=n2 NCS_HA_NODE=n2 ncs --cd ./n2 -c ncs.conf

Get the status of the `n1` and `n2` nodes:

    NCS_IPC_PORT=5757 ncs_cli -u admin
    > request ha-config status
    > exit
    NCS_IPC_PORT=5758 ncs_cli -u admin
    > request ha-config status
    > exit


Have the `n1` node be primary:

    NCS_IPC_PORT=5757 ncs_cli -u admin
    > request ha-config be-primary
    > request ha-config status
    > exit

Have the `n2` node be secondary:

    NCS_IPC_PORT=5758 ncs_cli -u admin
    > request ha-config be-secondary
    > request ha-config status

Cleanup
-------

Stop the two NSO instances:

    NCS_IPC_PORT=5757 sname=n1 ncs --stop
    NCS_IPC_PORT=5758 sname=n2 ncs --stop

Stop the netsim network:

    ncs-netsim stop

To clean all created files after stopping NSO and the simulated devices:

    make clean

Further Reading
---------------

+ NSO Administration Guide: HA Framework Requirements
+ The `demo.sh` script
* The `packages/manual-ha` package
* The Java, Python, Erlang HA API and the confd_lib_ha(3) man page
