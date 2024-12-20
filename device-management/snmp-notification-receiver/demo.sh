#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### SNMP Notification Receiver demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the example\n${NC}"
make all

printf "\n${PURPLE}##### Start NSO\n${NC}"
make start

printf "\n${PURPLE}##### Check the current alarms\n${NC}"
ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
EOF

printf "\n\n${PURPLE}##### Show the SNMP notification receiver configuration\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config snmp-notification-receiver | nomore
EOF

printf "\n\n${PURPLE}##### Simulate that a managed device sends an SNMP notification\n${NC}"
./sendnotif.sh 127.0.0.1 8000 1

printf "\n${PURPLE}##### Check the alarm list from the NSO CLI\n${NC}"

ALARMS=$(echo "show alarms alarm-list number-of-alarms | nomore" | ncs_cli -u admin -C)
ONE_ALARM="alarms alarm-list number-of-alarms 1"
while [[ $ALARMS != *$ONE_ALARM* ]]; do
    printf "${RED}Waiting for NSO to receive the alarm notification\n${NC}"
    sleep 1
    ALARMS=$(echo "show alarms alarm-list number-of-alarms | nomore" | ncs_cli -u admin -C)
done

ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
EOF

printf "\n${PURPLE}##### Examples using the Net-SNMP snmptrap application to send notifications\n${NC}"
export SNMP_PERSISTENT_FILE=/dev/null
if hash snmptrap 2> /dev/null; then
    printf "\n${PURPLE}##### Send a v1 trap\n${NC}"
    snmptrap -v1 -c foo 127.0.0.1:8000 1.3.6.1.4.1.3.1.1 10.0.0.1 1 1 100
    printf "\n${PURPLE}##### Send a v2c notification\n${NC}"
    snmptrap -v2c -c foo 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1
    printf "\n${PURPLE}##### Send an authPriv v3 notification\n${NC}"
    snmptrap -v3 -u ncs -l authPriv -a SHA -A authpass -x aes -X privpass 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1
else
    printf "${RED}##### No 'snmptrap' command available, skip\n${NC}"
fi

if hash snmpinform 2> /dev/null; then
    printf "\n${PURPLE}##### Send a v2c inform\n${NC}"
    snmpinform -v2c -c foo 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1
    printf "\n${PURPLE}##### Send an authPriv v3 inform\n${NC}"
    snmpinform -v3 -u ncs -l authPriv -a SHA -A authpass -x aes -X privpass 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1
else
    printf "${RED}##### No 'snmpinform' command available, skip\n${NC}"
fi

NUM=$(echo "show alarms alarm-list | nomore" | ncs_cli -u admin -C | grep -o "test alarm" | wc -l)
while [ $NUM -ne 6 ]; do
    printf "${RED}Waiting for status changes\n${NC}"
    sleep 1
    NUM=$(echo "show alarms alarm-list | nomore" | ncs_cli -u admin -C | grep -o "test alarm" | wc -l)
done

printf "\n${PURPLE}##### Check the alarm list from the NSO CLI\n${NC}"
ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
