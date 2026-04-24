#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

pause() {
    prompt="${1-}"
    if [ -z "$prompt" ]; then
        prompt="${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    fi
    if [ -z "$NONINTERACTIVE" ]; then
        printf "%b" "$prompt"
        read -n 1 -s -r
    fi
}


printf "\n${GREEN}##### SRv6 multivendor VPN services netsim demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
make stop clean

printf "\n${PURPLE}##### Start the simulated network, the NSO instance, and tell NSO about the netsim devices\n${NC}"
make all start

printf "\n${PURPLE}##### Multivendor topology summary\n${NC}"
printf "##### core-1: Nokia SR OS SRv6 core router\n"
printf "##### pe-01: Juniper Junos SRv6 PE router\n"
printf "##### pe-02: Cisco IOS-XR SRv6 PE router\n"

printf "\n${PURPLE}##### Verify the devices were onboarded successfully and that the multivendor topology is in use\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show devices device * last-in-sync | nomore
show running-config devices device core-1 device-type | nomore
show running-config devices device pe-01 device-type | nomore
show running-config devices device pe-02 device-type | nomore
EOF

printf "\n\n${PURPLE}##### Enable the SRv6 services across the Nokia SR OS core, the Junos PE, and the IOS-XR nodes\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
core-network provision
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the resulting SRv6 service instance configuration changes on Nokia SR OS, Junos, and IOS-XR\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
core-network services srv6-node core-1 get-modifications outformat cli-c | nomore
core-network services srv6-node pe-01 get-modifications outformat xml | nomore
core-network services srv6-node pe-02 get-modifications outformat cli-c | nomore
EOF

printf "\n\n${PURPLE}##### Configure a layer-2 point-to-point VPN link between the Junos PE access port and the IOS-XR PE access port towards ce-1-3\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
eline sample-eline customer Tail-f ports [ pe-01-3 pe-02-4 ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Create a multipoint layer-2 VPN across Junos pe-01 and IOS-XR pe-02 access ports, inspect but do not commit\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
l2vpn sample-l2vpn customer Tail-f ports [ pe-01-2 pe-02-2 pe-02-3 ]
commit dry-run outformat native
EOF

printf "\n\n${PURPLE}##### Configure a basic layer-3 VPN between the Junos PE access port and an IOS-XR PE access port\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
l3vpn sample-l3vpn customer Tail-f link 1 port pe-01-2
l3vpn sample-l3vpn customer Tail-f link 2 port pe-02-2
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Add another IOS-XR PE link towards ce-1-3 using BGP to exchange routes with the managed CE device\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
l3vpn sample-l3vpn link 3 port pe-02-3 bgp-peering enabled peer-as 65010
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Inspect the resulting Junos PE service instances, Nokia SR OS SRv6 core state, and the managed CE device\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show running-config devices device pe-01 config configuration routing-instances | display xml | nomore
show running-config devices device core-1 config router Base segment-routing segment-routing-v6 | display xml | nomore
show running-config devices device ce-1-3 config | display xml | nomore
EOF

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
