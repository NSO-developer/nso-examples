#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Router network demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "${GREEN}##### Starting the Simulated Network\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### To start the simulated devices, build them using 'make all'\n${NC}"
make all

printf "\n${PURPLE}##### To start the simulated network, use the 'ncs-netsim start' command\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### List the devices in the simulated network using 'ncs-netsim list'\n${NC}"
ncs-netsim list

printf "\n${PURPLE}##### Show additional commands by issuing 'ncs-netsim help'\n${NC}"
ncs-netsim help

printf "${GREEN}##### Starting NSO\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Start the NSO server with the default configuration\n${NC}"
ncs

printf "${PURPLE}##### Connect to the NSO Command Line Interface (CLI) and show the NSO version\n${NC}"
ncs_cli -n -u admin -C << EOF
show ncs-state version | nomore
EOF

printf "\n\n${PURPLE}##### Use the 'ssh' command to the NSO CLI like an external user using public key authentication\n${NC}"
if hash ssh 2> /dev/null && hash ssh-keygen 2> /dev/null; then
    printf "\n${PURPLE}##### Create SSH client keys\n${NC}"
    mkdir -p ssh-keydir/admin/.ssh
    ssh-keygen -N "" -t ed25519 -m pem -f ssh-keydir/id_ed25519

    cp ssh-keydir/id_ed25519.pub ssh-keydir/admin/.ssh/authorized_keys

    printf "${PURPLE}##### Create a known_hosts key file for the SSH client\n${NC}"
    HOST_KEY=$(cat ${NCS_DIR}/etc/ncs/ssh/ssh_host_ed25519_key.pub | cut -d ' ' -f1-2)
    echo "[localhost]:2024 $HOST_KEY" >> ssh-keydir/known_hosts
    cat ssh-keydir/known_hosts
    ssh-keygen -Hf ssh-keydir/known_hosts

    printf "\n${PURPLE}##### Configure NSO with where the NSO SSH server 'ssh_keydir' with the authorized_keys for the admin user is located\n${NC}"
    ncs_cli -n -u admin -C << EOF
config
aaa authentication users user admin ssh_keydir $(pwd)/ssh-keydir/admin/.ssh
commit
EOF

    printf "\n\n${PURPLE}##### Connect to the NSO CLI through the NSO SSH server using the SSH command and verify the NSO version status\n${NC}"
    ssh -i ssh-keydir/id_ed25519 -o UserKnownHostsFile=$(pwd)/ssh-keydir/known_hosts -l admin -p 2024 localhost "show ncs-state version | nomore"
else
    printf "${RED}##### No 'ssh' and/or 'ssh-keygen' command, skip connecting to the NSO SSH server\n${NC}"
fi

printf "\n${PURPLE}##### Enable the trace output of what NSO sends to the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices global-settings trace pretty
commit
EOF

printf "\n\n${GREEN}##### Managing Devices Using NSO\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### List the devices NSO is configured to manage\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices device | display-level 1 | nomore
EOF

printf "\n\n${GREEN}##### The NSO Device Manager\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Connect to the devices and read their configurations into the NSO database\n${NC}"
ncs_cli -n -u admin -C << EOF
devices connect
devices sync-from
EOF

printf "\n\n${PURPLE}##### Show the ex0 device configuration\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device ex0 config | nomore
EOF

printf "\n\n${PURPLE}##### Show the sys 'routes inet' configuration from several devices\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device ex0..2 config r:sys routes inet | nomore
EOF

printf "\n\n${PURPLE}##### Change a particular piece of configuration across multiple devices\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0..2 config r:sys routes inet route 10.2.0.0 24
next-hop 10.2.0.254 metric 20
commit
EOF

printf "\n\n${PURPLE}##### Show the result\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device ex* config r:sys routes inet | nomore
EOF

printf "\n\n${PURPLE}##### Print the 'logs/netconf-ex0.trace' NSO device communication trace log file enabled for all devices earlier\n${NC}"
cat logs/netconf-ex0.trace

printf "${GREEN}##### What if a Device is Down?\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Shut down the ex1 simulated device${NC}"
ncs-netsim stop ex1

printf "${PURPLE}##### Check which simulated devices are up${NC}"
ncs-netsim is-alive

printf "\n${PURPLE}##### Make another device configuration change${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 config r:sys ntp key 3
commit
EOF

printf "\n\n${RED}##### Commit a second change that involve the device 'ex1', which is down${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0..2 config r:sys ntp key 3
commit
EOF

printf "\n\n${PURPLE}##### Let's try the commit-queue alternative${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0..2 config r:sys ntp key 3
commit commit-queue async
EOF

printf "\n\n${PURPLE}##### View the commit queue${NC}"
ncs_cli -n -u admin -C << EOF
show devices commit-queue | nomore
EOF

printf "\n\n${PURPLE}##### Start the ex1 simulated device${NC}"
ncs-netsim start ex1

while [[ "$(echo 'show devices device ex1 commit-queue queue-length' | ncs_cli -u admin -C)" != *"commit-queue queue-length 0"* ]] ; do
  printf "${RED}#### Waiting for the commit queue length to become zero...\n${NC}"
  sleep 1
done

printf "\n${PURPLE}##### View the commit queue${NC}"
ncs_cli -n -u admin -C << EOF
show devices commit-queue | nomore
EOF

printf "\n\n${GREEN}##### Syncing\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Verify that all devices are in sync with NSO${NC}"
ncs_cli -n -u admin -C << EOF
devices check-sync
EOF

printf "\n\n${PURPLE}##### Introduce a configuration mismatch by changing the configuration on the ex1 device${NC}"
ncs-netsim cli-c ex1 << EOF
config
no sys routes
commit
EOF

printf "${PURPLE}##### Check if the devices are in sync from NSO${NC}"
ncs_cli -n -u admin -C << EOF
devices check-sync
EOF

printf "\n\n${PURPLE}##### Get the full details of what is out-of-sync${NC}"
ncs_cli -n -u admin -C << EOF
devices device ex1 compare-config
EOF

printf "\n\n${PURPLE}##### Confirm the sync to the device${NC}"
ncs_cli -n -u admin -C << EOF
devices device ex1 sync-to
EOF

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and clean all created files\n${NC}"
    ncs-netsim stop
    ncs --stop
    make clean
fi

printf "\n\n${GREEN}##### Done!\n${NC}"
