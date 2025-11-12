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

printf "\n${GREEN}##### Layered Services Architecture Multi NSO Version Deployment Demo\n${NC}"
printf "\n${GREEN}##### Get the NSO major version\n${NC}"
NSO_VERSION=$(ncs --version)
NSO_MAJOR_VERSION=${NSO_VERSION::3}
printf "${PURPLE}##### NSO major version: $NSO_MAJOR_VERSION\n${NC}"

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

printf "\n\n${PURPLE}##### No devices have been configured yet\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
show running-config devices device | nomore
EOF

printf "\n\n${PURPLE}##### To be able to handle the lower nso node as an LSA node the correct version of the cisco-nso-nc-x.x package needs to be installed\n${NC}"
ln -sf ${NCS_DIR}/packages/lsa/cisco-nso-nc-$NSO_MAJOR_VERSION upper-nso/packages
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
packages reload
EOF

printf "\n\n${PURPLE}##### Configure the two lower RFS nodes as LSA devices from the CFS NSO node\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
ncs:devices device lower-nso-1 device-type netconf ned-id cisco-nso-nc-$NSO_MAJOR_VERSION
ncs:devices device lower-nso-1 authgroup default
ncs:devices device lower-nso-1 lsa-remote-node lower-nso-1
ncs:devices device lower-nso-1 state admin-state unlocked
ncs:devices device lower-nso-2 device-type netconf ned-id cisco-nso-nc-$NSO_MAJOR_VERSION
ncs:devices device lower-nso-2 authgroup default
ncs:devices device lower-nso-2 lsa-remote-node lower-nso-2
ncs:devices device lower-nso-2 state admin-state unlocked
commit dry-run
commit
ncs:devices fetch-ssh-host-keys
ncs:devices sync-from
EOF

printf "\n\n${PURPLE}##### The configured devices of the lower nodes can now be viewed and alarms can be inspected\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
show running-config devices device config devices device | display xpath | nomore
show devices device lower-nso-1 live-status alarms summary | nomore
EOF

printf "\n\n${PURPLE}##### Create an NSO $NSO_MAJOR_VERSION LSA NETONF NED package\n${NC}"
ncs-make-package --no-netsim --no-java --no-python --lsa-netconf-ned package-store/rfs-vlan/src/yang --lsa-lower-nso cisco-nso-nc-$NSO_MAJOR_VERSION --package-version $NSO_MAJOR_VERSION --dest upper-nso/packages/rfs-vlan-nc-$NSO_MAJOR_VERSION --build rfs-vlan-nc-$NSO_MAJOR_VERSION

printf "\n${PURPLE}##### Install the cfs-vlan service\n${NC}"
ln -sf ../../package-store/cfs-vlan upper-nso/packages
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
packages reload
EOF

printf "\n\n${GREEN}##### Verify the CFS-VLAN service\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n\n${PURPLE}##### Enable the NSO progress traces${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
unhide debug
config
devices device lower-nso-* config progress trace progress-trace enabled verbosity very-verbose destination format csv file progress-trace.csv
top
progress trace progress-trace enabled verbosity very-verbose destination file progress-trace.csv format csv
commit
EOF

printf "${PURPLE}##### Configure the cfs-vlan service\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77
commit dry-run
commit commit-queue sync
EOF

printf "\n${PURPLE}##### Disable the NSO progress traces${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
unhide debug
config
progress trace progress-trace disabled
commit
devices device lower-nso-* config progress trace progress-trace disabled
commit
EOF

printf "\n\n${PURPLE}##### Review the configuration changes\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
cfs-vlan v1 get-modifications
EOF

printf "\n\n${PURPLE}##### Show a graph representation of the upper-nso progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py upper-nso/logs/progress-trace.csv
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n\n${NC}"
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "${PURPLE}##### Show a graph representation of the lower-nso-1 progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py lower-nso-1/logs/progress-trace.csv
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "${PURPLE}##### Show a graph representation of the lower-nso-2 progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py lower-nso-2/logs/progress-trace.csv
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "\n\n${GREEN}##### Makefile setup\n${NC}"
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
printf "\n\n${PURPLE}##### Enable the NSO progress traces${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
unhide debug
config
devices device lower-nso-* config progress trace progress-trace enabled verbosity very-verbose destination format csv file progress-trace.csv
top
progress trace progress-trace enabled verbosity very-verbose destination file progress-trace.csv format csv
commit
EOF

printf "${PURPLE}##### Configure the cfs-vlan service (using default commit-queue async)\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77
commit dry-run
commit
EOF

printf "\n${PURPLE}##### Waiting for upper-nso, lower-nso-1 and lower-nso-2 commit queue items to complete...\n${NC}"
while : ; do
    arr1=($(echo "show devices commit-queue queue-item | icount" | env ${ENV}4569 ncs_cli -C -u admin))
    res1=${arr1[1]}
    arr2=($(echo "show devices device lower-nso-2 live-status devices commit-queue queue-item | icount" | env ${ENV}4569 ncs_cli -C -u admin))
    res2=${arr2[1]}
    arr3=($(echo "show devices device lower-nso-2 live-status devices commit-queue queue-item | icount" | env ${ENV}4569 ncs_cli -C -u admin))
    res3=${arr3[1]}
    if [ "$res1" == "0" ] && [ "$res2" == "0" ] && [ "$res3" == "0" ]; then
        printf "${GREEN}##### All commit queue items completed\n${NC}"
        break
    fi
    printf "${RED}##### Waiting for $res1 upper-nso, $res2 lower-nso-1, $res3 lower-nso-2 commit queue items to complete...\n${NC}"
    sleep .1
done

printf "\n${PURPLE}##### Disable the NSO progress traces${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
unhide debug
config
progress trace progress-trace disabled
commit
devices device lower-nso-* config progress trace progress-trace disabled
commit
EOF

printf "\n\n${PURPLE}##### Review the configuration changes\n${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
cfs-vlan v1 get-modifications
EOF

printf "\n\n${PURPLE}##### Show a graph representation of the upper-nso progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py upper-nso/logs/progress-trace.csv
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n\n${NC}"
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "${PURPLE}##### Show a graph representation of the lower-nso-1 progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py lower-nso-1/logs/progress-trace.csv
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "${PURPLE}##### Show a graph representation of the lower-nso-2 progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py lower-nso-2/logs/progress-trace.csv
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
