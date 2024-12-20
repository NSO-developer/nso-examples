#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Website service demo\n${NC}"
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

printf "\n\n${PURPLE}##### Create a load balancer profile\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services properties web-site profile gold lb lb0 backend www0
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Create a website\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services web-site acme port 8080 ip 168.192.0.1 lb-profile gold url www.vip.org
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the lbConfig and wsConfig of the managed devices\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device www0..2 config ws:wsConfig | nomore
show running-config devices device lb0 config lb:lbConfig | nomore
EOF

printf "\n\n${PURPLE}##### Make a rouge reconfiguration on the www0 device\n${NC}"
ncs-netsim cli www0 << EOF
configure
delete wsConfig
commit
EOF

printf "\n\n${PURPLE}##### Do a check-sync from the NSO CLI\n${NC}"
ncs_cli -n -u admin -C << EOF
devices check-sync
devices device www0 compare-config
EOF

printf "\n\n${PURPLE}##### Sync from the out-of-sync device\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device www0 sync-from
EOF

printf "\n\n${PURPLE}##### Re-deploy the out-of-sync service\n${NC}"
ncs_cli -n -u admin -C << EOF
services web-site acme check-sync
services web-site acme check-sync outformat cli
services web-site acme re-deploy
services web-site acme check-sync
EOF

printf "\n\n${PURPLE}##### Verify that the wsConfig for www0 has been updated\n${NC}"
ncs-netsim cli www0 << EOF
show configuration wsConfig
EOF

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
