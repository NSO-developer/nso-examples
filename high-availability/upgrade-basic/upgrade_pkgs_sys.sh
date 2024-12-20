#!/usr/bin/env bash

# A Basic NSO Simulated System Install Built-in High Availability Two Node
# Package Upgrade Example.
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
NSO_IP1="$IP1" NSO_IP2="$IP2" make clean system start

printf "\n\n${PURPLE}##### Initial high-availability config for both nodes\n${NC}"
env $NODE1 ncs_load -W -Fp -p /high-availability

printf "\n${PURPLE}##### Add some dummy config to node 1, replicated to secondary node 2\n${NC}"
env $NODE1 ncs_cli --cwd nso-node1 -n -u admin -C << EOF
config
dummies dummy d1 dummy 1.2.3.4
commit
end
show high-availability | notab | nomore
show running-config dummies | nomore
show packages package dummy package-version | nomore
EOF

env $NODE2 ncs_cli --cwd nso-node2 -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
show packages package dummy package-version | nomore
EOF

printf "\n\n${PURPLE}##### Backup before upgrading\n##### Since we are simulating a"
printf " system install with a local NSO install, we backup the runtime directories\n"
printf "for potential disaster recovery. Normally we would use the ncs-backup tool for a system install\n${NC}"
make backup

printf "\n\n${PURPLE}##### Upgrade node 1 system install packags and sync the packages to node 2. Add some new config through node 1\n${NC}"
env $NODE1 ncs_cli --cwd nso-node1 -n -u admin -C << EOF
software packages list
software packages fetch package-from-file $PWD/package-store/inert-1.0.tar.gz
software packages fetch package-from-file $PWD/package-store/dummy-1.1.tar.gz
software packages list
software packages install package inert-1.0
software packages install package dummy-1.1 replace-existing
software packages list
packages ha sync and-reload { wait-commit-queue-empty }
config
dummies dummy d1 description "hello world"
top
inerts inert i1 dummy 4.3.2.1
commit
EOF

env $NODE1 ncs_cli --cwd nso-node1 -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
show running-config inerts | nomore
show packages package dummy package-version | notab | nomore
EOF

env $NODE2 ncs_cli --cwd nso-node2 -n -u admin -C << EOF
show high-availability | notab | nomore
show running-config dummies | nomore
show running-config inerts | nomore
show packages package dummy package-version | notab | nomore
EOF

printf "\n\n${GREEN}##### Done!\n${NC}"
