#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Data center connectivity demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network and NSO\n${NC}"
make start

printf "\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Create a connectivity service instance and add endpoints (access switches)\n${NC}"
ncs_cli -n -u admin -C << EOF
config
datacenter connectivity connection1 vlan 777 ip-network 10.1.1.0/24
datacenter connectivity connection1 connectivity-settings preserve-vlan-tags
datacenter connectivity connection1 endpoint catalyst0 GigabitEthernet0/5
datacenter connectivity connection1 endpoint dell0 GigabitEthernet0/2
datacenter connectivity connection1 endpoint catalyst2 GigabitEthernet0/7
datacenter connectivity connection1 endpoint catalyst3 GigabitEthernet0/7
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Update the VLAN parameter of the service\n${NC}"
ncs_cli -n -u admin -C << EOF
config
datacenter connectivity connection1 vlan 234
commit dry-run outformat native
commit
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
