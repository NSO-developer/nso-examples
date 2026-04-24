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
        prompt="${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    fi
    if [ -z "$NONINTERACTIVE" ]; then
        printf "%b" "$prompt"
        read -n 1 -s -r
    fi
}

show_compliance_report() {
    report_name="${1}"
    title="${2}"
    ncs_cli -n -u admin -C <<EOF
compliance reports report ${report_name} run \
title "${title}" outformat text
show compliance report-results | nomore
EOF
    report_file="$(
        ls -t ./nso-run/state/compliance-reports/*.txt 2>/dev/null \
        | head -n 1
    )"
    if [ -z "${report_file}" ]; then
        printf "No compliance report text file found.\n" >&2
        return 1
    fi
    cat "${report_file}"
}

drift_ios_banner() {
    printf "\n${RED}On lon-br1:\n${NC}"
    printf "config\n"
    printf "banner login \"Drifted banner outside NSO.\"\n"
    printf "commit\n"
    ncs-netsim --dir "./nso-run/netsim" cli-c lon-br1 <<'EOF'
config
banner login "Drifted banner outside NSO."
commit
EOF
}

drift_junos_message() {
    printf "\n${RED}On lon-tr1:\n${NC}"
    printf "config\n"
    printf "configuration system login message "
    printf "\"Drifted message outside NSO.\"\n"
    printf "commit\n"
    ncs-netsim --dir "./nso-run/netsim" cli-c lon-tr1 <<'EOF'
config
configuration system login message "Drifted message outside NSO."
commit
EOF
}

oob_ios_changes() {
    printf "\n${RED}On lon-core1:\n${NC}"
    printf "config\n"
    printf "interface GigabitEthernet 0/2\n"
    printf "description Emergency direct core path\n"
    printf "exit\n"
    printf "interface Loopback 0\n"
    printf "ip address 10.255.255.1 255.255.255.255\n"
    printf "exit\n"
    printf "commit\n"
    ncs-netsim --dir "./nso-run/netsim" cli-c lon-core1 <<'EOF'
config
interface GigabitEthernet 0/2
description Emergency direct core path
exit
interface Loopback 0
ip address 10.255.255.1 255.255.255.255
exit
commit
EOF
}

printf "\n${GREEN}##### Enterprise fabric NSO demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
make stop clean

printf "\n${PURPLE}##### Build the packages, create the multivendor lab, "
printf "start netsim and NSO, and sync the devices\n${NC}"
make all start

printf "\n${PURPLE}##### Inspect the brownfield catalog, device sync state, "
printf "the loaded out-of-band policy, and the compliance report "
printf "definition\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
show devices device * last-in-sync | nomore
show running-config enterprise-fabric-catalog | nomore
show running-config devices global-settings confirm-network-state | nomore
show running-config services out-of-band policy enterprise-fabric-service \
| nomore
show running-config compliance reports report enterprise-fabric-audit \
| nomore
EOF

printf "\n\n${PURPLE}##### Create the enterprise fabric intent with an "
printf "explicit management profile and preview the full rendering\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
config
enterprise-fabric corp-lab ntp-server [ ntp-a ntp-b ]
enterprise-fabric corp-lab management-profile domain-name corp.example
enterprise-fabric corp-lab management-profile dns-server \
[ 192.0.2.53 192.0.2.54 ]
enterprise-fabric corp-lab management-profile syslog-server 192.0.2.200
enterprise-fabric corp-lab management-profile syslog-severity informational
commit dry-run
commit
EOF

ncs_cli -n -u admin -C <<'EOF'
show running-config enterprise-fabric corp-lab | nomore
show running-config devices device lon-core1 config ip domain | nomore
show running-config devices device lon-core1 config logging | nomore
show running-config devices device lon-core1 config archive | nomore
show running-config devices device lon-core1 config ntp | nomore
show running-config devices device lon-core1 config router ospf | nomore
show running-config devices device lon-core1 config router bgp | nomore
show running-config devices device lon-core1 config interface GigabitEthernet \
0/2 | nomore
show running-config devices device lon-core1 config interface GigabitEthernet \
0/3 | nomore
show running-config devices device lon-tr1 config configuration system \
| display xml | nomore
show running-config devices device lon-tr1 config configuration protocols \
| display xml | nomore
EOF

printf "\n\n${PURPLE}##### Preview a Day-2 banner update, apply it, inspect "
printf "rollback history, and revert the change\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
config
enterprise-fabric corp-lab banner \
"Authorized access only. Managed by NSO change window."
commit dry-run
commit
show configuration commit list | nomore
rollback-files apply-rollback-file id 0
commit
EOF

ncs_cli -n -u admin -C <<'EOF'
show running-config enterprise-fabric corp-lab | nomore
EOF

printf "\n\n${PURPLE}##### Introduce configuration drift on IOS and Junos "
printf "outside NSO by changing service-owned banners/messages\n${NC}"
pause
drift_ios_banner
drift_junos_message

printf "\n${PURPLE}##### Detect the drift from NSO, inspect the deltas, and "
printf "preview the corrective redeploy\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
show devices device lon-br1 last-in-sync | nomore
show devices device lon-tr1 last-in-sync | nomore
devices device lon-br1 check-sync
devices device lon-tr1 check-sync
devices device lon-br1 sync-from dry-run | nomore
devices device lon-tr1 sync-from dry-run | nomore
EOF

printf "\n\n${PURPLE}##### Run a text compliance report to show the service and "
printf "devices as non-compliant\n${NC}"
pause
show_compliance_report \
    enterprise-fabric-audit \
    "Enterprise fabric drift audit"

printf "\n${PURPLE}##### Sync the drift into NSO, re-deploy the intent, "
printf "and verify that both devices return to policy\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
devices device lon-br1 sync-from
devices device lon-tr1 sync-from
enterprise-fabric corp-lab re-deploy dry-run
enterprise-fabric corp-lab re-deploy
devices device lon-br1 compare-config | nomore
devices device lon-tr1 compare-config | nomore
show devices device lon-br1 last-in-sync | nomore
show devices device lon-tr1 last-in-sync | nomore
EOF

printf "\n\n${PURPLE}##### Re-run the text compliance report after remediation"
printf "\n${NC}"
pause
show_compliance_report \
    enterprise-fabric-audit \
    "Enterprise fabric post-remediation audit"

printf "\n${PURPLE}##### Introduce IOS out-of-band changes that the "
printf "service policy will merge and reject\n${NC}"
pause
oob_ios_changes

printf "\n${PURPLE}##### Process the IOS out-of-band changes with "
printf "confirm-network-state enabled\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
show devices device lon-core1 last-in-sync | nomore
devices device lon-core1 check-sync
devices device lon-core1 compare-config | nomore
devices device lon-core1 sync-from
EOF

printf "\n\n${PURPLE}##### Run the text compliance report after the out-of-band "
printf "change is ingested\n${NC}"
pause
show_compliance_report \
    enterprise-fabric-audit \
    "Enterprise fabric OOB audit"

printf "\n${PURPLE}##### Inspect the accepted interface description, the "
printf "restored loopback address, and the clean sync state\n${NC}"
pause
ncs_cli -n -u admin -C <<'EOF'
enterprise-fabric corp-lab re-deploy dry-run
enterprise-fabric corp-lab re-deploy
EOF

ncs_cli -n -u admin -C <<'EOF'
show running-config devices device lon-core1 config interface GigabitEthernet \
0/2 | nomore
show running-config devices device lon-core1 config interface Loopback 0 \
| nomore
devices device lon-core1 compare-config | nomore
show devices device lon-core1 last-in-sync | nomore
EOF

printf "\n\n${PURPLE}##### Re-run the text compliance report after the policy-"
printf "driven repair\n${NC}"
pause
show_compliance_report \
    enterprise-fabric-audit \
    "Enterprise fabric post-OOB audit"

printf "\n\n${PURPLE}##### Show out-of-band policy log\n${NC}"
cat ./nso-run/logs/out-of-band-policy.log

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
