#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Link migration nano service demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Set up a VPN link between two devices and set test-passed to true to complete it immediately\n${NC}"
ncs_cli -n -u admin -C << EOF
config
link t2 unit 17 vlan-id 1
link t2 endpoints ex1 eth0 ex2 eth0 test-passed true
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the plan\n${NC}"
ncs_cli -n -u admin -C << EOF
show link t2 plan component * state * status
EOF

printf "\n\n${PURPLE}##### Change the link by changing the interface on one of the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
config
no link t2 endpoints ex1 eth0 ex2 eth0
link t2 endpoints ex1 eth0 ex2 eth1
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Check what the service has configured at this point\n${NC}"
ncs_cli -n -u admin -C << EOF
link t2 get-modifications
EOF

printf "\n\n${PURPLE}##### Set the test-passed to true\n${NC}"
ncs_cli -n -u admin -C << EOF
config
link t2 endpoints ex1 eth0 ex2 eth1 test-passed true
commit
EOF

printf "\n\n${PURPLE}##### Check the service modifications\n${NC}"
ncs_cli -n -u admin -C << EOF
link t2 get-modifications
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim stop

    printf "\n${PURPLE}##### Reset the example to its original files\n${NC}"
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
