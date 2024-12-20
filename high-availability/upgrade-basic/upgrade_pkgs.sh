#!/usr/bin/env bash

# A Basic NSO Built-in High Availability Two Node Package Upgrade Example.
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
PWD=$(pwd)

function usage()
{
   printf "${GREEN}Example of a basic high availability setup with one primary"
   printf " and one secondary node performing a package upgrade\n\n"
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

while getopts "a:b:h" OPTION; do
    case "${OPTION}"
    in
        a)  IP1="${OPTARG}";;
        b)  IP2="${OPTARG}";;
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
show packages package dummy package-version | nomore
EOF

env $NODE2 ncs_cli -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
show packages package dummy package-version | nomore
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

printf "\n\n${PURPLE}##### Disable node 1 high availability for node 2 to automatically failover and assume primary role in read-only mode\n${NC}"
env $NODE1 ncs_cli -n -u admin -C << EOF
high-availability disable
EOF

printf "\n${PURPLE}##### Disable read-only mode on node 1 to allow rw transaction\n"
env $NODE1 ncs_cli -n -u admin -C << EOF
high-availability read-only mode false
EOF

printf "\n\n${PURPLE}##### Upgrade node 1 local install packages\n${NC}"
cp $PWD/package-store/dummy-1.1.tar.gz $PWD/nso-node1/packages
cp $PWD/package-store/inert-1.0.tar.gz $PWD/nso-node1/packages
rm $PWD/nso-node1/packages/dummy-1.0.tar.gz
env $NODE1 ncs_cli -n -u admin -C << EOF
packages reload
EOF

printf "\n\n${PURPLE}##### Disable high availability for node 2\n${NC}"
env $NODE2 ncs_cli -n -u admin -C << EOF
high-availability disable
EOF

printf "\n\n${PURPLE}##### Enable high availability for node 1\n"
printf "##### Note: If you enable HA on n1 before disabling n2, n1 might become secondary to n2,\n"
printf "            requiring a full timeout to actually assume primary role.\n${NC}"
env $NODE1 ncs_cli -n -u admin -C << EOF
high-availability enable
EOF

printf "\n\n${PURPLE}##### Upgrade node 2 local install packages and enable high availability to assume secondary role\n${NC}"
cp $PWD/package-store/dummy-1.1.tar.gz $PWD/nso-node2/packages
cp $PWD/package-store/inert-1.0.tar.gz $PWD/nso-node2/packages
rm $PWD/nso-node2/packages/dummy-1.0.tar.gz
env $NODE2 ncs_cli -n -u admin -C << EOF
packages reload
EOF

printf "\n\n"
while [[ $(env $NODE1 ncs_cmd -o -c 'mrtrans; mget "/high-availability/status/mode"') != "primary" ]]; do
    printf "${RED}#### Waiting for node 1 to become primary...\n${NC}"
    sleep 1
done

env $NODE2 ncs_cli -n -u admin -C << EOF
high-availability enable
EOF

printf "\n\n"
while [[ $(env $NODE1 ncs_cmd -o -c 'mrtrans; maapi_num_instances "/high-availability/status/connected-secondary"') != "1" ]]; do
    printf "${RED}#### Waiting for node 2 to become secondary to node 1...\n${NC}"
    sleep 1
done

printf "\n\n${PURPLE}##### Add some new config through node 1\n${NC}"
env $NODE1 ncs_cli -n -u admin -C << EOF
config
dummies dummy d1 description "hello world"
top
inerts inert i1 dummy 4.3.2.1
commit
EOF

env $NODE1 ncs_cli -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
show running-config inerts | nomore
show packages package package-version | notab | nomore
EOF

env $NODE2 ncs_cli -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
show running-config inerts | nomore
show packages package package-version | notab | nomore
EOF

printf "\n\n${GREEN}##### Done!\n${NC}"
