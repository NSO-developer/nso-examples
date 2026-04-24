#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m'
NONINTERACTIVE="${NONINTERACTIVE-}"

pause() {
    prompt="${1-}"
    if [ -z "$prompt" ]; then
        prompt="${RED}##### Press any key to continue or ctrl-c to exit
${NC}"
    fi
    if [ -z "$NONINTERACTIVE" ]; then
        printf "%b" "$prompt"
        read -n 1 -s -r
    fi
}

drift_eos_maximum_routes() {
    printf "\n${RED}On eos-leaf-1:\n${NC}"
    printf "config\nrouter bgp 65010\nvrf ACME-101\nneighbor 172.16.102.2 maximum-routes 5\ncommit\n"
    ncs-netsim --dir "./nso-run/netsim" cli-c eos-leaf-1 <<'EOF'
config
router bgp 65010
vrf ACME-101
neighbor 172.16.102.2 maximum-routes 5
commit
EOF
}

drift_xr_maximum_prefix() {
    printf "\n${RED}On xr-pe-1:\n${NC}"
    printf "config\nrouter bgp 65010\nvrf ACME-101\nneighbor 172.16.101.2\naddress-family ipv4 unicast\nmaximum-prefix 5\ncommit\n"
    ncs-netsim --dir "./nso-run/netsim" cli-c xr-pe-1 <<'EOF'
config
router bgp 65010
vrf ACME-101
neighbor 172.16.101.2
address-family ipv4 unicast
maximum-prefix 5
commit
EOF
}

oob_xr_changes() {
    printf "\n${RED}On xr-pe-1:\n${NC}"
    printf "config\ninterface GigabitEthernet 0/0/0/1\nmtu 1600\nipv4 address 172.16.101.9 255.255.255.252\nexit\nprefix-set TE-101-PS\n10.101.0.0/16\n10.101.10.0/24\n10.101.99.0/24\nend-set\ncommit\n"
    ncs-netsim --dir "./nso-run/netsim" cli-c xr-pe-1 <<'EOF'
config
interface GigabitEthernet 0/0/0/1
mtu 1600
ipv4 address 172.16.101.9 255.255.255.252
exit
prefix-set TE-101-PS
10.101.0.0/16
10.101.10.0/24
10.101.99.0/24
end-set
commit
EOF
}

printf "\n${GREEN}##### Tenant edge closed-loop multivendor demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
make stop clean

printf "\n${PURPLE}##### Build the packages, create the two-device lab, start netsim and NSO, and sync the devices\n${NC}"
make all start

printf "\n${PURPLE}##### Verify the lab state, inspect the brownfield site catalog, and review the loaded out-of-band policy\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
show devices device * last-in-sync | nomore
show packages package tenant-edge-service | nomore
show running-config tenant-edge-catalog | nomore
show running-config devices global-settings confirm-network-state | nomore
show running-config services out-of-band policy tenant-edge-service | nomore
EOF

printf "\n\n${PURPLE}##### Define the tenant intent, preview the multivendor rendering, and deploy it to IOS-XR and EOS\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
config
tenant-edge acme-blue customer ACME tenant-id 101 local-as 65010 vrf-name ACME-101 max-prefix 50 export-prefix [ 10.101.0.0/16 10.101.10.0/24 ] site [ xr-site eos-site ]
commit dry-run
commit
EOF

ncs_cli -n -u admin -C <<'EOF'
show running-config tenant-edge acme-blue | nomore
EOF

printf "\n\n${PURPLE}##### Inspect the resulting IOS-XR and EOS service modifications and rendered config subtrees\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
config
tenant-edge acme-blue get-modifications outformat xml | nomore
EOF

ncs_cli -n -u admin -C <<'EOF'
show running-config devices device xr-pe-1 config vrf | nomore
show running-config devices device xr-pe-1 config router bgp | nomore
show running-config devices device eos-leaf-1 config vrf | nomore
show running-config devices device eos-leaf-1 config router bgp | nomore
EOF

printf "\n\n${PURPLE}##### Preview a Day-2 update, apply it, inspect the rollback history, and undo the change\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
config
tenant-edge acme-blue max-prefix 75
tenant-edge acme-blue export-prefix 10.101.20.0/24
commit dry-run
commit
EOF

ncs_cli -n -u admin -C <<'EOF'
show configuration commit list | nomore
EOF

ncs_cli -n -u admin -C <<'EOF'
config
rollback-files apply-rollback-file id 0
commit
EOF

ncs_cli -n -u admin -C <<'EOF'
show running-config tenant-edge acme-blue | nomore
EOF

printf "\n\n${PURPLE}##### Introduce plain multivendor drift by tightening the BGP prefix limits on EOS and IOS-XR\n${NC}"
pause
drift_eos_maximum_routes
drift_xr_maximum_prefix

printf "\n${PURPLE}##### Detect the drift from NSO and inspect the delta against the intended service state\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
show devices device eos-leaf-1 last-in-sync | nomore
show devices device xr-pe-1 last-in-sync | nomore
devices device eos-leaf-1 check-sync
devices device xr-pe-1 check-sync
devices device eos-leaf-1 sync-from dry-run | nomore
devices device xr-pe-1 sync-from dry-run | nomore
EOF

printf "\n\n${PURPLE}##### Sync the drift into NSO, preview the corrective redeploy, then remediate safely back to intent\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
devices sync-from
tenant-edge acme-blue re-deploy dry-run
tenant-edge acme-blue re-deploy
devices device eos-leaf-1 compare-config | nomore
devices device xr-pe-1 compare-config | nomore
show devices device eos-leaf-1 last-in-sync | nomore
show devices device xr-pe-1 last-in-sync | nomore
EOF

printf "\n\n${PURPLE}##### Introduce out-of-band IOS-XR changes that the service policy will merge, reject, and manage\n${NC}"
pause
oob_xr_changes

printf "\n${PURPLE}##### Process the out-of-band changes with XR sync-from, following the confirm-network-state policy\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
show devices device xr-pe-1 last-in-sync | nomore
devices device xr-pe-1 check-sync
devices device xr-pe-1 compare-config | nomore
devices device xr-pe-1 sync-from
EOF

printf "\n\n${PURPLE}##### Inspect how NSO accepted the MTU, rejected the IP change, preserved the extra prefix-set entry, and logged the policy matches\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
show running-config devices device xr-pe-1 config interface GigabitEthernet 0/0/0/1 | nomore
show running-config devices device xr-pe-1 config prefix-set TE-101-PS | nomore
devices device xr-pe-1 compare-config | nomore
show devices device xr-pe-1 last-in-sync | nomore
EOF

ncs_cli -n -u admin -C <<'EOF'
tenant-edge acme-blue re-deploy dry-run
EOF

printf "\n\n${PURPLE}##### Show out-of-band policy log\n${NC}"
cat ./nso-run/logs/out-of-band-policy.log

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
