#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Simple MPLS layer3 VPN demo\n${NC}"
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
printf "${PURPLE}##### Setup the environment and start the simulated network\n${NC}"
make all start

printf "\n${PURPLE}##### Sync the configuration from all network devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${GREEN}##### VPN service configuration\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Configure a VPN network\n${NC}"
ncs_cli -n -u admin -C << EOF
autowizard false
config
vpn l3vpn volvo endpoint c1 as-number 65001
ce device ce0 local interface-name GigabitEthernet interface-number 0/9 ip-address 192.168.0.1
ce link interface-name GigabitEthernet interface-number 0/2 ip-address 10.1.1.1
pe device pe2 link interface-name GigabitEthernet interface-number 0/0/0/1 ip-address 10.1.1.2
vpn l3vpn volvo endpoint c2 as-number 65001
ce device ce2 local interface-name GigabitEthernet interface-number 0/3 ip-address 192.168.1.1
ce link interface-name GigabitEthernet interface-number 0/1 ip-address 10.2.1.1
pe device pe2 link interface-name GigabitEthernet interface-number 0/0/0/2 ip-address 10.2.1.2
commit dry-run outformat native
commit dry-run | debug template
commit
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    make stop

    printf "\n${PURPLE}##### Reset the example to its original files\n${NC}"
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
