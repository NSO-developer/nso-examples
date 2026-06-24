#!/usr/bin/env bash
set -eu

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m'
NONINTERACTIVE=${NONINTERACTIVE-}
WITH_RESOURCE_MANAGER=${WITH_RESOURCE_MANAGER-}

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


printf "\n${GREEN}##### SRv6 transport migration multivendor netsim demo\n${NC}"
printf "${PURPLE}##### Walks the full reference: SRv6, RSVP-TE, TE topology, ODN, PM/assurance, DIA, L2NM, L3NM, and slices\n${NC}"
printf "${PURPLE}##### Vendor roles: IOS XR CLI shows PE/CE and core service rendering, SR policy/ODN, PM, telemetry, DIA, L2/L3 VPN, and NSS\n${NC}"
printf "${PURPLE}##### Vendor roles: IOS XR NETCONF core-2 keeps the same topology while enabling the YANG-Push variant\n${NC}"
printf "${PURPLE}##### Vendor roles: Junos shows PE SRv6, RSVP-TE, IETF TE head-end, source-routing templates, L3VPN color steering, RPM, and analytics\n${NC}"
printf "${PURPLE}##### Vendor roles: Nokia SR OS shows multivendor core transport participation, TE/SRLG topology, RSVP/MPLS/SRv6, OAM-PM/TWAMP-light, and telemetry\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
make stop clean

printf "\n${PURPLE}##### Start the simulated multivendor network and NSO, then perform the initial sync-from\n${NC}"
make all start

if [ -n "$WITH_RESOURCE_MANAGER" ]; then
    printf "\n${PURPLE}##### Initialize Resource Manager ID pools after package startup\n${NC}"
    ncs_cli -n -u admin -C << EOF
config
load merge payload/rm-sr-color-pool.xml
commit dry-run
commit
end
show running-config resource-pools id-pool | nomore
EOF
fi

printf "\n${PURPLE}##### Show the multivendor topology in use\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show running-config devices device device-type | nomore
EOF

printf "\n\n${PURPLE}##### Provision the multivendor SRv6 underlay and export an RFC 8795 TE topology view\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
core-network provision
core-network export-te-topology
commit dry-run
commit
end
show running-config networks network srv6-transport-migration-te network-types | nomore
show running-config networks network srv6-transport-migration-te node pe-01 | nomore
show running-config networks network srv6-transport-migration-te link pe-02-Gi0_0_0_0-to-core-2-Gi0_0_0_4 | nomore
EOF

printf "\n\n${PURPLE}##### Load the RSVP/MPLS-TE underlay helper so IOS XR, Junos, and SR OS participate in the Phase 1 transport estate\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/rsvp-underlay.xml
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the RSVP underlay service and rendered RSVP/MPLS config on XR, Junos, and SR OS nodes\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show core-network services rsvp-underlay default | nomore
show running-config devices device pe-02 config interface Loopback 0 | nomore
show running-config devices device pe-02 config rsvp interface Gi0/0/0/0 | nomore
show running-config devices device pe-02 config mpls traffic-eng interface Gi0/0/0/0 | nomore
show running-config devices device pe-01 config configuration protocols rsvp interface xe-0/0/0:0.0 | nomore
show running-config devices device pe-01 config configuration protocols mpls interface xe-0/0/0:0.0 | nomore
show running-config devices device core-1 config router Base rsvp interface 1/1/5 | nomore
show running-config devices device core-1 config router Base mpls interface 1/1/5 | nomore
EOF

printf "\n\n${PURPLE}##### Load the standard IETF TE RSVP payload and let the example map it into a Junos RSVP-TE LSP on pe-01\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/ietf-te-rsvp.xml
load merge payload/te-tunnel-service.xml
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the IETF TE service plus the rendered Junos RSVP-TE LSP, explicit path, and IS-IS LSP advertisement\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show te tunnels tunnel IETF-RSVP-TE | nomore
show running-config devices device pe-01 config configuration protocols mpls label-switched-path IETF-RSVP-TE | nomore
show running-config devices device pe-01 config configuration protocols mpls path IETF-RSVP-TE-PATH-1-1 | nomore
show running-config devices device pe-01 config configuration protocols isis label-switched-path IETF-RSVP-TE | nomore
EOF

printf "\n\n${PURPLE}##### Load the sample transport migration payload with SR policies and an ODN template on IOS XR and Junos head-ends\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/transport-te.xml
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Inspect the rendered IOS XR and Junos ODN/SR policy configuration on pe-02 and pe-01\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3000 | nomore
show running-config devices device pe-02 config segment-routing traffic-eng policy pe02-to-pe01 | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing | nomore
show running-config devices device pe-01 config configuration protocols bgp group RR family inet-vpn unicast | nomore
EOF

