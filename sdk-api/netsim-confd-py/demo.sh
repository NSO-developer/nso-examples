#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### NSO ConfD netsim Python SDK application demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim --dir nso-rundir/netsim stop &> /dev/null
set -e
rm -rf nso-rundir

printf "\n${GREEN}##### Setting up and running netsim\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Set up NSO and create a single device simulated network\n${NC}"
ncs-setup --package package-repository/dummy --use-copy --dest nso-rundir
make -C nso-rundir/packages/dummy/src/
ncs-netsim --dir nso-rundir/netsim create-network nso-rundir/packages/dummy 1 d
ncs-netsim --dir nso-rundir/netsim ncs-xml-init d0 > nso-rundir/ncs-cdb/device-init.xml

printf "\n${PURPLE}##### Start NSO and the simulated device\n${NC}"
ncs-netsim --dir nso-rundir/netsim start
ncs --cd ./nso-rundir

printf "\n${PURPLE}##### Sync the configuration from the netsim device to NSO\n${NC}"
ncs_cli -n -u admin -C << EOF
show packages
devices sync-from
EOF

printf "\n\n${GREEN}##### Run a netsim ConfD application Demo\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Change the device configuration to trigger the config subscriber\n${NC}"
ncs_cli -n -u admin -C << EOF
config
device device d0 config dummy name test-device
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Check the netsim device log for the configuration subscriber application being notified of changes\n${NC}"
cat nso-rundir/netsim/d/d0/logs/dummy.log

printf "\n${PURPLE}##### Make operational data changes over MAAPI using the confd_cmd tool setting /dummy:dummy/state to \"new-state\"\n${NC}"
$NCS_DIR/netsim/confd/bin/confd_cmd -o -p $(ncs-netsim --dir nso-rundir/netsim get-port d0 ipc) -c 'mset /dummy:dummy/state "new-state"'

printf "\n${PURPLE}##### Check the netsim device log for the operational data subscriber application being notified of changes\n${NC}"
cat nso-rundir/netsim/d/d0/logs/dummy.log

printf "\n${PURPLE}##### Configure NSO to subscribe to NETCONF notifications for the something_done_notifications stream\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices device d0 notifications stream | nomore
config
devices device d0 notifications subscription something-done-notif stream something_done_notifications local-user admin
commit
EOF

printf "\n\n${PURPLE}##### Call the action on the d0 simulated device using the NSO CLI\n${NC}"
echo "devices device d0 live-status dummy do-something" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}##### Check the netsim device log for the action application being invoked\n${NC}"
cat nso-rundir/netsim/d/d0/logs/dummy.log

printf "\n${PURPLE}##### Show received device something-done notification that was sent from the action application\n${NC}"
echo "show devices device d0 notifications received-notifications | nomore" | ncs_cli -n -u admin -C

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop NSO and the simulated device\n${NC}"
    ncs --stop
    ncs-netsim --dir nso-rundir/netsim stop

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    rm -rf ./nso-rundir
fi

printf "\n${GREEN}##### Done!\n${NC}"