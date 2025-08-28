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

printf "\n${GREEN}##### MPLS Layer3 VPN LSA Demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the example\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Setup the environment\n${NC}"
make all

printf "\n${PURPLE}##### Start the simulated network and NSO\n${NC}"
make start

printf "\n${PURPLE}##### Get the status from NSO on all nodes\n${NC}"
make status

printf "\n${PURPLE}##### Configure a VPN network from the CFS NSO node\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn l3vpn volvo route-distinguisher 999 endpoint main-office ce-device ce6 ce-interface GigabitEthernet0/11 ip-network 10.10.1.0/24 as-number 65101 bandwidth 12000000
vpn l3vpn volvo route-distinguisher 999 endpoint branch-office1 ce-device ce1 ce-interface GigabitEthernet0/11 ip-network 10.7.7.0/24 as-number 65102 bandwidth 6000000
vpn l3vpn volvo route-distinguisher 999 endpoint branch-office2 ce-device ce4 ce-interface GigabitEthernet0/18 ip-network 10.8.8.0/24 as-number 65103 bandwidth 300000
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Verify that the nso-1 RFS node has only modified the CE devices\n${NC}"
env ${ENV}4692 ncs_cli -n -u admin -C << EOF
vpn l3vpn volvo get-modifications
EOF

printf "\n\n${PURPLE}##### Verify that the nso-3 RFS node has modified the affected PE routers\n${NC}"
env ${ENV}4694 ncs_cli -n -u admin -C << EOF
vpn l3vpn volvo get-modifications
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    make stop

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
