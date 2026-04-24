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

function url_open {
    if [ -z "$NONINTERACTIVE" ]; then
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
            xdg-open $1
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            open $1
        else
            print "\nOpen $1 in your browser\n"
        fi
    fi
}

printf "\n${GREEN}##### Compliance reporting demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the example\n${NC}"
pause
printf "${PURPLE}##### Setup the environment\n${NC}"
make all

printf "\n\n${PURPLE}##### Start the simulated network and NSO\n${NC}"
make start

printf "\n\n${PURPLE}##### Sync the configuration from all network devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Deploy services\n${NC}"
ncs_cli -n -u admin -C << EOF
vpn l3vpn * re-deploy
EOF

printf "\n\n${PURPLE}##### Add QOS to the VPN customers\n${NC}"
ncs_cli -n -u admin -C << EOF
config
vpn l3vpn volvo qos qos-policy SILVER
vpn l3vpn ford qos qos-policy BRONZE
commit no-deploy
EOF

printf "\n\n${GREEN}##### Configure a compliance report\n${NC}"
printf "${PURPLE}##### Configure simple device and service checks\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
compliance reports report Compliance-Audit device-check device-group [ P PE ] current-out-of-sync true
compliance reports report Compliance-Audit service-check all-services current-out-of-sync true
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Run the configured compliance report\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
compliance reports report Compliance-Audit run
EOF

printf "\n\n${PURPLE}##### Log in to the web UI\n${NC}"
pause
url_open http://localhost:8080/webui-one/Compliance/ReportResults

printf "\n\n${PURPLE}##### View the compliance report results in the web UI\n${NC}"
pause
url_open http://localhost:8080/webui-one/Compliance/ReportResults

printf "\n\n${PURPLE}##### Re-deploy the non-compliance services\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
vpn l3vpn * re-deploy
EOF

printf "\n\n${PURPLE}##### Re-run the configured compliance report\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
compliance reports report Compliance-Audit run
EOF

printf "\n\n${PURPLE}##### View the new resuls in the web UI\n${NC}"
pause
url_open http://localhost:8080/webui-one/Compliance/ReportResults

printf "\n\n${GREEN}##### Add compliance templates\n${NC}"
printf "${PURPLE}##### Load the pre-defined compliance templates\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge templates/acl_deny_options.xml
load merge templates/disable_propagate_ttl.xml
load merge templates/interface_unreachables.xml
load merge templates/line_console_strict.xml
load merge templates/service_encrypt.xml
load merge templates/service_small_servers.xml
load merge templates/timezone.xml
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Check a compliance template without running the report\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
compliance template timezone check device-group PE variable { name TIMEZONE value EST } variable { name OFFSET_HOURS value -6 } variable { name OFFSET_MINUTES value 0 }
EOF

printf "\n\n${PURPLE}##### Add the templates to the report\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
compliance reports report Compliance-Audit device-check template acl_deny_options
variable IPV4_PROTECT value filter_traffic
exit
compliance reports report Compliance-Audit device-check template disable_propagate_ttl
compliance reports report Compliance-Audit device-check template interface_unreachables
compliance reports report Compliance-Audit device-check template line_console_strict
variable AUTH_NAME value default
exit
compliance reports report Compliance-Audit device-check template service_encrypt
compliance reports report Compliance-Audit device-check template service_small_servers
compliance reports report Compliance-Audit device-check template timezone
variable TIMEZONE value EST
exit
variable OFFSET_HOURS value -5
exit
variable OFFSET_MINUTES value 0
top
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Re-run the configured compliance report\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
compliance reports report Compliance-Audit run
EOF

printf "\n\n${PURPLE}##### View the new resuls in the web UI\n${NC}"
pause
url_open http://localhost:8080/webui-one/Compliance/ReportResults

printf "\n\n${PURPLE}##### Fix the non-compliant configuration\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
devices device p0..3 config cisco-ios-xr:clock timezone EST -5 0
top
devices device pe0..1 config cisco-ios-xr:clock timezone EST -5 0
top
devices device p0..3 config cisco-ios-xr:mpls ip-ttl-propagate disable
top
devices device pe0..1 config cisco-ios-xr:mpls ip-ttl-propagate disable
top
commit dry-run outformat native
commit
EOF

printf "\n\n${PURPLE}##### Re-run the configured compliance report\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
compliance reports report Compliance-Audit run
EOF

printf "\n\n${PURPLE}##### View the new resuls in the web UI\n${NC}"
pause
url_open http://localhost:8080/webui-one/Compliance/ReportResults

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    pause

    printf "${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    make stop

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    pause
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
