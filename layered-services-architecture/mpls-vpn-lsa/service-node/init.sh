#!/bin/sh
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

printf "\n${GREEN}##### Get the NSO major version\n${NC}"
NSO_VERSION=$(ncs --version)
NSO_MAJOR_VERSION=${NSO_VERSION::3}
printf "${PURPLE}##### NSO major version: $NSO_MAJOR_VERSION\n${NC}"

printf "\n${PURPLE}##### Configure the nodes in the cluster from the CFS NSO node\n${NC}"
ncs_cli -n -u admin -C << EOF
config
cluster device-notifications enabled
cluster remote-node nso-1 address 127.0.0.1 port 2223 authgroup default username admin
cluster remote-node nso-2 address 127.0.0.1 port 2226 authgroup default username admin
cluster remote-node nso-3 address 127.0.0.1 port 2225 authgroup default username admin
cluster commit-queue enabled
commit
cluster remote-node nso-* ssh fetch-host-keys
load merge init-data/nsos.xml
commit
ncs:devices fetch-ssh-host-keys
ncs:devices sync-from
ncs:devices device nso-* trace raw
commit
EOF

printf "\n\n${PURPLE}##### Load QoS and topology config\n${NC}"
ncs_cli -n -u admin -C << EOF
config
load merge init-data/qos.xml
load merge init-data/topology.xml
commit
EOF
