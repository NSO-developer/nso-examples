#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Java action demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the package and start NSO\n${NC}"
make all start

printf "'\n${PURPLE}##### Run the 'reboot' action\n${NC}"
ncs_cli -n -u admin -C << EOF
action-test system reboot
EOF

printf "\n\n${PURPLE}##### Run the 'restart' action\n${NC}"
ncs_cli -n -u admin -C << EOF
action-test system restart mode xx data { debug }
EOF

printf "\n\n${PURPLE}##### Run the 'verify' action - will finish after 10 seconds\n${NC}"
ncs_cli -n -u admin -C << EOF
action-test system verify | details debug
EOF

TIME=$(date +"%H:%M:%S")
printf "\n\n${PURPLE}##### Run the 'reset' action\n${NC}"
ncs_cli -n -u admin -C << EOF
config
action-test server test
action-test server test reset when $TIME
EOF

printf "'\n\n${PURPLE}##### Call the 'reboot' action from a Java application instead of the CLI\n${NC}"
ant -f packages/actions/src/java call-reboot

printf "\n${PURPLE}##### View the log output in ncs-java-vm.log\n${NC}"
cat logs/ncs-java-vm.log

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
