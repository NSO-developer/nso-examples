#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### SRv6 VPN services netsim demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
make stop-netsim clean

printf "\n${PURPLE}##### Start the simulated network, the NSO instance, and tell NSO about the netsim devices\n${NC}"
make netsim

printf "\n${PURPLE}##### Verify the devices were onboarded successfully\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
show devices device * last-in-sync | nomore
EOF

printf "\n\n${PURPLE}##### Enable the SRv6 services\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
core-network provision
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the resulting SRv6 service instance configuration changes\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
core-network services srv6-node pe-01 get-modifications outformat cli-c | nomore
EOF

printf "\n\n${PURPLE}##### Configure a layer-2 point-to-point VPN link (also called E-Line) between ce-1-4 and ce-1-3\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
eline sample-eline customer Tail-f ports [ pe-01-3 pe-02-4 ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Create a multipoint layer-2 VPN (EPVN ELAN) between ce-1-1, ce-1-2 and ce-1-3, inspect but do not commit\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
l2vpn sample-l2vpn customer Tail-f ports [ pe-01-2 pe-02-2 pe-02-3 ]
commit dry-run outformat native
EOF

printf "\n\n${PURPLE}##### Configure a basic layer-3 VPN between ce-1-1 and ce-1-2, requiring preconfigured static routes on CEs\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
l3vpn sample-l3vpn customer Tail-f link 1 port pe-01-2
l3vpn sample-l3vpn customer Tail-f link 2 port pe-02-2
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Add another link towards ce-1-3 to the layer-3 VPN instance using BGP to exchange routes with the CE device\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
l3vpn sample-l3vpn link 3 port pe-02-3 bgp-peering enabled peer-as 65010
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### The ce-1-3 device now has two interfaces configured for VPN; one as a layer-2 link and one as a layer-3 link\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
show running-config devices device ce-1-3 config | nomore
EOF

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop-netsim clean
fi

printf "\n${GREEN}##### Done!\n${NC}"