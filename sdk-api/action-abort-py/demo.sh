#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Python abort action demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the package and start NSO\n${NC}"
make all start
ncs_cmd -u admin -c "maction /ncs:devices/sync-from"

printf "\n${PURPLE}##### Run the 'iosxr-config' action completing it before a timeout\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
action-abort-test iosxr-config timeout 10 device xr1 work-duration 5
show running-config devices device xr1 config interface Loopback
EOF

printf "\n\n${PURPLE}##### Run the 'iosxr-config' action with an abort due to timeout\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
action-abort-test iosxr-config timeout 2 device xr1 work-duration 10
show running-config devices device xr1 config interface Loopback
EOF

printf "\n\n${PURPLE}##### Again abort the 'iosxr-config' action due to a timeout\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
action-abort-test iosxr-config timeout 5 device xr1 work-duration 20
show running-config devices device xr1 config interface Loopback
EOF

printf "\n\n${PURPLE}##### Again complete the 'iosxr-config' action before a timeout\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
action-abort-test iosxr-config timeout 6 device xr1 work-duration 3
show running-config devices device xr1 config interface Loopback
EOF

printf "\n\n${PURPLE}##### View the log output in ncs-python-vm-actions.log\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
cat logs/ncs-python-vm-abort-action.log

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
