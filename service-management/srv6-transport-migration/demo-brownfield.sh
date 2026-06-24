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


printf "\n${GREEN}##### Brownfield transport migration demo: NSO service lifecycle evaluation path\n${NC}"
printf "${PURPLE}##### Shows RSVP/MPLS-TE today, SRv6 later, service migration, drift repair, and rollback\n${NC}"
printf "${PURPLE}##### Reset the lab so the migration story starts from a known network state\n${NC}"
make stop clean

printf "\n${PURPLE}##### Start the multivendor netsim network and let NSO discover/sync the devices\n${NC}"
make all start

printf "\n${PURPLE}##### Brownfield discovery: NSO has a multivendor inventory, packages, and synced device state before changing service intent\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show packages package * oper-status | nomore
show devices device * last-in-sync | nomore
show running-config devices device pe-01 device-type | nomore
show running-config devices device pe-02 device-type | nomore
EOF

printf "\n\n${PURPLE}##### Phase 1: adopt today's MPLS/RSVP-TE estate with no SRv6 locators anywhere\n${NC}"
printf "${PURPLE}##### NSO loads RSVP/MPLS-TE helper config, an IETF RSVP-TE tunnel, assurance inputs, and an SR color pool before any SRv6 underlay exists\n${NC}"
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
show running-config networks network srv6-transport-migration-te link pe-02-Gi0_0_0_0-to-core-2-Gi0_0_0_4 | nomore
show te tunnels tunnel IETF-RSVP-TE | nomore
show running-config devices device pe-01 config configuration protocols mpls label-switched-path IETF-RSVP-TE | nomore
show running-config devices device pe-01 config configuration protocols mpls path IETF-RSVP-TE-PATH-1-1 | nomore
show running-config devices device pe-02 config rsvp interface Gi0/0/0/0 | nomore
show running-config devices device core-1 config router Base rsvp interface 1/1/5 | nomore
show core-network services assurance-monitor transport-domain | nomore
show running-config devices device pe-02 config telemetry model-driven | nomore
show running-config devices device pe-01 config configuration services analytics | nomore
show running-config devices device core-1 config system telemetry | nomore
show running-config core-network services transport-resource-pools | nomore
core-network services assurance-monitor transport-domain self-test
EOF

printf "\n\n${PURPLE}##### Deploy the customer slice over today's transport: transport-profile rsvp-te using the adopted IETF TE tunnel id 1234\n${NC}"
printf "${PURPLE}##### The native dry-run shows exactly what NSO will change on Junos and IOS XR before the service is committed\n${NC}"
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
core-network services pm-profile nss-bf-gold-pm self-test
network-slice-services slice-service bf-gold migration-readiness
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
show running-config devices device pe-02 config router bgp 65000 vrf L3VPN-1 address-family ipv4 unicast | nomore
show running-config devices device ce-1-1 config router bgp | nomore
show running-config devices device ce-1-3 config router bgp | nomore
show core-network services assurance-monitor | nomore
show core-network services odn-template | nomore
EOF

printf "\n\n${PURPLE}##### TE manager planning: NSO computes the PE-to-PE path and refuses service migration until SRv6 underlay exists\n${NC}"
printf "${PURPLE}##### Evaluation point: NSO plans with topology, SRLG, SLO, PM, assurance, and staged-wave context before changing service intent\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
EOF

printf "\n\n${PURPLE}##### Phase 2: enable the SRv6 underlay across the PE/core transport domain, before migrating the service\n${NC}"
printf "${PURPLE}##### This is the controlled network-wide capability turn-up: SRv6 services appear for core and PE nodes, but customer intent is still RSVP-TE\n${NC}"
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

printf "\n\n${PURPLE}##### Service intent migration: change only the transport profile from RSVP-TE to SRv6, with NSO allocating the ODN color from the pool\n${NC}"
printf "${PURPLE}##### The guarded migration action executes the selected planner recommendation, produces the native dry-run, commits, and post-checks the service\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
core-network services migrate-transport slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors execute true
show network-slice-services slice-service bf-gold | nomore
show running-config core-network services transport-resource-pools sr-color-pool transport-sr-colors | nomore
show core-network services odn-template nss-bf-gold-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-gold-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration policy-options community L3VPN-nss-bf-gold-cg-l3-COLOR | nomore
show running-config devices device pe-01 config configuration policy-options resolution-map L3VPN-nss-bf-gold-cg-l3-COLOR-MAP | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3400 | nomore
show running-config devices device pe-02 config route-policy L3VPN-nss-bf-gold-cg-l3-ODN-EXP | nomore
show running-config devices device pe-02 config extcommunity-set opaque COLOR_3400 | nomore
show running-config devices device ce-1-1 config router bgp | nomore
show running-config devices device ce-1-3 config router bgp | nomore
network-slice-services slice-service bf-gold migration-readiness
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors include-no-op true
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
EOF

printf "\n\n${PURPLE}##### Service ownership: NSO can explain and verify the service lifecycle, not just replay device commands\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
network-slice-services slice-service bf-gold check-sync
network-slice-services slice-service bf-gold re-deploy dry-run { outformat native }
EOF

printf "\n\n${PURPLE}##### Simulate out-of-band operator drift by deleting one service-owned ODN color object directly on pe-02\n${NC}"
printf "${PURPLE}##### NSO device check-sync detects live drift, sync-from dry-run previews the import, then service check-sync and re-deploy repair the intent\n${NC}"
pause
printf "\n${RED}On pe-02, outside NSO:\n${NC}"
printf "config\n"
printf "no extcommunity-set opaque COLOR_3400\n"
printf "commit\n"
ncs-netsim --dir ./nso-run/netsim cli-c pe-02 << 'EOF'
config
no extcommunity-set opaque COLOR_3400
commit
EOF

ncs_cli -n -u admin -C << EOF
devices device pe-02 check-sync
devices device pe-02 sync-from dry-run
devices device pe-02 sync-from
devices device pe-02 check-sync
network-slice-services slice-service bf-gold check-sync
network-slice-services slice-service bf-gold re-deploy dry-run { outformat native }
network-slice-services slice-service bf-gold re-deploy
network-slice-services slice-service bf-gold check-sync
EOF

printf "\n\n${PURPLE}##### Roll back the transport migration by returning the service intent to transport-profile rsvp-te\n${NC}"
printf "${PURPLE}##### The VPN service remains, while the ODN/SRv6 steering artifacts are removed transactionally\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
network-slice-services slice-service bf-gold connection-group cg-l3 transport-profile rsvp-te
no network-slice-services slice-service bf-gold connection-group cg-l3 color
commit dry-run outformat native
commit
end
show network-slice-services slice-service bf-gold | nomore
show core-network services odn-template | nomore
show l3vpn nss-bf-gold-cg-l3 | nomore
network-slice-services slice-service bf-gold check-sync
EOF

printf "\n\n${GREEN}##### Key evaluation point: same service intent, controlled transport migration, native diffs, drift detection, healing, and rollback\n${NC}"

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean generated files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
