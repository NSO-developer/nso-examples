#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}
ALARM_WAIT_TIMEOUT=${ALARM_WAIT_TIMEOUT-30}
STATUS_WAIT_TIMEOUT=${STATUS_WAIT_TIMEOUT-30}

pause() {
    prompt="${1-}"
    if [ -z "$prompt" ]; then
        prompt="${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    fi
    if [ -z "$NONINTERACTIVE" ]; then
        printf "%b" "$prompt"
        read -n 1 -s -r
    fi
}

dump_notification_debug() {
    printf "\n${RED}##### Notification receiver debug information\n${NC}"
    ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
show running-config snmp-notification-receiver | nomore
EOF
}


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
EXPECTED_LINKDOWN=0
if ./sendnotif.sh 127.0.0.1 8000 1; then
    EXPECTED_LINKDOWN=1
else
    printf "${RED}##### Unable to send the initial SNMPv3 authPriv notification, skip alarm verification\n${NC}"
fi

printf "\n${PURPLE}##### Check the alarm list from the NSO CLI\n${NC}"

if [ "$EXPECTED_LINKDOWN" -gt 0 ]; then
    ALARMS=$(echo "show alarms alarm-list | nomore" | ncs_cli -u admin -C)
    RETRIES=0
    while [[ $ALARMS != *IF-MIB::linkDown* ]]; do
        if [ "$RETRIES" -ge "$ALARM_WAIT_TIMEOUT" ]; then
            printf "${RED}##### Timed out waiting for NSO to receive IF-MIB::linkDown\n${NC}"
            dump_notification_debug
            exit 1
        fi
        printf "${RED}Waiting for NSO to receive the alarm notification\n${NC}"
        sleep 1
        RETRIES=$((RETRIES + 1))
        if [ $((RETRIES % 5)) -eq 0 ]; then
            ./sendnotif.sh 127.0.0.1 8000 1 >/dev/null 2>&1 || true
        fi
        ALARMS=$(echo "show alarms alarm-list | nomore" | ncs_cli -u admin -C)
    done

    ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
EOF
fi

printf "\n${PURPLE}##### Examples using the Net-SNMP snmptrap application to send notifications\n${NC}"
export SNMP_PERSISTENT_FILE=/dev/null
EXPECTED_TEST_ALARMS=0
if hash snmptrap 2> /dev/null; then
    printf "\n${PURPLE}##### Send an authPriv v3 notification\n${NC}"
    snmptrap -v3 -u ncs -l authPriv -a SHA -A authpass -x AES -X privpass 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1
    EXPECTED_TEST_ALARMS=$((EXPECTED_TEST_ALARMS + 1))
else
    printf "${RED}##### No 'snmptrap' command available, skip\n${NC}"
fi

if hash snmpinform 2> /dev/null; then
    printf "\n${PURPLE}##### Send an authPriv v3 inform\n${NC}"
    snmpinform -v3 -u ncs -l authPriv -a SHA -A authpass -x AES -X privpass 127.0.0.1:8000 100 1.3.6.1.4.1.3.1.1
    EXPECTED_TEST_ALARMS=$((EXPECTED_TEST_ALARMS + 1))
else
    printf "${RED}##### No 'snmpinform' command available, skip\n${NC}"
fi

if [ "$EXPECTED_TEST_ALARMS" -gt 0 ]; then
    NUM=$(echo "show alarms alarm-list | nomore" | ncs_cli -u admin -C | awk '/test alarm/ {c++} END {print c+0}')
    RETRIES=0
    while [ "$NUM" -lt "$EXPECTED_TEST_ALARMS" ]; do
        if [ "$RETRIES" -ge "$STATUS_WAIT_TIMEOUT" ]; then
            printf "${RED}##### Timed out waiting for SNMP notification status changes\n${NC}"
            dump_notification_debug
            exit 1
        fi
        printf "${RED}Waiting for status changes\n${NC}"
        sleep 1
        RETRIES=$((RETRIES + 1))
        NUM=$(echo "show alarms alarm-list | nomore" | ncs_cli -u admin -C | awk '/test alarm/ {c++} END {print c+0}')
    done
fi

printf "\n${PURPLE}##### Check the alarm list from the NSO CLI\n${NC}"
ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    pause
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
