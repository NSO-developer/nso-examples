#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}#### Kicker demo\n${NC}"
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

printf "\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${GREEN}#### Data kicker demo\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Create a data-kicker\n${NC}"
ncs_cli -n -u admin -C << EOF
unhide debug
config
kickers data-kicker a1 monitor /services/properties/wsp:web-site/profile kick-node /services/wse:actions action-name diffcheck
commit dry-run
commit
top
show full-configuration kickers data-kicker a1
EOF

printf "\n\n${PURPLE}##### Commit a change in the profile list\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services properties web-site profile lean lb lb0
commit dry-run
commit | debug kicker
EOF

while ! grep "newValue = lb0" logs/ncs-java-vm.log; do
    printf "\n${RED}Waiting for the n1 notification-kicker to trigger\n${NC}"
    sleep 1;
done

printf "\n\n${PURPLE}##### Check the result of the action by looking into the ncs-java-vm.log log file\n${NC}"
cat logs/ncs-java-vm.log

printf "\n${GREEN}#### Notification kicker demo\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Create a notification-kicker\n${NC}"
ncs_cli -n -u admin -C << EOF
unhide debug
config
kickers notification-kicker n1 selector-expr "\$SUBSCRIPTION_NAME = 'mysub'" kick-node /services/wse:actions action-name diffcheck
commit dry-run
commit
top
show full-configuration kickers notification-kicker n1
EOF

printf "\n\n${PURPLE}##### Create the mysub subscription for device www0\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device www0 notifications subscription mysub local-user admin stream interface
commit dry-run
commit
EOF

while ! grep "newValue = 4668" logs/ncs-java-vm.log; do
    printf "\n${RED}Waiting for the n1 notification-kicker to trigger\n${NC}"
    sleep 1;
done

printf "\n\n${PURPLE}##### Check for notifications that have been received in the ncs-java-vm.log log file\n${NC}"
cat logs/ncs-java-vm.log

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Removing the kicker and the subscription\n${NC}"
ncs_cli -n -u admin -C << EOF
unhide debug
config
no kickers notification-kicker
no devices device www0 notifications subscription
commit dry-run
commit
EOF

if [ -z "$NONINTERACTIVE" ]; then
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make stop clean
fi
printf "\n${GREEN}##### Done!\n${NC}"
