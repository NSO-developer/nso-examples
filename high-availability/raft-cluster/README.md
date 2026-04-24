HA Raft Cluster
===============

The example shows the steps to initially set up an HA Raft cluster.
It includes securing the cluster by provisioning node certificates
and the way the cluster behaves when nodes go down and come up again.

Cluster setup steps consist of first preparing each individual node
(`ncs.conf`, certificate and key pair) and then running the
`ha-raft create-cluster` action. The certificates are generated with
the help of certificate management scripts in `../ca/`.

Note that when using seed nodes for discovery, the potential cluster
member nodes will show in the `ha-raft status connected-node` list
before you actually join them to the cluster. This signifies the
underlying transport (TCP/TLS) connection is working correctly.

A simple, do-nothing service called `dummies` is used for observing
how the replication takes place.

To start the example, run:

    make demo

The demo script will guide you through the procedure step by step,
providing explanations along the way. Alternatively, you can use
`./demo.sh -n` to run the example straight through.

If you wish to simply use this example as a sandbox for your own
exploration or testing, you can start with a clean state where the
cluster is initiated with `n1` as the leader and `n2` and `n3` as
followers:

    make stop clean
    make all start
    make configure

Also available are individual node management targets, such as
`make start-nodeX`, `make stop-nodeX`, and `make cliX` for `nX`.


Cleanup
-------

After you are finished, run:

    make stop clean

to clean up and release used resources.

Further Reading
---------------

+ NSO Administration Guide: High Availability
* The `demo.sh` script
