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

printf "\n${GREEN}##### Full brownfield migration demo: RSVP/MPLS-TE estate to SRv6-only final state\n${NC}"
printf "${PURPLE}##### Runs RSVP/MPLS-TE to SRv6 end-to-end and retires RSVP scaffolding for an SRv6-only final state\n${NC}"
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

printf "\n\n${PURPLE}##### Phase 1: operate today's RSVP/MPLS-TE estate with no SRv6 locators anywhere\n${NC}"
printf "${PURPLE}##### NSO adopts the RSVP/MPLS-TE underlay, an IETF TE tunnel, PM/assurance inputs, and an SR color pool\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/rsvp-underlay.xml
load merge payload/ietf-te-rsvp.xml
load merge payload/te-tunnel-service.xml
load merge payload/service-assurance.xml
load merge payload/transport-resource-pools.xml
core-network export-te-topology
commit dry-run
commit
end
show running-config core-network services srv6-node | nomore
show running-config networks network srv6-transport-migration-te network-types | nomore
show running-config networks network srv6-transport-migration-te node pe-01 | nomore
show te tunnels tunnel IETF-RSVP-TE | nomore
show running-config devices device pe-01 config configuration protocols mpls label-switched-path IETF-RSVP-TE | nomore
show running-config devices device pe-02 config rsvp interface Gi0/0/0/0 | nomore
show running-config devices device core-1 config router Base rsvp interface 1/1/5 | nomore
show core-network services assurance-monitor transport-domain | nomore
show running-config core-network services transport-resource-pools | nomore
core-network services assurance-monitor transport-domain self-test
EOF

printf "\n\n${PURPLE}##### Deploy the customer slice over today's transport: transport-profile rsvp-te tunnel id 1234\n${NC}"
printf "${PURPLE}##### The native dry-run shows the managed CE-to-PE L3VPN while transport remains RSVP/MPLS-TE\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/brownfield-migration.xml
commit dry-run outformat native
commit
end
show network-slice-services slice-service bf-gold | nomore
show l3vpn nss-bf-gold-cg-l3 | nomore
show running-config core-network services pm-profile nss-bf-gold-pm | nomore
network-slice-services slice-service bf-gold migration-readiness
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
show core-network services odn-template | nomore
EOF

printf "\n\n${PURPLE}##### Phase 2: enable SRv6 underlay across the PE/core transport domain before service migration\n${NC}"
printf "${PURPLE}##### This turns on network-wide SRv6 capability while the customer service is still RSVP/MPLS-TE\n${NC}"
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
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
EOF

printf "\n\n${PURPLE}##### Full service migration: move the brownfield slice from RSVP/MPLS-TE to SRv6\n${NC}"
printf "${PURPLE}##### NSO allocates the SR color, commits the service-intent change, and verifies the SRv6 ODN rendering\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
core-network services migrate-transport slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors execute true
show network-slice-services slice-service bf-gold | nomore
show l3vpn nss-bf-gold-cg-l3 | nomore
show running-config core-network services transport-resource-pools sr-color-pool transport-sr-colors | nomore
show core-network services odn-template nss-bf-gold-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-gold-cg-l3-odn | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3400 | nomore
network-slice-services slice-service bf-gold migration-readiness
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors include-no-op true
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
network-slice-services slice-service bf-gold check-sync
EOF

printf "\n\n${PURPLE}##### Retire legacy RSVP/MPLS-TE scaffolding now that all demo service intent is SRv6\n${NC}"
printf "${PURPLE}##### NSO removes the IETF RSVP-TE tunnel and the local RSVP underlay helper, leaving SRv6 as the visible transport state\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
no te-tunnel-services tunnel-service IETF-RSVP-TE
no te tunnels tunnel IETF-RSVP-TE
no core-network services rsvp-underlay default
commit dry-run outformat native
commit
end
show network-slice-services slice-service bf-gold | nomore
show core-network services odn-template nss-bf-gold-cg-l3-odn | nomore
show running-config core-network services srv6-node | nomore
show running-config core-network services rsvp-underlay | nomore
show running-config te tunnels | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-gold-cg-l3-odn | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3400 | nomore
network-slice-services slice-service bf-gold check-sync
EOF

printf "\n\n${GREEN}##### Key evaluation point: NSO adopted RSVP/MPLS-TE, enabled SRv6, migrated the service, and retired legacy scaffolding so the final demo state is SRv6-only\n${NC}"

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean generated files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
