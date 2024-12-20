#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Service pre-modification callback demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"

printf "${PURPLE}##### Build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### The XML init file containing the device configuration\n${NC}"
cat ./ncs-cdb/ncs_init.xml

printf "\n\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Check the initial DNS server configuration for the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device * config r:sys dns | nomore
EOF

printf "\n\n${PURPLE}##### Create a VPN service\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn-endpoints vpn-endpoint s1 router [ ex0 ex1 ex2 ] iface ethX unit 1 vid 2
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Create a second VPN service instance\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn-endpoints vpn-endpoint s2 router [ ex0 ex1 ex2 ] iface ethX unit 2 vid 3
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Confirm the DNS server was created without FASTMAP service meta-data\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device * config r:sys dns | display service-meta-data | nomore
EOF

printf "\n\n${PURPLE}##### Remove both services to restore the original state of the device\n${NC}"
ncs_cli -n -u admin -C << EOF
config
no vpn-endpoints vpn-endpoint *
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Check that the DNS server configuration was not removed by FASTMAP\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device * config r:sys dns | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim stop

    printf "\n${PURPLE}##### Reset the example to its original files\n${NC}"
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
