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

printf "\n${GREEN}##### Layered Services Architecture Single NSO Version Deployment Demo\n${NC}"
printf "${GREEN}##### Running the Example\n${NC}"
printf "${GREEN}##### Manual Setup\n${NC}"
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop
set -e
make clean
printf "\n${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make manual

printf "\n${PURPLE}##### Start the simulated network and NSO\n${NC}"
make start-manual

printf "\n${PURPLE}##### Configure the nodes in the cluster from the CFS NSO node\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cluster device-notifications enabled
cluster remote-node lower-nso-1 authgroup default username admin
cluster remote-node lower-nso-1 address 127.0.0.1 port 2023
cluster remote-node lower-nso-2 authgroup default username admin
cluster remote-node lower-nso-2 address 127.0.0.1 port 2024
cluster commit-queue enabled
commit dry-run
commit
cluster remote-node lower-nso-* ssh fetch-host-keys
EOF

printf "\n\n${PURPLE}##### Configure the two lower RFS nodes as LSA devices from the CFS NSO node\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
ncs:devices device lower-nso-1 device-type netconf ned-id lsa-netconf
ncs:devices device lower-nso-1 authgroup default
ncs:devices device lower-nso-1 lsa-remote-node lower-nso-1
ncs:devices device lower-nso-1 state admin-state unlocked
ncs:devices device lower-nso-2 device-type netconf ned-id lsa-netconf
ncs:devices device lower-nso-2 authgroup default
ncs:devices device lower-nso-2 lsa-remote-node lower-nso-2
ncs:devices device lower-nso-2 state admin-state unlocked
commit dry-run
commit
ncs:devices fetch-ssh-host-keys
ncs:devices sync-from
EOF

printf "\n\n${PURPLE}##### Create a LSA NETONF NED package\n${NC}"
ncs-make-package --no-netsim --no-java --no-python --lsa-netconf-ned package-store/rfs-vlan/src/yang  --dest upper-nso/packages/rfs-vlan-ned --build rfs-vlan-ned

printf "\n\n${PURPLE}##### Install the cfs-vlan service\n${NC}"
ln -sf ../../package-store/cfs-vlan upper-nso/packages
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
packages reload
ncs:devices sync-from
EOF

printf "\n\n${PURPLE}##### Set up the dispatch table for the devices on the lower RFS nodes\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cfs-vlan:devices device ex0 lower-node lower-nso-1
cfs-vlan:devices device ex1 lower-node lower-nso-1
cfs-vlan:devices device ex2 lower-node lower-nso-1
cfs-vlan:devices device ex3 lower-node lower-nso-2
cfs-vlan:devices device ex4 lower-node lower-nso-2
cfs-vlan:devices device ex5 lower-node lower-nso-2
commit dry-run
commit
EOF

printf "\n\n${GREEN}##### Verify the CFS-VLAN service\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Configure the cfs-vlan service\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Review the configuration changes\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
cfs-vlan v1 get-modifications
EOF

printf "\n\n${GREEN}##### Makefile Setup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop
set -e
make clean
printf "\n${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the simulated network and NSO\n${NC}"
make start-all

printf "\n${GREEN}##### Verify the CFS-VLAN service\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Configure the cfs-vlan service\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Review the configuration changes\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
cfs-vlan v1 get-modifications
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
