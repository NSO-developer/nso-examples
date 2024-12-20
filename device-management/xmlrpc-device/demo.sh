#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Create and install a generic NED demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the example\n${NC}"
make all

printf "\n${PURPLE}##### Start three XML-RPC server instances and the Java applications needed to configure the devices\n${NC}"
make start

printf "\n${PURPLE}##### Configure the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices sync-from
devices device x1..3 config if:interface eth0 mtu 1200
commit | details
EOF

printf "\n\n${PURPLE}##### Turn on the NED trace\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices global-settings trace raw
commit
devices disconnect
EOF

printf "\n\n${PURPLE}##### Change some configuration to the devices through NSO and see the commands issued by NSO in the NED trace\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device x1..3 config if:interface eth0 mtu 1300
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### The commands issued by NSO in the NED trace:\n${NC}"
cat logs/ned-genxmlrpc-x1.trace

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
