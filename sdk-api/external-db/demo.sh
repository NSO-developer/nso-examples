#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### DP API External Database demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the package\n${NC}"
make all

printf "\n${PURPLE}##### Start NSO and the data provider application\n${NC}"
ncs

printf "\n\n${PURPLE}##### Trigger the external data provider application\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config work
config
work item 4 title "Finish the RFC" responsible Martin comment "Do this later"
commit
show full-configuration work item 4
EOF

printf "\n\n${PURPLE}##### Resulting log entries in logs/devel.log\n${NC}"
cat logs/devel.log | grep "callpoint"

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO\n${NC}"
    make stop
    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
