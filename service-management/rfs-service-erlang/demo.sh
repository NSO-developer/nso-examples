#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Creating a resource facing service Erlang application demo\n${NC}"
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

printf "\n${PURPLE}##### The XML init file containing the device configuration\n${NC}"
cat ./ncs-cdb/ncs_init.xml

printf "\n\n${PURPLE}##### Instantiate the managed interfaces\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Add configuration shared between multiple service instances\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services vlan s1 description x iface ethX unit 1 vid 3
services vlan s2 description x iface ethX unit 2 vid 4
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Show the created device configuration with the service metadata\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device ex0..2 config r:sys | display service-meta-data | nomore
EOF

printf "\n\n${PURPLE}##### Get the created configuration and service metadata over MAAPI\n${NC}"
ncs_load -M -Fp -P /devices/device/config/sys/interfaces/interface

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim stop

    printf "\n${PURPLE}##### Restore the example to the initial configuration\n${NC}"
    ncs-netsim reset
    ncs-setup --reset

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
