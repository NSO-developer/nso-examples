#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Management Agent API (MAAPI) demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the two packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Low level read example\n${NC}"
python3 low-level-read.py

printf "\n\n${PURPLE}##### High level read example\n${NC}"
python3 high-level-read.py

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim stop
    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
