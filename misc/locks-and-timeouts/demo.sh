#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Timeout and locks demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make reset
set -e
make clean

printf "${PURPLE}##### Build the packages and start NSO\n${NC}"
make all
ncs

printf "\n${GREEN}##### Timeouts\n${NC}"
printf "\n${GREEN}##### Action and Data provider timeouts\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Set the action and query timeout to 4s in ncs.conf\n${NC}"
sed 's/PT4000S/PT4S/g' ncs.conf > ncs.conf.tmp && mv ncs.conf.tmp ncs.conf
ncs --reload

printf "\n${PURPLE}##### Trigger the action timeout using the CLI\n${NC}"
ncs_cli -n -u admin -C << EOF
test-action sleep secs 6
EOF

printf "\n\n${PURPLE}##### Trigger the query timeout using MAAPI\n${NC}"
set +e
ncs_cmd -dd -c 'mget /test-stats/item{k1}/i'
set -e

#printf "\n\n${PURPLE}##### Trigger the query timeout using the CLI\n${NC}"
#ncs_cli -n -u admin -C << EOF
#show test-stats item k1
#EOF

printf "\n\n${PURPLE}##### Check the ncs.log\n${NC}"
cat ./logs/ncs.log | grep "CRIT"

while [ $(cat ./logs/ncs-java-vm.log | grep REDEPLOY | grep "dp" | grep DONE | wc -l) -lt 1 ]; do
    printf "${RED}##### Wait for the Java VM to restart after the query timeout\n${NC}"
    sleep 1
done
printf "\n\n${PURPLE}##### Check the devel.log\n${NC}"
cat ./logs/devel.log | grep "ERR"

printf "\n${PURPLE}##### Check the ncs-java-vm.log\n${NC}"
cat ./logs/ncs-java-vm.log

printf "\n${GREEN}##### FASTMAP service create() timeout\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Set the service callback timeout to four seconds\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services global-settings service-callback-timeout 4
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Trigger the service callback timeout using the CLI and slow service create() code\n${NC}"
ncs_cli -n -u admin -C << EOF
config
slowsrv s1 sleep-secs 6
commit
EOF

while [ $(cat ./logs/ncs-java-vm.log | grep "REDEPLOY" | grep "slowsrv" | grep "DONE" | wc -l) -lt 1 ]; do
    printf "${RED}##### Wait for the Java VM to restart after the query timeout\n${NC}"
    sleep 1
done

printf "\n${PURPLE}##### The Java log shows similar printouts as in the data provider timeout section above\n${NC}"
cat ./logs/ncs-java-vm.log

printf "\n\n${PURPLE}##### The devel.log\n${NC}"
cat ./logs/devel.log | grep "ERR"

printf "\n\n${PURPLE}##### And the ncs.log\n${NC}"
cat ./logs/ncs.log | grep "CRIT"

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and clean all created files\n${NC}"
    ncs --stop
    make reset clean
fi
sed 's/PT4S/PT4000S/g' ncs.conf > ncs.conf.tmp && mv ncs.conf.tmp ncs.conf

printf "\n${GREEN}##### Done!\n${NC}"
