#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Service progress monitoring demo\n${NC}"
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

printf "\n\n${PURPLE}##### Create a service instance\n${NC}"
ncs_cli -n -u admin -C << EOF
config
myserv m1 dummy 1.1.1.1
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### The Python myserv package creates a plan for the services\n${NC}"
ncs_cli -n -u admin -C << EOF
show myserv m1 plan | nomore
EOF

printf "\n\n${PURPLE}##### Load the policy and the trigger\n${NC}"
ncs_cli -n -u admin -C << EOF
config
load merge self_policy_plus_action.xml
load merge self_trigger.xml
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### The SPM will be fulfilled when the self ready state has been reached\n${NC}"
ncs_cli -n -u admin -C << EOF
show myserv m1 service-progress-monitoring | nomore
EOF

printf "\n\n${PURPLE}##### Do some initializaton\n${NC}"
ncs_cli -n -u admin -C << EOF
myserv m1 self-test syslog true
myserv m1 self-test ntp true
myserv m1 self-test dns true
EOF

STATUS=""
REACHED="reached"
while [[ $STATUS != *$REACHED* ]]; do
    printf "${RED}Waiting for router ready reached status\n${NC}"
    sleep 1
    STATUS=$(echo "show myserv m1 plan component router state ready status | nomore" | ncs_cli -u admin -C)
done

ncs_cli -n -u admin -C << EOF
show myserv m1 plan | nomore
config
myserv m1 router2ready true
commit dry-run
commit
EOF

STATUS=""
REACHED="reached"
while [[ $STATUS != *$REACHED* ]]; do
    printf "${RED}Waiting for router2 ready reached status\n${NC}"
    sleep 1
    STATUS=$(echo "show myserv m1 plan component router2 state ready status | nomore" | ncs_cli -u admin -C)
done

ncs_cli -n -u admin -C << EOF
show myserv m1 plan | nomore
show myserv m1 service-progress-monitoring | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
