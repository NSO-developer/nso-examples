#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### MPLS Layer3 VPN Demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the example\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Setup the environment\n${NC}"
make all

printf "\n${PURPLE}##### Start the simulated network and NSO\n${NC}"
make start

printf "\n${GREEN}##### VPN service configuration\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Sync the configuration from all network devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Configure a VPN network\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn l3vpn volvo as-number 65101 endpoint main-office ce-device ce0 ce-interface GigabitEthernet0/11 ip-network 10.10.1.0/24 bandwidth 12000000
vpn l3vpn volvo as-number 65101 endpoint branch-office1 ce-device ce1 ce-interface GigabitEthernet0/11 ip-network 10.7.7.0/24 bandwidth 6000000
vpn l3vpn volvo as-number 65101 endpoint branch-office2 ce-device ce4 ce-interface GigabitEthernet0/18 ip-network 10.8.8.0/24 bandwidth 300000
commit dry-run outformat native
commit dry-run | debug template
commit
EOF

printf "\n\n${PURPLE}##### Add a second VPN\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn l3vpn ford as-number 65200 endpoint main-office ce-device ce2 ce-interface GigabitEthernet0/5 ip-network 192.168.1.0/24 bandwidth 10000000
vpn l3vpn ford as-number 65200 endpoint branch-office1 ce-device ce3 ce-interface GigabitEthernet0/5 ip-network 192.168.2.0/24 bandwidth 5500000
vpn l3vpn ford as-number 65200 endpoint branch-office2 ce-device ce5 ce-interface GigabitEthernet0/5 ip-network 192.168.7.0/24 bandwidth 1500000
commit dry-run outformat native
commit
EOF

printf "\n\n${GREEN}##### Adding new devices\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Add two new CE devices to the topology\n${NC}"
ncs_cli -n -u admin -C << EOF
config
topology connection c7 endpoint-1 device ce7 interface GigabitEthernet0/1 ip-address 192.168.1.25/30
topology connection c7 endpoint-2 device pe3 interface GigabitEthernet0/0/0/2 ip-address 192.168.1.26/30
topology connection c7 link-vlan 104
topology connection c8 endpoint-1 device ce8 interface GigabitEthernet0/1 ip-address 192.168.1.29/30
topology connection c8 endpoint-2 device pe3 interface GigabitEthernet0/0/0/2 ip-address 192.168.1.30/30
topology connection c8 link-vlan 104
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Add the devices to the VPNs\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn l3vpn ford as-number 65200 endpoint new-branch-office ce-device ce7 ce-interface GigabitEthernet0/5 ip-network 192.168.9.0/24 bandwidth 4500000
vpn l3vpn volvo as-number 65101 endpoint new-branch-office ce-device ce8 ce-interface GigabitEthernet0/5 ip-network 10.8.9.0/24 bandwidth 4500000
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Change the topology information as the new CE7 and CE8 devices are now connected to PE1 instead of PE3\n${NC}"
ncs_cli -n -u admin -C << EOF
config
topology connection c7 endpoint-2 device pe1
topology connection c8 endpoint-2 device pe1
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Use service re-deploy dry-run to preview what will be sent to the network\n${NC}"
ncs_cli -n -u admin -C << EOF
vpn l3vpn * re-deploy dry-run { outformat native }
EOF

printf "\n\n${PURPLE}##### Re-deploy to send the configuration to the network\n${NC}"
ncs_cli -n -u admin -C << EOF
vpn l3vpn * re-deploy
EOF

printf "\n\n${GREEN}##### QOS Configuration\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Add QOS to the VPN customers\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn l3vpn volvo qos qos-policy SILVER
vpn l3vpn ford qos qos-policy BRONZE
commit dry-run outformat native
commit
EOF

printf "\n\n${GREEN}##### External QOS Policy Changes\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Set DSCP values for each class will be set on all CE routers and matched against the PE router and used within the MPLS cloud\n${NC}"
ncs_cli -n -u admin -C << EOF
config
qos qos-class MISSION-CRITICAL dscp-value af32
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Preview what NSO will calculate as the minimal diff to be sent to the network\n${NC}"
ncs_cli -n -u admin -C << EOF
vpn l3vpn * re-deploy dry-run { outformat native }
EOF

printf "\n\n${PURPLE}##### Re-deploy to send the configuration to the network\n${NC}"
ncs_cli -n -u admin -C << EOF
vpn l3vpn * re-deploy
EOF

printf "\n\n${GREEN}##### Decommissioning VPNs\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Decommission the volvo L3VPN\n${NC}"
ncs_cli -n -u admin -C << EOF
config
no vpn l3vpn volvo
commit
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    make stop

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
