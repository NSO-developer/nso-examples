#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### NETCONF SSH call home demo\n${NC}"
printf "${PURPLE}##### Start clean\n${NC}"
set +e
ncs --stop
ncs-netsim stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the simulated network and the NSO nodes\n${NC}"
ncs-netsim start
ncs

printf "\n\n${PURPLE}##### Sync the configuration from the ex0 device\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Enable call home for the ex0 device\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 state admin-state call-home
devices device ex0 local-user admin
devices device ex0 ssh host-key-verification reject-mismatch
devices device ex0 ssh host-key ssh-ed25519 key-data "$(cat netsim/ex/ex0/ssh/ssh_host_ed25519_key.pub)"
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Device ex0 has not yet established a call home connection\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Start a Python app that subsribes to the NCS_NOTIF_CALL_HOME_INFO event from NSO\n${NC}"
python3 notif.py -C </dev/null &
echo $! > notif-app.pid

printf "\n\n${PURPLE}##### Configure a device and commit through the commit queue\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 config r:sys syslog server 1.2.3.4
commit commit-queue async | details verbose
EOF

printf "\n\n${PURPLE}##### Have the ex0 device call home\n${NC}"
CONFD_IPC_PORT=5010 confd_cmd -dd -c "netconf_ssh_call_home 127.0.0.1 4334"

while : ; do
  EXPECT="1.2.3.4"
  SERVER=$(echo "show running-config r:sys syslog server | include 1.2.3.4 | nomore" | CONFD_IPC_PORT=5010 confd_cli -u admin -C)
  if [[ $SERVER == *$EXPECT* ]]; then
      printf "\n${GREEN}##### Device ex0 called home and was configured by NSO\n${NC}"
      break
  fi
  printf "${RED}##### Waiting for ex0 to call home and be configured by NSO...\n${NC}"
  sleep 1
done

printf "\n${PURPLE}##### Show ex0 syslog server config\n${NC}"
CONFD_IPC_PORT=5010 confd_cli -n -u admin -C << EOF
show running-config r:sys syslog server
EOF

printf "\n\n${PURPLE}##### Have the, to NSO, unknown ex1 device call home\n${NC}"
set +e
CONFD_IPC_PORT=5011 confd_cmd -dd -c "netconf_ssh_call_home 127.0.0.1 4334"
set -e

printf "\n\n${PURPLE}##### Check the NETCONF log for call home activity\n${NC}"
cat logs/netconf.log | grep "call home"

kill $(cat notif-app.pid)

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    ncs --stop
    ncs-netsim stop
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
