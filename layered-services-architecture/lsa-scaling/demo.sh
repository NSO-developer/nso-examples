#!/usr/bin/env bash
if [ -n "${NCS_IPC_PATH}" ]; then
ENV="NCS_IPC_PATH=${NCS_IPC_PATH}."
else
ENV="NCS_IPC_PORT="
fi

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Layered Services Architecture Scaling Demo\n${NC}"
printf "${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop
set -e
make clean
printf "\n${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the simulated network and NSO\n${NC}"
make start

printf "\n${PURPLE}##### Sync the configuration from the remote LSA-node\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Configure a CFS VLAN service instance\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77
commit dry-run
commit
EOF

printf "\n\n${GREEN}##### Moving a Device\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Move device ex0 from lower-nso-1 to lower-nso-2\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
move-device move src-nso lower-nso-1 dest-nso lower-nso-2 device-name ex0
cfs-vlan v1 get-modifications
EOF

printf "\n\n${PURPLE}##### Move a device by reading the configuration from a common store\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
move-device move src-nso lower-nso-2 dest-nso lower-nso-1 device-name ex0 read-from-db
cfs-vlan v1 get-modifications
EOF

printf "\n\n${GREEN}##### Re-balancing the Lower Layer\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Dry run a re-balance of the lower LSA layer\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
move-device rebalance dry-run
EOF

printf "\n\n${PURPLE}##### Dry run a re-balance between a set of lower LSA nodes\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
move-device rebalance nodes [ lower-nso-2 lower-nso-3 ] dry-run
EOF

printf "\n\n${PURPLE}##### Re-balance the lower LSA layer\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
move-device rebalance
EOF

printf "\n\n${PURPLE}##### Inspect the dispatch-map\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
show running-config dispatch-map | nomore
EOF

printf "\n\n${GREEN}##### Evacuating a Lower LSA Node\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Dry run moving all devices on lower-nso-1 to the other lower LSA nodes in the cluster\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
move-device evacuate node lower-nso-1 dry-run
EOF

printf "\n\n${PURPLE}##### Move all devices from lower-nso-1 to lower-nso-3\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
move-device evacuate node lower-nso-1 dest-nodes [ lower-nso-3 ]
EOF

printf "\n\n${PURPLE}##### Inspect the dispatch-map\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
show running-config dispatch-map | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
