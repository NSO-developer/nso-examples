#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### NSO SNMP agent demo\n${NC}"
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make -C ../../service-management/website-service stop
set -e
make -C ../../service-management/website-service clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Setup the website-server example simulated network and build the packages\n${NC}"
make -C ../../service-management/website-service all

printf "\n${PURPLE}##### Start the website-server example netsim network and NSO\n${NC}"
make -C ../../service-management/website-service start

printf "\n${PURPLE}##### Set environment for Net-SNMP\n${NC}"
export MIBDIRS=$NCS_DIR/src/ncs/snmp/mibs
export MIBS=TAILF-TOP-MIB:TAILF-ALARM-MIB

printf "\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Show the SNMP configuration\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config snmp agent | nomore
EOF

printf "\n\n${PURPLE}##### Check the USM and VACM settings\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config snmp usm | nomore
show running-config snmp vacm | nomore
EOF

printf "\n\n${PURPLE}##### Verify viewing the alarm list\n${NC}"
if hash snmpwalk 2> /dev/null; then
    snmpwalk -c public -v2c localhost:4000 enterprises tfAlarmTable
else
    printf "${RED}##### No 'snmpwalk' command, skip\n${NC}"
fi

printf "\n${PURPLE}##### Start a trap receiver to listen for NSO alarms\n${NC}"
if hash snmptrapd 2> /dev/null; then
    snmptrapd --disableAuthorization=yes -Lf traplog.txt -p trapd.txt localhost:27777
else
    printf "${RED}##### No 'snmptrapd' command, skip\n${NC}"
fi
ncs_cli -n -u admin -C << EOF
config
snmp target monitor udp-port 27777
commit
EOF

printf "\n${GREEN}##### Generate some alarms\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Stop some devices and then ask NSO to connect to them\n${NC}"
ncs-netsim --dir ../../service-management/website-service/netsim stop lb0
ncs-netsim --dir ../../service-management/website-service/netsim stop www0
ncs_cli -n -u admin -C << EOF
devices connect
EOF

printf "\n\n${PURPLE}##### Show the alarm list\n${NC}"
ncs_cli -n -u admin -C << EOF
show alarms | nomore
EOF

printf "\n\n${PURPLE}##### Get the alarm list in NSO over SNMP\n${NC}"
if hash snmpwalk 2> /dev/null; then
    snmpwalk -c public -v2c localhost:4000 enterprises tfAlarmTable
else
    printf "${RED}##### No 'snmpwalk' command, skip\n${NC}"
fi

printf "\n${PURPLE}##### Using SNMPv3\n${NC}"
if hash snmpwalk 2> /dev/null; then
    snmpwalk -v3 -u initial -l noAuthNoPriv localhost:4000 enterprises tfAlarmTable
    snmpwalk -v3 -u initial -l authNoPriv -a sha -A GoTellMom localhost:4000 enterprises tfAlarmTable
    snmpwalk -v3 -u initial -l authPriv -a sha -A GoTellMom -x aes -X GoTellMom localhost:4000 enterprises tfAlarmTable
else
    printf "${RED}##### No 'snmpwalk' command, skip\n${NC}"
fi

printf "\n${PURPLE}##### Start the devices and connect to them to clear the alarms\n${NC}"
ncs-netsim --dir ../../service-management/website-service/netsim start lb0
ncs-netsim --dir ../../service-management/website-service/netsim start www0
ncs_cli -n -u admin -C << EOF
devices connect
EOF
echo ""
if hash snmpwalk 2> /dev/null; then
    snmpwalk -c public -v2c localhost:4000 enterprises tfAlarmTable
else
    printf "${RED}##### No 'snmpwalk' command, skip\n${NC}"
fi

printf "\n${GREEN}##### Notifications (traps)\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Show SNMP notification settings\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config snmp notify | nomore
show running-config snmp target | nomore
EOF

printf "\n\n${PURPLE}##### View the contents of the trap recevier log\n${NC}"
cat traplog.txt

printf "\n${GREEN}##### Changing probable cause mapping\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Change the mapping of connection-failure alarm type\n${NC}"
ncs_cli -n -u admin -C << EOF
config
alarms alarm-model alarm-type connection-failure probable-cause 22
commit
EOF

printf "\n\n${PURPLE}##### Walk the alarm list to verify probable case is now 22\n${NC}"
if hash snmpwalk 2> /dev/null; then
    snmpwalk -c public -v2c localhost:4000 enterprises tfAlarmTable
else
    printf "${RED}##### No 'snmpwalk' command, skip\n${NC}"
fi

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    set +e
    make -C ../../service-management/website-service stop
    set -e
    make -C ../../service-management/website-service clean
    if hash snmptrapd 2> /dev/null; then
        kill $(cat ./trapd.txt)
    fi
    rm -f trapd.txt traplog.txt
fi

printf "\n${GREEN}##### Done!\n${NC}"
