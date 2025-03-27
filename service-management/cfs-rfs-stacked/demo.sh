#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Creating a stacked customer-resource facing service demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### Instantiate the managed interfaces\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Add customer-facing service (CFS) configuration\n${NC}"
ncs_cli -n -u admin -C << EOF
config
cfs-vlans vlan s1 description x iface ethX unit 1 vid 3
cfs-vlans vlan s2 description x iface ethX unit 2 vid 4
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Show the created CFS configuration\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config cfs-vlans | nomore
EOF

printf "\n\n${PURPLE}##### Show the created resource-facing service (RFS) configuration with the service metadata from the CFS\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config rfs-vlans | display service-meta-data | nomore
EOF

printf "\n\n${PURPLE}##### Show the created device configuration with the service metadata from the RFS\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device ex0..2 config r:sys interfaces interface ethX | display service-meta-data | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make stop clean
fi
printf "\n${GREEN}##### Done!\n${NC}"
