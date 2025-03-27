#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Router network CLI NED demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop &> /dev/null
set -e
make clean

printf "\n${PURPLE}##### Build the CLI NED package and generate netsim devies\n${NC}"
make all

printf "\n${PURPLE}##### Start the NSO and the ncs-netsim network\n${NC}"
make start

printf "\n${PURPLE}##### List the devices in the simulated network using 'ncs-netsim list'\n${NC}"
ncs-netsim list

printf "\n${PURPLE}##### Sync the configuration from the CLI devices\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
devices sync-from dry-run
devices sync-from
EOF

printf "\n\n${PURPLE}##### Show the device configuration\n${NC}"
ncs_cli -n -u admin -C <<< "show running-config devices device * config sys | nomore"

printf "\n\n${PURPLE}##### Show the device status\n${NC}"
ncs_cli -n -u admin -C <<< "show devices device * live-status sys | nomore"

printf "\n\n${PURPLE}##### Add configuration to the devices\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
devices device ex0..2 config r:sys routes inet route 10.2.0.0 24
next-hop 10.2.0.254 metric 20
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Show the routes configuration logging into the ex1 device\n${NC}"
ncs-netsim cli-c ex1 <<< "show running-config sys routes | nomore"

printf "\n\n${PURPLE}##### Execute the "archive-log" action from NSO\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C <<< "devices device ex0..2 config sys syslog server 10.3.4.5 archive-log archive-path test compress true"

printf "\n\n${PURPLE}##### Introduce a configuration mismatch by changing the configuration on the ex1 device\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs-netsim cli-c ex1 << EOF
config
no sys routes
commit
EOF

printf "\n\n${PURPLE}##### Check if the devices are in sync with NSO\n${NC}"
ncs_cli -n -u admin -C <<< "devices check-sync"

printf "\n\n${PURPLE}##### Check the diff between NSO and the device configuration and sync from NSO to the ex1 device\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
devices device ex1 sync-to dry-run
devices device ex1 sync-to
EOF

printf "\n\n${PURPLE}##### Check again if the devices are in sync with NSO\n${NC}"
ncs_cli -n -u admin -C <<< "devices check-sync"

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
