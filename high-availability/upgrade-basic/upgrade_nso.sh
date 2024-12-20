#!/usr/bin/env bash

# A Basic NSO Built-in High-Availability Two Node NSO Version Upgrade Example.
#
# Upgrade script
#
# See the README file for more information

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

function version_lt() { test "$(printf '%s\n' "$@" | sort -rV | head -n 1)" != "$1"; }
function version_ge() { test "$(printf '%s\n' "$@" | sort -rV | head -n 1)" == "$1"; }

NSO55=5.5
NSO56=5.6

function usage()
{
   printf "${GREEN}Example of a basic high availability setup with one primary"
   printf " and one secondary node performing a NSO version upgrade\n\n"
   printf "  -a  IP address for node 1. Default: 127.0.1.1\n"
   printf "  -b  IP address for node 2. Default: 127.0.2.1\n"
   printf "\nOn most Linux distributions the above default IP addresses are"
   printf " configured for\nthe loopback interface by default. On MacOS the"
   printf " two unique IP addresses can be\ncreated using for example the ip"
   printf " or ifconfig command:\n\n"
   printf "# MacOS setup:\n"
   printf "\$ sudo ifconfig lo0 alias 127.0.1.1/24 up\n"
   printf "\$ sudo ifconfig lo0 alias 127.0.2.1/24 up\n\n"
   printf "# MacOS cleanup:\n"
   printf "\$ sudo ifconfig lo0 -alias 127.0.1.1\n"
   printf "\$ sudo ifconfig lo0 -alias 127.0.2.1\n\n${NC}"
}

while getopts "a:b:o:n:h" OPTION; do
    case "${OPTION}"
    in
        a)  IP1="${OPTARG}";;
        b)  IP2="${OPTARG}";;
        o)  OLD_DIR="${OPTARG}";;
        n)  NEW_DIR="${OPTARG}";;
        h)  usage; exit 0;;
        \?) echo "Invalid parameter"; usage; exit 1;;
    esac
done

set +u
if [ -z "$IP1" ]; then
    IP1="127.0.1.1"
fi
if [ -z "$IP2" ]; then
    IP2="127.0.2.1"
fi
if [ -z "$OLD_DIR" ]; then
    OLD_DIR=${NCS_DIR}
fi
if [ -z "$NEW_DIR" ]; then
    NEW_DIR=${NCS_DIR}
fi
if [ -f "$OLD_DIR/ncsrc" ]; then
    source $OLD_DIR/ncsrc
fi

if [ -n "$NCS_IPC_PATH" ]; then
NODE1="NCS_IPC_PATH=${NCS_IPC_PATH}.4561"
NODE2="NCS_IPC_PATH=${NCS_IPC_PATH}.4562"
else
# All nodes use the same IP for IPC but different ports
export NCS_IPC_ADDR=127.0.0.1
NODE1=NCS_IPC_PORT=4561
NODE2=NCS_IPC_PORT=4562
fi
set -u
OLD_VERSION=$(ncs --version)

printf "\n${PURPLE}##### Upgrade from $(ncs --version)\n${NC}"

printf "\n${PURPLE}##### Reset, setup, start node 1 & 2, and enable HA with start-up settings\n${NC}"
NSO_IP1="$IP1" NSO_IP2="$IP2" make stop &> /dev/null
NSO_IP1="$IP1" NSO_IP2="$IP2" make clean all start

printf "\n\n${PURPLE}##### Initial high-availability config for both nodes\n${NC}"
env $NODE1 ncs_load -W -Fp -p /high-availability

printf "\n${PURPLE}##### Add some dummy config to node 1, replicated to secondary node 2\n${NC}"
env $NODE1 ncs_cli -n -u admin -C << EOF
config
dummies dummy d1 dummy 1.2.3.4
commit
end
show high-availability | notab | nomore
show running-config dummies | nomore
EOF

env $NODE2 ncs_cli -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
EOF

printf "\n${PURPLE}##### Enable read-only mode on both nodes\n"
printf "##### Note: This is not strictly necessary on fail-over node 2 since we rely on timeout\n"
printf "#####       with rule-based algorithm enabled and never explicitly promote node 2.\n${NC}"
env $NODE1 ncs_cli -n -u admin -C << EOF
high-availability read-only mode true
show high-availability | notab | nomore
EOF

