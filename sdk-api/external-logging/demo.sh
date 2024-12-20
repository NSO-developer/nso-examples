#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Sending log data to an external application demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the package\n${NC}"
make all

printf "\n${PURPLE}##### Start NSO\n${NC}"
make start

printf "\n${PURPLE}##### Sync the devices with NSO\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Enable trace logging\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ios* trace pretty
devices device ios1 trace-output external
commit
EOF

printf "\n\n${PURPLE}##### Set filtered field on the device\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ios* config ios:banner motd "secret motd"
commit
EOF

printf "\n\n${PURPLE}##### Compare the trace log with the filtered trace log\n${NC}"
make grep-logs

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
