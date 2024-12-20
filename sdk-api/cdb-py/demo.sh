#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### CDB API Python subscriber demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the two packages\n${NC}"
make all

printf "\n${PURPLE}##### Start NSO, the subsribers, and the netsim network\n${NC}"
make start

printf "\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Trigger the CDB configuration data subscriber\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 config sys syslog server 4.5.6.7 enabled
commit
no devices device ex0 config sys syslog server 4.5.6.7
commit
EOF

printf "\n\n${PURPLE}##### Resulting log entries in logs/ncs-python-vm-cdb.log\n${NC}"
grep -q '4.5.6.7' <(tail -f logs/ncs-python-vm-cdb.log)
cat logs/ncs-python-vm-cdb.log | grep "4.5.6.7"

printf "\n${PURPLE}##### Trigger the CDB operational data subscriber\n${NC}"
ncs_cmd -o -dd -c 'mcreate /test/stats-item{dawnfm}; mset /test/stats-item{dawnfm}/i 42; mset /test/stats-item{dawnfm}/inner/l boogaloo'
ncs_cmd -o -dd -c 'mdel /test/stats-item{dawnfm}'

printf "\n${PURPLE}##### Resulting log entries in logs/ncs-python-vm-cdb.log\n${NC}"
grep -q 'dawnfm' <(tail -f logs/ncs-python-vm-cdb.log)
cat logs/ncs-python-vm-cdb.log | grep "dawnfm"

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    make stop
    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