printf "\n\n${PURPLE}##### Load service assurance and transport PM profiles, then attach XR, Junos, and SR OS PM/telemetry objects where the netsim NEDs support them\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/service-assurance.xml
load merge payload/transport-pm.xml
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Inspect PM profiles, assurance profile, IOS XR PM, and multivendor telemetry renderings on XR, Junos, and SR OS\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show core-network services pm-profile transport-gold | nomore
core-network services pm-profile transport-gold self-test
show running-config core-network services assurance-profile transport-aa | nomore
show core-network services assurance-monitor transport-domain | nomore
core-network services assurance-monitor transport-domain self-test
show running-config devices device pe-02 config performance-measurement | nomore
show running-config devices device pe-02 config segment-routing traffic-eng policy pe02-to-pe01 performance-measurement | nomore
show running-config devices device pe-01 config configuration services rpm | nomore
show running-config devices device core-1 config oam-pm | nomore
show running-config devices device pe-02 config telemetry model-driven | nomore
show running-config devices device pe-01 config configuration services analytics | nomore
show running-config devices device core-1 config system telemetry | nomore
EOF

printf "\n\n${PURPLE}##### Load a DIA service with PE/CE BGP, PM attachment, and service-assurance monitor composition\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/internet-access-service.xml
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Inspect the DIA service, PE/CE routing, access PM, and telemetry subscription created for the monitor\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show core-network services internet-access tailf-dia | nomore
show running-config devices device pe-02 config interface GigabitEthernet 0/0/0/4 | nomore
show running-config devices device pe-02 config router bgp 65000 neighbor 198.51.100.2 | nomore
show running-config devices device ce-1-3 config router bgp | nomore
show running-config devices device pe-02 config telemetry model-driven | nomore
EOF

printf "\n\n${PURPLE}##### Create an L3VPN that references the tailf-odn template so exported VPN routes get the ODN color on both PEs\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
l3vpn sample-l3vpn customer Tail-f link 1 port pe-01-2
l3vpn sample-l3vpn customer Tail-f link 2 port pe-02-2
l3vpn sample-l3vpn odn-template tailf-odn
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Verify the XR route-policy/extcommunity and Junos community/resolution-map objects created for ODN steering\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show running-config devices device pe-02 config route-policy L3VPN-sample-l3vpn-ODN-EXP | nomore
show running-config devices device pe-02 config extcommunity-set opaque COLOR_3000 | nomore
show running-config devices device pe-02 config vrf L3VPN-1 | nomore
show running-config devices device pe-01 config configuration policy-options community L3VPN-sample-l3vpn-COLOR | nomore
show running-config devices device pe-01 config configuration policy-options resolution-map L3VPN-sample-l3vpn-COLOR-MAP | nomore
show running-config devices device pe-01 config configuration policy-options policy-statement L3VPN-sample-l3vpn-EXP | nomore
EOF

printf "\n\n${PURPLE}##### Load the standard IETF L2NM payload and show the composed E-Line service it creates\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/ietf-l2vpn-nm.xml
load merge payload/l2vpn-nm-service.xml
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the IETF L2NM service and the resulting EVPN VPWS configuration\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show l2vpn-ntw vpn-services vpn-service l2nm-evpn | nomore
show eline | nomore
show running-config devices device pe-02 config l2vpn | nomore
EOF

printf "\n\n${PURPLE}##### The IETF L3NM sample reuses the same PE access ports as sample-l3vpn, so remove the ODN demo VPN before loading L3NM\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
no l3vpn sample-l3vpn
commit dry-run
commit
load merge payload/ietf-l3vpn-nm.xml
load merge payload/l3vpn-nm-service.xml
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the IETF L3NM service, the composed L3VPN service, and the rendered PE/CE BGP and VRF config\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show l3vpn-ntw vpn-services vpn-service l3nm-basic | nomore
show l3vpn | nomore
show running-config devices device pe-02 config vrf | nomore
show running-config devices device pe-02 config router bgp 65000 | nomore
show running-config devices device ce-1-3 config router bgp | nomore
EOF

printf "\n\n${PURPLE}##### Free the access ports used by the previous northbound VPN demos, then load an IETF NSS slice that composes ODN, L3VPN, PM, and assurance\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
no l3vpn-ntw vpn-services vpn-service l3nm-basic
no l2vpn-ntw vpn-services vpn-service l2nm-evpn
commit dry-run
commit
load merge payload/transport-slice-service.xml
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Inspect the IETF NSS slice, composed services, PM profile, and assurance monitors\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show network-slice-services slice-service nss-gold | nomore
show l3vpn nss-nss-gold-cg-l3 | nomore
show core-network services odn-template nss-nss-gold-cg-l3-odn | nomore
show core-network services pm-profile nss-nss-gold-pm | nomore
show core-network services assurance-monitor | nomore
show running-config devices device pe-01 config configuration protocols source-packet-routing source-routing-path-template nss-nss-gold-cg-l3-odn | nomore
show running-config devices device pe-01 config configuration policy-options resolution-map L3VPN-nss-nss-gold-cg-l3-COLOR-MAP | nomore
show running-config devices device pe-02 config segment-routing traffic-eng on-demand color 3300 | nomore
EOF

printf "\n\n${PURPLE}##### The inherited and IETF northbound services can be demonstrated together in this lab, but some of them must use the access ports sequentially\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show te tunnels tunnel IETF-RSVP-TE | nomore
show core-network services sr-policy pe02-to-pe01 | nomore
show core-network services odn-template tailf-odn | nomore
show core-network services pm-profile transport-gold | nomore
show core-network services internet-access tailf-dia | nomore
show network-slice-services slice-service nss-gold | nomore
EOF

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean generated files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