env $NODE2 ncs_cli -n -u admin -C << EOF
high-availability read-only mode true
show high-availability | notab | nomore
EOF

printf "\n\n${PURPLE}##### Backup before upgrading\n##### Since we are using a local NSO install, we backup the runtime directories for potential disaster recovery.\n${NC}"
make backup

set +u
if [ -f "$NEW_DIR/ncsrc" ]; then
    source $NEW_DIR/ncsrc
fi
set -u
NEW_VERSION=$(ncs --version)

# NSO 5.5 removed the show-log-directory parameter.
if version_lt $OLD_VERSION $NSO55 && version_ge $NEW_VERSION $NSO55; then
    sed -i.bak 's%<show-log-directory>./logs</show-log-directory>%%' nso-node1/ncs.conf
    sed -i.bak 's%<show-log-directory>./logs</show-log-directory>%%' nso-node2/ncs.conf
fi

# NSO 5.6 removed the large-scale parameters
if version_lt $OLD_VERSION $NSO56 && version_ge $NEW_VERSION $NSO56
then
    sed -i.bak '/<large-scale>/I,+7 d' nso-node1/ncs.conf
    sed -i.bak '/<large-scale>/I,+7 d' nso-node2/ncs.conf
fi

printf "\n\n${PURPLE}##### Disable node 1 high availability for node 2 to automatically failover and assume primary role in read-only mode\n${NC}"
env $NODE1 ncs_cli -n -u admin -C << EOF
high-availability disable
EOF

printf "\n\n${PURPLE}##### Switch VIP to point to node 2 instead of node 1\n${NC}"

printf "\n\n${PURPLE}##### Rebuild node 1 package(s) with $NEW_VERSION\n${NC}"
cd nso-node1/packages
tar xvfz dummy-1.0.tar.gz
rm dummy-1.0.tar.gz
cd -
make -C nso-node1/packages/dummy-1.0/src clean all

printf "\n\n${PURPLE}##### Upgrade node 1 to $NEW_VERSION\n${NC}"
env $NODE1 ncs --stop
env $NODE1 sname=ncsd1 ncs --cd nso-node1 -c $(pwd)/nso-node1/ncs.conf --with-package-reload

printf "\n${PURPLE}##### Switch VIP back to point to node 1\n${NC}"

printf "\n${PURPLE}##### Disable high availability for node 2\n${NC}"
env $NODE2 ncs_cli -n -u admin -C << EOF
high-availability disable
EOF

printf "\n\n${PURPLE}##### Enable high availability for node 1 that will assume primary role\n${NC}"
env $NODE1 ncs_cli -n -u admin -C << EOF
high-availability enable
EOF

printf "\n\n${PURPLE}##### Rebuild node 2 package(s) with $NEW_VERSION\n${NC}"
cd nso-node2/packages
tar xvfz dummy-1.0.tar.gz
rm dummy-1.0.tar.gz
cd -
make -C nso-node2/packages/dummy-1.0/src clean all

printf "\n\n${PURPLE}##### Upgrade node 2 to $(ncs --version).\n${NC}"
env $NODE2 ncs --stop
env $NODE2 sname=ncsd2 ncs --cd nso-node2 -c $(pwd)/nso-node2/ncs.conf --with-package-reload

printf "\n"
while [[ $(env $NODE1 ncs_cmd -o -c 'mrtrans; mget "/high-availability/status/mode"') != "primary" ]]; do
    printf "${RED}#### Waiting for node 1 to become primary...\n${NC}"
    sleep 1
done

printf "\n${PURPLE}##### Enable high availability for node 2 that will assume secondary role\n${NC}"
env $NODE2 ncs_cli -n -u admin -C << EOF
high-availability enable
EOF

printf "\n\n"
while [[ $(env $NODE1 ncs_cmd -o -c 'mrtrans; maapi_num_instances "/high-availability/status/connected-secondary"') != "1" ]]; do
    printf "${RED}#### Waiting for node 2 to become secondary to node 1...\n${NC}"
    sleep 1
done

env $NODE1 ncs_cli -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
EOF

env $NODE2 ncs_cli -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
EOF

printf "\n\n${GREEN}##### Done!\n${NC}"
