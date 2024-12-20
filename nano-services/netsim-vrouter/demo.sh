#!/usr/bin/env bash

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

printf "\n${PURPLE}###### Reset and setup the example\n${NC}"

make stop &> /dev/null
make clean all start

printf "\n${PURPLE}##### Deploy and configure $1 vrouter through the vrouter nano service, but immediately delete the service during init so that the nano service backtrack\n${NC}"

VROUTERS=""
i=1
while [ $i -le $1 ]; do
    VROUTERS+="vrouters vrouter vr$i iface eth$(($i-1)) unit $i vid $i;"
    i=$(($i+1))
done

ncs_cli -n -u admin -C << EOF
config
$VROUTERS
commit dry-run
commit
no vrouters
commit dry-run
commit
do show zombies | nomore
do show side-effect-queue | nomore
EOF

echo ""
while [[ $(ncs_cmd -o -c 'maapi_num_instances "/zombies/service"') != "0" ]]; do
      printf "${RED}##### Waiting for the nano service instances to be deleted...\n${NC}"
    sleep 1
done

printf "\n\n${PURPLE}##### Deploy and configure $1 vrouter through the vrouter nano service\n${NC}"

VROUTERS=""
i=$(($1+1))
NUM=$(($1*2))
while [ $i -le $NUM ]; do
    VROUTERS+="vrouters vrouter vr$i iface eth$(($i-1)) unit $i vid $i;"
    i=$(($i+1))
done

ncs_cli -n -u admin -C << EOF
config
$VROUTERS
commit dry-run
commit
do show side-effect-queue | nomore
EOF

echo ""

VROUTERS=()
i=$(($1+1))
NUM=$(($1*2))
while [ $i -le $NUM ]; do
    VROUTERS+=("vr$i")
    i=$(($i+1))
done

for VROUTER in "${VROUTERS[@]}"
do
    while : ; do
      CMD="maapi_get \"/vrouters/vrouter{$VROUTER}/plan/component{ncs:self self}/state{ncs:ready}/status\""
      if [[ $(ncs_cmd -o -c "$CMD") == "reached" ]]; then
          printf "${GREEN}##### $VROUTER deployed\n${NC}"
          break
      fi
      printf "${RED}##### Waiting for $VROUTER to reach the ncs:ready state...\n${NC}"
      sleep 1
    done
done

ncs_cli -n -u admin -C << EOF
show vrouters vrouter plan component | tab | nomore
show running-config devices device | nomore
EOF

printf "\n\n${PURPLE}##### Make some configuration changes to the vrouter service\n${NC}"

i=$(($1+1))
ncs_cli -n -u admin -C << EOF
config
vrouters vrouter vr$i iface eth99 unit 99 vid 99
commit dry-run
commit
show full-configuration devices device vr5 config | nomore
EOF

printf "\n\n${PURPLE}##### Delete vrouter service to backtrack\n${NC}"

ncs_cli -n -u admin -C << EOF
config
do show vrouters vrouter plan component | tab | nomore
no vrouters
commit dry-run
commit
do show zombies | nomore
do show side-effect-queue | nomore
EOF

echo ""
while [[ $(ncs_cmd -o -c 'maapi_num_instances "/zombies/service"') != "0" ]]; do
      printf "${RED}##### Waiting for the nano service instances to be deleted...\n${NC}"
    sleep 1
done

i=$(($1*2+1))
printf "\n\n${PURPLE}##### Deploy and configure a vrouter through the vrouter nano service, but set the admin state on the netsim device to \"locked\"\n\n${NC}"
ncs_cli -u admin -C << EOF
config
vrouters vrouter vr$i iface eth$(($i-1)) unit $i vid $i
commit dry-run
commit
EOF

until [ -d "./netsim" ] && [[ "$(ncs-netsim list)" = *"vr$i"* ]]; do
    printf "${RED}##### Waiting for the netsim device to be listed...\n${NC}"
    sleep 1
done

NETSIM_PORT=$(ncs-netsim get-port vr$i 2> /dev/null)
while [ "$NETSIM_PORT" = "" ]; do
    NETSIM_PORT=$(ncs-netsim get-port vr$i 2> /dev/null)
    printf "${RED}##### Waiting for the netsim device to be created to get the port number...\n${NC}"
    sleep 1
done

until $NCS_DIR/netsim/confd/bin/confd_cmd -p $NETSIM_PORT -c 'wait-start 1' &> /dev/null; do
    printf "${RED}##### Waiting for the netsim device with port $NETSIM_PORT to start...\n${NC}"
    sleep .5
done

printf "\n${PURPLE}##### Set admin state to \"locked\" on the netsim device to make the transaction fail\n${NC}"
$NCS_DIR/netsim/confd/bin/confd_cmd -o -p $NETSIM_PORT -c 'mset "/r:sys/state/admin-state" "locked"'

CMD="maapi_exists \"/vrouters/vrouter{vr$i}/plan/failed\""
while [[ $(ncs_cmd -o -c "$CMD") != "yes" ]]; do
  printf "${RED}##### Waiting for the vr$i plan to fail due to the transaction being aborted by the device...\n${NC}"
  sleep 1
done

ncs_cli -n -u admin -C << EOF
show vrouters vrouter vr$i plan | tab | nomore
EOF

printf "\n\n${PURPLE}##### Set admin state to \"unlocked\" on the netsim device to make the next transaction succeed\n${NC}"
$NCS_DIR/netsim/confd/bin/confd_cmd -o -p $NETSIM_PORT -c 'mset "/r:sys/state/admin-state" "unlocked"'

printf "\n${PURPLE}##### Redeploy\n${NC}"
ncs_cli -n -u admin -C << EOF
vrouters vrouter vr$i re-deploy
EOF

printf "\n\n"
CMD="maapi_get \"/vrouters/vrouter{vr$i}/plan/component{ncs:self self}/state{ncs:ready}/status\""
while [[ $(ncs_cmd -o -c "$CMD") != "reached" ]]; do
    printf "${RED}##### Waiting for the nano service to reach the ncs:ready state...\n${NC}"
    sleep 1
done
printf "${GREEN}##### vr$i deployed\n${NC}"

ncs_cli -n -u admin -C << EOF
show vrouters vrouter vr$i plan | tab | nomore
vrouters vrouter vr$i plan component vrouter vr$i-day0 state onboarded get-modifications
vrouters vrouter vr$i plan component vrouter-day1 vr$i-day1 state configured get-modifications
show running-config devices device vr$i config | nomore
EOF

printf "\n\n${GREEN}##### Done!\n${NC}"
