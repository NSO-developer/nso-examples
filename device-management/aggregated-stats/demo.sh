#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Aggregate state data from devices demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the two packages and create the netsim network\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### The XML init file containing the device group configuration\n${NC}"
cat ./ncs-cdb/ncs_init.xml

printf "\n${PURPLE}##### The configuration from the CLI after being loaded into CDB at startup\n${NC}"
ncs_cli -n -u admin << EOF
show configuration devices device-group | nomore
EOF

printf "\n\n${PURPLE}##### Invoke the example code that aggregates the device interface statistics\n${NC}"
ncs_cli -n -u admin << EOF
show aggregate-stats | nomore
EOF

printf "\n\n${PURPLE}##### The Java application log output\n${NC}"
cat logs/ncs-java-vm.log

printf "\n${PURPLE}##### Show the data on the devices that the Java code aggregates from\n${NC}"
ncs_cli -n -u admin << EOF
show devices device ex0..3 live-status sys interfaces interface status receive | nomore
EOF

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
    printf "\n${PURPLE}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
