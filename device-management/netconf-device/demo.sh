#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Simulate a NETCONF device demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
rm -rf README.ncs README.netsim logs ncs-cdb ncs.conf netsim packages scripts state

printf "${GREEN}##### Create a package amd setup NSO\n${NC}"
printf "${PURPLE}##### Create a package called 'host' from the YANG file and build the YANG module in the package\n${NC}"
ncs-make-package --netconf-ned . --dest packages/host --build host

printf "\n${PURPLE}##### Add a couple of simulated devices\n${NC}"
ncs-netsim create-network packages/host 2 h

printf "\n${PURPLE}##### Setup an NSO instance\n${NC}"
ncs-setup --dest .

printf "\n${GREEN}##### Start NSO and the simulated devices\n${NC}"
printf "${PURPLE}##### Start\n${NC}"
ncs-netsim start
ncs

printf "\n${PURPLE}##### Sync the configuration from the netsim devices to NSO\n${NC}"
ncs_cli -n -u admin -C << EOF
show packages
devices sync-from
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop NSO and the simulated devices\n${NC}"
    ncs --stop
    ncs-netsim stop

    printf "\n${PURPLE}##### Restore the example to the initial configuration\n${NC}"
    ncs-setup --reset

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    rm -rf README.ncs README.netsim logs ncs-cdb ncs.conf netsim packages scripts state
fi

printf "\n${GREEN}##### Done!\n${NC}"
