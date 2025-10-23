#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Manage SNMP Devices with NSO demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the example\n${NC}"
make all

printf "\n${PURPLE}##### Start the simulated network and NSO\n${NC}"
make start

printf "\n${PURPLE}##### NSO is configured to communicate with the three devices\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device r0..2 | nomore
EOF

printf "\n\n${PURPLE}##### Show the authentication groups v1/v2c and v3c authentication parameters\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices authgroups snmp-group default-map | nomore
show running-config devices authgroups snmp-group umap | nomore
EOF

printf "\n\n${PURPLE}##### Get the config from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
show running-config devices device | nomore
EOF

printf "\n\n${PURPLE}##### Find out which devices implement the SNMPv2-MIB data model\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices device module SNMPv2-MIB | nomore
EOF

printf "\n\n${PURPLE}##### Configure device r1\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device r1 config SNMPv2-MIB system sysContact wallan
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### If available, use the Net-SNMP 'snmpget' command towards the device to verify the new value\n${NC}"
if hash snmpget 2> /dev/null; then
    snmpget -v2c -c public 127.0.0.1:11023 sysContact.0
else
    printf "${RED}##### No 'snmpget' command, skip\n${NC}"
fi

printf "\n\n${PURPLE}##### Set the 'sysContact' on the 'r2' device too\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device r2 config SNMPv2-MIB system sysContact wallan
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Show the rollback files\n${NC}"
cat logs/rollback*

printf "\n${PURPLE}##### Undo the 'sysContact' change\n${NC}"
ncs_cli -n -u admin -C << EOF
config
rollback-files apply-rollback-file fixed-number 10005
commit dry-run
commit
EOF

printf "\n${PURPLE}##### set the 'sysContact' over SNMP using a Net-SNMP command\n${NC}"
if hash snmpset 2> /dev/null; then
    snmpset -v2c -c public 127.0.0.1:11023 sysContact.0 s john
else
    printf "${RED}##### No 'snmpset' command, skip\n${NC}"
fi

printf "\n\n${PURPLE}##### Compare NSO config with the device configurationl\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device r1 compare-config | nomore
EOF

printf "\n\n${PURPLE}##### Sync the NSO config to the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-to
EOF

printf "\n\n${PURPLE}##### Create a new row in the 'bscActTable' without committing\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device r0 config basic-config:BASIC-CONFIG-MIB bscActTable bscActEntry 17 bscActOwner wallan bscActFlow 42 bscActAdminState unlocked
commit dry-run
revert no-confirm
EOF

printf "\n\n${PURPLE}##### Add config where 'bscAddrTable' expands 'bscBaseTable'\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device r0 config basic-config:BASIC-CONFIG-MIB bscBaseTable bscBaseEntry 17 bscBaseStr seventeen
devices device r0 config basic-config:BASIC-CONFIG-MIB bscAddrTable bscAddrEntry 17 82 bscAddrStr expansion-demo
commit dry-run
commit | details
EOF

printf "\n\n${PURPLE}##### Force an error by setting 'bscBaseErr' to a non-zero value\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device r2 config basic-config:BASIC-CONFIG-MIB bscBaseTable bscBaseEntry 42 bscBaseErr 1
commit
EOF

printf "\n\n${PURPLE}##### Show operational data that is directly fetched from the device\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices device r1 live-status SNMPv2-MIB
EOF

printf "\n\n${PURPLE}##### Retrieve stats from all devices from NSO over NETCONF using the 'netconf-console' tool\n${NC}"
netconf-console --get -x "/devices/device/live-status/SNMPv2-MIB/snmp"

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
