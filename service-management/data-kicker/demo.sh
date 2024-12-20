#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Reactive FASTMAP with data kicker demo\n${NC}"
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network and NSO\n${NC}"
make start

printf "\n\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Create a service instance and verify that it didn't do anything, except creating a kicker from the Java service create() application\n${NC}"
ncs_cli -n -u admin -C << EOF
config
ppp-accounting ppp0
commit dry-run
commit
ppp-accounting ppp0 get-modifications
EOF

printf "\n\n${PURPLE}##### Show the kicker's data\n${NC}"
ncs_cli -n -u admin -C << EOF
unhide debug
show running-config kickers | nomore
EOF

printf "\n\n${PURPLE}##### Trigger the kicker. Commit the config change with the debug kicker flag\n${NC}"
ncs_cli -n -u admin -C << EOF
config
ppp-accounting-data ppp0 accounting radius
commit dry-run
commit | debug kicker
EOF

STATUS=$(echo "unhide debug; show running-config kickers | nomore" | ncs_cli -u admin -C)
PPP_KICKER="ppp-accounting-ppp0"
while [[ $STATUS == *$PPP_KICKER* ]]; do
    printf "\n${RED}Waiting for the ppp-accounting-ppp0 kicker to trigger\n${NC}"
    sleep 1
    STATUS=$(echo "unhide debug; show running-config kickers | nomore" | ncs_cli -u admin -C)
done

ncs_cli -n -u admin -C << EOF
ppp-accounting ppp0 get-modifications
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
