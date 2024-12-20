#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### NSO SNMP agent demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the example\n${NC}"
make all

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### Show the SNMP configuration\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config snmp | nomore
EOF

printf "\n\n${PURPLE}##### Show the configuration initialized from the ncs-cdb/simple_init.xml file\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config simpleObjects | nomore
EOF

printf "\n\n${PURPLE}##### If available, use Net-SMMP commands towards the NSO SNMP agent\n${NC}"
export MIBS=$(pwd)/packages/snmp-mib/src/TAIL-F-TEST-MIB.mib

printf "${PURPLE}##### SNMP walk\n${NC}"
if hash snmpwalk 2> /dev/null; then
    snmpwalk -c public -v2c localhost:4000 enterprises
else
    printf "${RED}##### No 'snmpwalk' command, skip\n${NC}"
fi

printf "\n${PURPLE}##### SNMP get\n${NC}"
if hash snmpget 2> /dev/null; then
    snmpget -c public -v2c localhost:4000 enterprises TAIL-F-TEST-MIB::maxNumberOfServers.0
else
    printf "${RED}##### No 'snmpget' command, skip\n${NC}"
fi

printf "\n${PURPLE}##### SNMP set\n${NC}"
printf "${PURPLE}##### Add a write view for the public community using the ncs_cmd tool\n${NC}"
ncs_cmd -dd -c 'mset "/snmp/vacm/group{public}/access{any no-auth-no-priv}/write-view" internet'

if hash snmpset 2> /dev/null; then
    snmpset -c public -v2c localhost:4000 TAIL-F-TEST-MIB::maxNumberOfServers.0 i 43
else
    printf "${RED}##### No 'snmpset' command, skip\n${NC}"
fi

printf "\n${PURPLE}##### SNMP table\n${NC}"
if hash snmptable 2> /dev/null; then
    snmptable -Ci -c public -v2c localhost:4000 TAIL-F-TEST-MIB::hostTable
else
    printf "${RED}##### No 'snmptable' command, skip\n${NC}"
fi

printf "\n${PURPLE}##### SNMP getnext\n${NC}"
if hash snmpgetnext 2> /dev/null; then
    snmpgetnext -c public -v2c localhost:4000 TAIL-F-TEST-MIB::hostEnabled.\"kalle\"
    snmpgetnext -c public -v2c localhost:4000 TAIL-F-TEST-MIB::hostEnabled.\"vega@tail-f.com\"
else
    printf "${RED}##### No 'snmpgetnext' command, skip\n${NC}"
fi

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    ncs --stop
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
