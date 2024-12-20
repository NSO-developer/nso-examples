#!/bin/sh

set -eu
. helpers/utility.sh
. helpers/raft.sh

NONINTERACTIVE=${NONINTERACTIVE-}
if [ $# -gt 0 ] && [ "x$1" = x-n ]; then
    NONINTERACTIVE=yes; export NONINTERACTIVE
fi

echo_green "\n##### Raft-based HA Cluster Example"
echo "Note: To run this example without pausing, use: ./demo.sh -n"

next_step "Reset the example"
make stop clean

next_step "Create a self-signed CA to use for node authentication
and create a certificate for all three nodes"
make certs

next_step "Reset and prepare 3 new NSO nodes for the HA Raft cluster
Note: This example creates a new certificate on-demand during node setup
      if one is not found."
make all

next_step "Check Raft configuration in ncs.conf (node1)"
print_section node1/ncs.conf ha-raft | grep -v '^ *<!-- *<'

next_step "Start NSO processes"
make start

next_step "Initialize the HA cluster with create-cluster action"
node_cli 1 'ha-raft create-cluster member [ ncsd2@127.0.0.1 ncsd3@127.0.0.1 ]'
wait_for follower node_role 2
wait_for follower node_role 3

next_step "Check HA status on participating nodes"
node_show 1 ha-raft
node_show 2 ha-raft
node_show 3 ha-raft

next_step "Add dummy package to node 1"
( set -x; cp -a packages/dummy node1/packages/ )

next_step "packages reload replicates the package to the followers and loads it
on all nodes in the HA cluster"
node_cli 1 "packages reload"

next_step "Add some dummy config to node 1, replicated to other nodes"
node_cli 1 "\
config
dummies dummy d1 dummy 1.2.3.4
commit
end
show running-config dummies | nomore"

next_step "Check dummy config on other nodes"
node_show 2 running-config dummies
node_show 3 running-config dummies

next_step "Observe fail over by bringing down node 1 (current leader)"
echo "Status of nodes pre-failover:"
show_ha_roles 3
echo ""
NCS_IPC_PORT=4561 ncs --stop
echo "Waiting for new leader"
wait_while none find_leader 3 0
leader=$(find_leader 3)
echo "New leader node ${leader} elected"
echo ""
echo "Status of nodes post-failover:"
show_ha_roles 3

next_step "Add additional config on the leader"
node_cli $leader "\
config
dummies dummy d2-new dummy 2.1.3.4
commit
end
show running-config dummies | nomore"

next_step "Show new config is replicated to all remaining nodes"
node_show 2 running-config dummies
node_show 3 running-config dummies

next_step "Show down node is not connected to leader"
node_cli $leader show ha-raft status connected-node

next_step "Bring node 1 up, connecting it to leader"
( set -x; cd node1 && ncs )
wait_while none node_leader 1
node_cli 1 show ha-raft status role
node_cli $leader show ha-raft status connected-node

next_step " Observe the new data is replicated as well"
node_show 1 running-config dummies

show_done
