#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Web server basic device manager demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the example\n${NC}"
make all

printf "\n${PURPLE}##### Start the four simulated devices, 'lb0', 'www0', 'www1' and 'www2'\n${NC}"
make start

printf "\n${PURPLE}##### Show the configuration needed to manage the 'lb0' device\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device lb0 | nomore
EOF

printf "\n\n${PURPLE}##### Do a cheap sync check\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device lb0 check-sync
EOF

printf "\n\n${PURPLE}##### Check if the device supports the tailf proprietary monitoring namespace http://tail-f.com/yang/common-monitoring\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices device lb0 capability | nomore
EOF

printf "\n\n${PURPLE}##### Show he string on the 'lb0' device that identifies the last committed transaction\n${NC}"
ncs-netsim cli lb0 << EOF
show status confd-state internal cdb datastore running transaction-id | nomore
EOF

printf "\n\n${PURPLE}##### Check if there is a diff between the 'lb0' device config and NSO\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device lb0 compare-config
EOF

printf "\n\n${PURPLE}##### Same diff in XML format\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device lb0 compare-config outformat xml
EOF

printf "\n\n${PURPLE}##### Sync with the 'lb0' device\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device lb0 sync-from
devices device lb0 check-sync
show running-config devices device lb0 config
EOF

printf "\n\n${PURPLE}##### Sync with the all devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Turn on the 'KeepAlive' feature for all web servers\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device www0..2 config ws:wsConfig global KeepAlive On
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Rollback the previous transaction\n${NC}"
ncs_cli -n -u admin -C << EOF
config
rollback configuration
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Stop the 'lb0' device\n${NC}"
ncs-netsim stop lb0

printf "\n\n${PURPLE}##### Attempt to configure the 'lb0' device and when the commit fails commit through the commit queue\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device lb0 config if:interface eth0 macaddr 00:66:77:22:11:22
devices device lb0 config if:interface eth0 ipv4-address 2.3.4.5
commit dry-run
commit
commit commit-queue async
EOF

printf "\n\n${PURPLE}##### Show the commit queue status\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices commit-queue
EOF

printf "\n\n${PURPLE}##### Restart the 'lb0' device\n${NC}"
ncs-netsim start lb0

CQ_STATUS=""
CQ_EMPTY="No entries found"
while [[ $CQ_STATUS != *$CQ_EMPTY* ]]; do
    printf "${RED}Waiting for NSO to successfully retry and the NSO commit queue to become empty\n${NC}"
    sleep 1
    CQ_STATUS=$(echo "show devices commit-queue | nomore" | ncs_cli -u admin -C)
done
printf "${PURPLE}##### Configuration committed to the device\n${NC}"

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
