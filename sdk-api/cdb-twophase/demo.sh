#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "${GREEN}##### Two phase commit mandatory subscriber blast radius demo\n${NC}"
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${PURPLE}##### Build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start NSO, the subscribers, and the netsim network\n${NC}"
make start

printf "\n${PURPLE}##### Configure the device blast radius to maximum two devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
config
devices blast-radius max-devices 2
commit
EOF

printf "\n\n${PURPLE}##### Configure two devices\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 config sys interfaces interface eth42 enabled
devices device ex1 config sys interfaces interface eth42 enabled
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Configure three devices causing the commit to be aborted in the prepare phase\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 config sys interfaces interface eth42 description "test1"
devices device ex1 config sys interfaces interface eth42 description "test2"
devices device ex2 config sys interfaces interface eth42 description "test3"
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Show the logs/ncs-python-vm-device-blast-radius.log file with the aborted commit\n${NC}"
cat logs/ncs-python-vm-device-blast-radius.log

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make stop
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"

