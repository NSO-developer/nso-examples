#!/usr/bin/env bash

# NSO nano service example.
#
# ncs_cli command over IPC demo script
#
# See the README file for more information

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

printf "\n${PURPLE}###### Reset and setup the example\n${NC}"
make stop &> /dev/null
make clean all start

echo ""
ncs-netsim list

printf "\n${PURPLE}##### Configure a VPN network by creating a new Volvo L3VPN nano service instance\n${NC}"
ncs_cli -n -u admin -C << EOF
paginate false
devices sync-from
show running-config topology
show running-config devices device esc0 config
config
load merge vpn_volvo.xml
commit dry-run outformat native
commit
EOF

while : ; do
    arr=($(echo "show vpn l3vpn volvo plan component self self state ready status" | ncs_cli -C -u admin))
    res=${arr[1]}
    if [ "$res" == "reached" ]; then
        printf "${GREEN}##### Volvo L3VPN deployed\n${NC}"
        break
    fi
    printf "${RED}##### Waiting for the Volvo L3VPN service instance to reach the ncs:ready state...\n${NC}"
    sleep 1
done

printf "\n${PURPLE}##### Show status after creating the new Volvo L3VPN nano service\n${NC}"
ncs_cli -n -u admin -C << EOF
paginate false
show vm-manager start volvo_vpn_CSR plan component * state * status | tab
show vpn l3vpn volvo plan component l3vpn-virtual head-office state ready status
show vpn l3vpn volvo plan component * state * status | tab
show running-config topology
show running-config devices device esc config
show running-config devices device volvo_vpn_CSR_esc0 port
EOF

echo ""
ncs-netsim list

printf "\n${PURPLE}###### Delete 'head-office' endpoint (ctrl-c to abort)\n${NC}"
i=5
while [ $i -gt 0 ]; do
    printf "${PURPLE}$i\n${NC}"
    sleep 1
    i=$(($i-1))
done

ncs_cli -n -u admin -C << EOF
paginate false
config
no vpn l3vpn volvo endpoint head-office
commit dry-run outformat native
commit
do show zombies
do show vpn l3vpn volvo plan component * state * status | tab
EOF

echo ""
res="-1"
while [ "$res" != "0" ]; do
    arr=($(echo "show zombies service | icount" | ncs_cli -C -u admin))
    res=${arr[1]}
    printf "${RED}##### Waiting for $res nano service instances to be deleted...\n${NC}"
    sleep 1
done

printf "\n${PURPLE}##### Show status after deleting the 'head-office' endpoint\n${NC}"
ncs_cli -n -u admin -C << EOF
paginate false
show vpn l3vpn volvo plan component * state * status | tab
show devices device | display-level 1
show vm-manager start
show zombies
show running-config devices device esc config
EOF

echo ""
ncs-netsim list

printf "\n${PURPLE}##### Reconfigure the Volvo L3VPN nano service to again include the 'head-office' endpoint\n${NC}"
ncs_cli -n -u admin -C << EOF
paginate false
config
load merge vpn_volvo.xml
commit dry-run outformat native
commit
EOF

while : ; do
    arr=($(echo "show vpn l3vpn volvo plan component l3vpn-virtual head-office state ready status" | ncs_cli -C -u admin))
    res=${arr[1]}
    if [ "$res" == "reached" ]; then
        printf "${GREEN}##### Volvo head-office component deployed\n${NC}"
        break
    fi
    printf "${RED}##### Waiting for the Volvo head-office component to reach the ncs:ready state...\n${NC}"
    sleep 1
done

echo ""
ncs-netsim list

printf "\n${PURPLE}###### Decomission the Volvo VPN (ctrl-c to abort)\n${NC}"
i=5
while [ $i -gt 0 ]; do
    printf "${PURPLE}$i\n${NC}"
    sleep 1
    i=$(($i-1))
done

ncs_cli -n -u admin -C << EOF
paginate false
config
no vpn l3vpn volvo
commit dry-run outformat native
commit
do show zombies
do show vpn l3vpn
EOF

echo ""
res="-1"
while [ "$res" != "0" ]; do
    arr=($(echo "show zombies service | icount" | ncs_cli -C -u admin))
    res=${arr[1]}
    printf "${RED}##### Waiting for $res nano service instances to be deleted...\n${NC}"
    sleep 1
done

printf "\n${PURPLE}##### Show status after decomissioning the Volvo VPN\n${NC}"
ncs_cli -n -u admin -C << EOF
paginate false
show vpn l3vpn
show devices device | display-level 1
show vm-manager start
show zombies
show running-config devices device esc config
EOF

echo ""
ncs-netsim list

printf "\n\n${GREEN}##### Done!\n${NC}"
