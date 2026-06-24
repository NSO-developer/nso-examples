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


printf "\n${GREEN}##### Brownfield SR-MPLS to SRv6 migration demo: NSO service lifecycle evaluation path\n${NC}"
printf "${PURPLE}##### Shows SR-MPLS today, SRv6 later, service migration, drift repair, and rollback\n${NC}"
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

printf "\n\n${PURPLE}##### Phase 1: operate today's SR-MPLS estate with no SRv6 locators anywhere\n${NC}"
printf "${PURPLE}##### The first slice commit will use SR-MPLS ODN color steering and assurance inputs before any SRv6 underlay exists\n${NC}"
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
show running-config networks network srv6-transport-migration-te link pe-02-Gi0_0_0_0-to-core-2-Gi0_0_0_4 | nomore
show core-network services assurance-monitor transport-domain | nomore
show running-config devices device pe-02 config telemetry model-driven | nomore
show running-config devices device pe-01 config configuration services analytics | nomore
show running-config devices device core-1 config system telemetry | nomore
core-network services assurance-monitor transport-domain self-test
show core-network services odn-template | nomore
EOF

printf "\n\n${PURPLE}##### Deploy the customer slice over today's SR-MPLS transport: transport-profile sr-mpls color 3300\n${NC}"
printf "${PURPLE}##### The native dry-run shows ODN color steering and VPN color tagging before the service is committed\n${NC}"
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
show running-config devices device pe-01 config configuration policy-options community L3VPN-nss-bf-srmpls-cg-l3-COLOR | nomore
show running-config devices device pe-01 config configuration policy-options resolution-map L3VPN-nss-bf-srmpls-cg-l3-COLOR-MAP | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 | nomore
show running-config devices device pe-02 config router bgp 65000 vrf L3VPN-1 address-family ipv4 unicast | nomore
show running-config devices device ce-1-1 config router bgp | nomore
show running-config devices device ce-1-3 config router bgp | nomore
show core-network services assurance-monitor | nomore
show running-config core-network services pm-profile nss-bf-srmpls-pm | nomore
core-network services pm-profile nss-bf-srmpls-pm self-test
network-slice-services slice-service bf-srmpls migration-readiness
core-network services plan-transport-migration slice-service bf-srmpls target-profile srv6
core-network services assess-transport-migration slice-service bf-srmpls connection-group cg-l3 target-profile srv6
EOF

printf "\n\n${PURPLE}##### Phase 2: enable the SRv6 underlay across the PE/core transport domain, before migrating the service\n${NC}"
printf "${PURPLE}##### NSO turns up SRv6 node services for selected transport nodes while the customer slice still runs as SR-MPLS\n${NC}"
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

printf "\n\n${PURPLE}##### Service intent migration: change only the service transport intent from SR-MPLS to SRv6\n${NC}"
printf "${PURPLE}##### The guarded migration action executes the selected planner recommendation, keeps color 3300, dry-runs, commits, and post-checks the service\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
core-network services migrate-transport slice-service bf-srmpls connection-group cg-l3 target-profile srv6 execute true
show network-slice-services slice-service bf-srmpls | nomore
show core-network services odn-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration policy-options resolution-map L3VPN-nss-bf-srmpls-cg-l3-COLOR-MAP | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 | nomore
show running-config devices device pe-02 config router bgp 65000 vrf L3VPN-1 address-family ipv4 unicast | nomore
show running-config devices device ce-1-1 config router bgp | nomore
show running-config devices device ce-1-3 config router bgp | nomore
network-slice-services slice-service bf-srmpls migration-readiness
core-network services plan-transport-migration slice-service bf-srmpls target-profile srv6 include-no-op true
core-network services assess-transport-migration slice-service bf-srmpls connection-group cg-l3 target-profile srv6
EOF

printf "\n\n${PURPLE}##### Service ownership: NSO can explain and verify the service lifecycle, not just replay device commands\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
network-slice-services slice-service bf-srmpls check-sync
network-slice-services slice-service bf-srmpls re-deploy dry-run { outformat native }
EOF

printf "\n\n${PURPLE}##### Simulate out-of-band operator drift by deleting the service-owned SRv6 ODN subtree directly on pe-02\n${NC}"
printf "${PURPLE}##### NSO device check-sync detects live drift, sync-from dry-run previews the import, then service check-sync and re-deploy repair the intent\n${NC}"
pause
printf "\n${RED}On pe-02, outside NSO:\n${NC}"
printf "config\n"
printf "no segment-routing traffic-eng on-demand color 3300 srv6\n"
printf "commit\n"
ncs-netsim --dir ./nso-run/netsim cli-c pe-02 << 'EOF'
config
no segment-routing traffic-eng on-demand color 3300 srv6
commit
EOF

ncs_cli -n -u admin -C << EOF
devices device pe-02 check-sync
devices device pe-02 sync-from dry-run
devices device pe-02 sync-from
devices device pe-02 check-sync
network-slice-services slice-service bf-srmpls check-sync
network-slice-services slice-service bf-srmpls re-deploy dry-run { outformat native }
network-slice-services slice-service bf-srmpls re-deploy
network-slice-services slice-service bf-srmpls check-sync
EOF

printf "\n\n${PURPLE}##### Roll back the transport migration by returning the service intent to transport-profile sr-mpls\n${NC}"
printf "${PURPLE}##### The VPN service and SR-MPLS color steering remain, while only SRv6 artifacts are removed transactionally\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
network-slice-services slice-service bf-srmpls connection-group cg-l3 transport-profile sr-mpls
commit dry-run outformat native
commit
end
show network-slice-services slice-service bf-srmpls | nomore
show core-network services odn-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-bf-srmpls-cg-l3-odn | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 | nomore
show running-config devices device pe-02 config router bgp 65000 vrf L3VPN-1 address-family ipv4 unicast | nomore
network-slice-services slice-service bf-srmpls check-sync
EOF

printf "\n\n${GREEN}##### Key evaluation point: the same slice can run on SR-MPLS today and move to SRv6 later through service intent\n${NC}"

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean generated files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
