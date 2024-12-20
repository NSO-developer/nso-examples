#!/bin/sh

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

printf "\n${PURPLE}##### Reset and setup the example\n${NC}"

make stop &> /dev/null
make clean all start

printf "\n${PURPLE}##### Create and initialize a vrouter instance${NC}"

ncs_cli -n -u admin -C << EOF
config
vrouter vr-01
commit dry-run
commit
EOF

while [[ $(ncs_cmd -o -c 'maapi_get "/vrouter{vr-01}/plan/component{vr:vrouter vrouter}/state{vr:vm-requested}/status"') != "reached" ]]; do
    printf "${RED}#### Waiting for the nano service to reach the vr:vm-requested state...\n${NC}"
    sleep 1
done

ncs_cli -n -u admin -C << EOF
show vrouter vr-01 plan | tab | nomore
vrouter vr-01 get-modifications | nomore
EOF

printf "\n\n${PURPLE}##### Set the vm-up-and-running leaf to \"true\"\n${NC}"

ncs_cmd -dd -o -c 'mset "/vr:vrouter{vr-01}/vm-up-and-running" "true"'

echo "";
while [[ $(ncs_cmd -o -c 'maapi_get "/vrouter{vr-01}/plan/component{vr:vrouter vrouter}/state{ncs:ready}/status"') != "reached" ]]; do
    printf "${RED}#### Waiting for the nano service to reach the ncs:ready state...\n${NC}"
    sleep 1
done

ncs_cli -n -u admin -C << EOF
show vrouter vr-01 vm-up-and-running
show vrouter vr-01 plan | tab | nomore
vrouter vr-01 get-modifications | nomore
show running-config vrouter
show running-config vm-instance
EOF

printf "\n\n${GREEN}##### Done!\n${NC}"
