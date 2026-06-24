#!/usr/bin/env bash
set -eu

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m'
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

printf "\n${GREEN}##### Full brownfield migration demo: SR-MPLS estate to SRv6-only final state\n${NC}"
printf "${PURPLE}##### Runs SR-MPLS to SRv6 end-to-end and leaves service transport intent in the SRv6 target state\n${NC}"
printf "${PURPLE}##### Reset the lab so the migration starts from a known multivendor network state\n${NC}"
make stop clean

printf "\n${PURPLE}##### Start the multivendor netsim network and let NSO discover/sync the devices\n${NC}"
make all start

printf "\n${PURPLE}##### Brownfield discovery: inventory and device sync before service intent changes\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show packages package * oper-status | nomore
show devices device * last-in-sync | nomore
show running-config devices device pe-01 device-type | nomore
show running-config devices device pe-02 device-type | nomore
EOF

printf "\n\n${PURPLE}##### Phase 1: operate today's SR-MPLS estate with no SRv6 locators anywhere\n${NC}"
printf "${PURPLE}##### NSO exports the TE topology and loads PM/assurance inputs before any SRv6 underlay exists\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/service-assurance.xml
core-network export-te-topology
commit dry-run
commit
end
show running-config core-network services srv6-node | nomore
show running-config networks network srv6-transport-migration-te network-types | nomore
show running-config networks network srv6-transport-migration-te node pe-01 | nomore
show core-network services assurance-monitor transport-domain | nomore
core-network services assurance-monitor transport-domain self-test
show core-network services odn-template | nomore
EOF

printf "\n\n${PURPLE}##### Deploy the customer slice over today's transport: transport-profile sr-mpls color 3300\n${NC}"
printf "${PURPLE}##### The native dry-run shows ODN color steering before SRv6 is available\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/brownfield-migration-sr-mpls.xml
commit dry-run outformat native
commit
end
show network-slice-services slice-service bf-srmpls | nomore
show l3vpn nss-bf-srmpls-cg-l3 | nomore
show core-network services odn-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 | nomore
show running-config core-network services pm-profile nss-bf-srmpls-pm | nomore
network-slice-services slice-service bf-srmpls migration-readiness
core-network services plan-transport-migration slice-service bf-srmpls target-profile srv6
core-network services assess-transport-migration slice-service bf-srmpls connection-group cg-l3 target-profile srv6
EOF

printf "\n\n${PURPLE}##### Phase 2: enable SRv6 underlay across the PE/core transport domain before service migration\n${NC}"
printf "${PURPLE}##### This turns on network-wide SRv6 capability while the customer service is still SR-MPLS\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
core-network services srv6-node core-1
core-network services srv6-node core-2
core-network services srv6-node core-3
core-network services srv6-node core-4
core-network services srv6-node core-5
core-network services srv6-node pe-01
core-network services srv6-node pe-02
commit dry-run outformat native
commit
end
show running-config core-network services srv6-node | nomore
show running-config devices device pe-01 config configuration routing-options source-packet-routing | nomore
show running-config devices device pe-02 config segment-routing srv6 | nomore
core-network services plan-transport-migration slice-service bf-srmpls target-profile srv6
core-network services assess-transport-migration slice-service bf-srmpls connection-group cg-l3 target-profile srv6
EOF

printf "\n\n${PURPLE}##### Full service migration: move the brownfield slice from SR-MPLS to SRv6\n${NC}"
printf "${PURPLE}##### NSO preserves the customer color, commits the service-intent change, and verifies SRv6 ODN rendering\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
core-network services migrate-transport slice-service bf-srmpls connection-group cg-l3 target-profile srv6 execute true
show network-slice-services slice-service bf-srmpls | nomore
show l3vpn nss-bf-srmpls-cg-l3 | nomore
show core-network services odn-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 srv6 | nomore
network-slice-services slice-service bf-srmpls migration-readiness
core-network services plan-transport-migration slice-service bf-srmpls target-profile srv6 include-no-op true
core-network services assess-transport-migration slice-service bf-srmpls connection-group cg-l3 target-profile srv6
network-slice-services slice-service bf-srmpls check-sync
EOF

printf "\n\n${PURPLE}##### Retire SR-MPLS scaffolding by keeping no rollback intent and validating the final service state\n${NC}"
printf "${PURPLE}##### SR-MPLS in this lab is service-rendered ODN color steering, so the migration itself replaces the SR-MPLS branch with SRv6 ODN state\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show network-slice-services slice-service bf-srmpls | nomore
show l3vpn nss-bf-srmpls-cg-l3 | nomore
show running-config core-network services srv6-node | nomore
show core-network services odn-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 srv6 | nomore
network-slice-services slice-service bf-srmpls check-sync
EOF

printf "\n\n${GREEN}##### Key evaluation point: NSO operated SR-MPLS first, enabled SRv6, migrated all demo service intent, and left the visible transport state SRv6-only\n${NC}"

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean generated files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
