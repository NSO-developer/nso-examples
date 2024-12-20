#!/usr/bin/env bash
if [ -n "${NCS_IPC_PATH}" ]; then
ENV="NCS_IPC_PATH=${NCS_IPC_PATH}."
else
ENV="NCS_IPC_PORT="
fi

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### External high availability framework demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
env ${ENV}5757 sname=n1 ncs --stop &> /dev/null
env ${ENV}5758 sname=n2 ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the examples\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start the NSO nodes\n${NC}"
env ${ENV}5757 sname=n1 NCS_HA_NODE=n1 ncs --cd ./n1 -c $(pwd)/n1/ncs.conf
env ${ENV}5758 sname=n2 NCS_HA_NODE=n2 ncs --cd ./n2 -c $(pwd)/n2/ncs.conf

printf "${PURPLE}##### Get the status of the n1 and n2 nodes using the NSO CLI\n${NC}"
echo "ha-config status" | env ${ENV}5757 ncs_cli -n -u admin -C
echo "ha-config status" | env ${ENV}5758 ncs_cli -n -u admin -C

printf "\n\n${PURPLE}##### Have the n1 node be primary\n${NC}"
env ${ENV}5757 ncs_cli -n -u admin -C << EOF
ha-config be-primary
EOF

printf "\n\n${PURPLE}##### Have the n2 node be secondary\n${NC}"
env ${ENV}5758 ncs_cli -n -u admin -C << EOF
ha-config be-secondary
EOF

printf "\n\n${PURPLE}##### Get the status of the n1 and n2 nodes using the NSO CLI\n${NC}"
echo "ha-config status" | env ${ENV}5757 ncs_cli -n -u admin -C
echo "ha-config status" | env ${ENV}5758 ncs_cli -n -u admin -C

printf "\n\n${PURPLE}##### Get the status of the n1 and n2 nodes using the ncs_cmd tool\n${NC}"
env ${ENV}5757 ncs_cmd -c "ha_status"
env ${ENV}5758 ncs_cmd -c "ha_status"

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "\n${PURPLE}##### Stop the NSO nodes and the netsim devices\n${NC}"
    env ${ENV}5757 sname=n1 ncs --stop
    env ${ENV}5758 sname=n2 ncs --stop
    ncs-netsim stop

    printf "\n${PURPLE}##### Reset the example to its original files\n${NC}"
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
