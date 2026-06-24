#!/usr/bin/env bash
set -eu

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m'
NONINTERACTIVE=${NONINTERACTIVE-}
CONFIRM_NETWORK_STATE="confirm-network-state compare write-and-service-read-set"

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

capture_compliance_report_time() {
    awk '
        {
            for (i = 1; i < NF; i++) {
                if ($i == "time" &&
                    $(i + 1) ~ /^[0-9][0-9][0-9][0-9]-/) {
                    print $(i + 1)
                    exit
                }
            }
            if (match($0, /report_[^[:space:]]+[.]txt/)) {
                timestamp = substr($0, RSTART + 7, RLENGTH - 11)
                print timestamp
                exit
            }
        }
    '
}

printf "\n${GREEN}##### Brownfield canary-gated migration demo: PM/assurance evidence controls the lifecycle\n${NC}"
printf "${PURPLE}##### Shows PM/assurance gates that replan, block unsafe migration, roll back failure, and then succeed\n${NC}"
printf "${PURPLE}##### Also demonstrates operator guardrails: strict dry-run drift detection, scoped confirm-network-state, OOB abort, XML compliance, and compliance report re-run\n${NC}"
printf "${PURPLE}##### Reset the lab so the health-aware migration story starts clean\n${NC}"
make stop clean

printf "\n${PURPLE}##### Start the multivendor netsim network and let NSO discover/sync the devices\n${NC}"
make all start

printf "\n\n${PURPLE}##### Enable operator guardrails for the brownfield migration window\n${NC}"
printf "${PURPLE}##### Strict dry-run drift detection protects reviewed changes, scoped confirm-network-state compares service-owned state, and the transport package has an OOB abort policy for critical SRv6 ODN SID-depth drift\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
session dry-run-drift-detection enabled
session dry-run-drift-detection mode strict
commit dry-run
commit
end
show running-config session dry-run-drift-detection | nomore
show compliance xml-templates template | nomore
EOF

printf "\n\n${PURPLE}##### Build the Phase 1 RSVP/MPLS-TE estate and customer slice, with no SRv6 service migration yet\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/rsvp-underlay.xml
load merge payload/ietf-te-rsvp.xml
load merge payload/te-tunnel-service.xml
load merge payload/service-assurance.xml
load merge payload/transport-resource-pools.xml
core-network export-te-topology
load merge payload/brownfield-migration.xml
commit dry-run ${CONFIRM_NETWORK_STATE}
commit ${CONFIRM_NETWORK_STATE}
end
show network-slice-services slice-service bf-gold | nomore
network-slice-services slice-service bf-gold migration-readiness
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
EOF

printf "\n\n${PURPLE}##### Enable SRv6 underlay, but keep the customer slice on RSVP-TE until health gates pass\n${NC}"
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
commit dry-run ${CONFIRM_NETWORK_STATE}
commit ${CONFIRM_NETWORK_STATE}
end
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
EOF

printf "\n\n${PURPLE}##### Inject PM evidence on the preferred primary link: slow/lossy pe-01 to core-1\n${NC}"
printf "${PURPLE}##### The planner avoids the unhealthy link and recommends the alternate PE-to-PE path instead of replaying a static command sequence\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
load merge payload/transport-health-link-degraded.xml
commit dry-run ${CONFIRM_NETWORK_STATE}
commit ${CONFIRM_NETWORK_STATE}
end
show running-config core-network services transport-health | nomore
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
EOF

printf "\n\n${PURPLE}##### Clear the link impairment and inject a failed target SRv6 pre-check canary\n${NC}"
printf "${PURPLE}##### The guarded migration refuses to commit while the target transport canary violates the SLO\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
no core-network services transport-health link pe01-core1-pm-degraded
load merge payload/transport-health-precheck-bad.xml
commit dry-run ${CONFIRM_NETWORK_STATE}
commit ${CONFIRM_NETWORK_STATE}
end
core-network services assess-transport-migration slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors
core-network services migrate-transport slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors execute true
show network-slice-services slice-service bf-gold | nomore
EOF

printf "\n\n${PURPLE}##### Replace the bad pre-check canary with a post-check failure to demonstrate rollback\n${NC}"
printf "${PURPLE}##### NSO commits the migration, detects the failed SRv6 canary after commit, and restores the previous RSVP-TE service intent\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
no core-network services transport-health service bf-gold cg-l3 srv6 pre-check
load merge payload/transport-health-postcheck-down.xml
commit dry-run ${CONFIRM_NETWORK_STATE}
commit ${CONFIRM_NETWORK_STATE}
end
core-network services migrate-transport slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors execute true
show network-slice-services slice-service bf-gold | nomore
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors
EOF

printf "\n\n${PURPLE}##### Clear the failed canary and run the migration again; this time the health gate passes\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
no core-network services transport-health service bf-gold cg-l3 srv6 post-check
commit dry-run ${CONFIRM_NETWORK_STATE}
commit ${CONFIRM_NETWORK_STATE}
end
core-network services migrate-transport slice-service bf-gold connection-group cg-l3 target-profile srv6 color-pool transport-sr-colors execute true
show network-slice-services slice-service bf-gold | nomore
show running-config core-network services transport-resource-pools sr-color-pool transport-sr-colors | nomore
core-network services plan-transport-migration slice-service bf-gold target-profile srv6 color-pool transport-sr-colors include-no-op true
network-slice-services slice-service bf-gold check-sync
EOF

printf "\n\n${PURPLE}##### Configure a scoped compliance report for the migrated PE pair\n${NC}"
printf "${PURPLE}##### The later re-run will verify only previously non-compliant devices instead of checking the full network again\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
config
compliance reports report transport-migration-ops device-check device [ pe-01 pe-02 ] current-out-of-sync true out-of-sync-diff true
commit dry-run
commit
end
show running-config compliance reports report transport-migration-ops | nomore
EOF

printf "\n\n${PURPLE}##### Simulate a critical out-of-band change directly on the live IOS XR PE\n${NC}"
printf "${PURPLE}##### The SRv6 ODN maximum SID depth is service-owned, so the transport-te OOB policy aborts the next guarded NSO transaction instead of silently accepting unsafe drift\n${NC}"
pause
ncs-netsim --dir ./nso-run/netsim cli-c pe-02 << EOF
config
segment-routing traffic-eng on-demand color 3400 maximum-sid-depth 12
commit
EOF

printf "\n\n${PURPLE}##### Try to change the ODN service under confirm-network-state; this is expected to abort because of the live SID-depth drift\n${NC}"
pause
set +e
ncs_cli -n -u admin -C << EOF
config
core-network services odn-template nss-bf-gold-cg-l3-odn maximum-sid-depth 15
commit dry-run ${CONFIRM_NETWORK_STATE}
commit ${CONFIRM_NETWORK_STATE}
exit no-confirm
EOF
OOB_ABORT_RC=$?
set -e
printf "${PURPLE}##### The Aborted messages above are expected; ncs_cli exit code was ${OOB_ABORT_RC}\n${NC}"

printf "\n\n${PURPLE}##### Run the scoped compliance report to detect the OOB drift\n${NC}"
printf "${PURPLE}##### The full report checks the two migrated PEs and records which device actually failed\n${NC}"
pause
COMPLIANCE_REPORT_OUTPUT=$(ncs_cli -n -u admin -C << EOF
compliance reports report transport-migration-ops run outformat text
EOF
)
printf "%s\n" "$COMPLIANCE_REPORT_OUTPUT"
COMPLIANCE_REPORT_TIME=$(printf "%s\n" "$COMPLIANCE_REPORT_OUTPUT" | capture_compliance_report_time)
if [ -z "$COMPLIANCE_REPORT_TIME" ]; then
    printf "${RED}##### Unable to capture compliance report timestamp\n${NC}" >&2
    exit 1
fi

printf "\n\n${PURPLE}##### Reconcile the device, then re-deploy service intent to restore the NSO-owned SRv6 ODN SID depth\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
devices device pe-02 sync-from
network-slice-services slice-service bf-gold check-sync
network-slice-services slice-service bf-gold re-deploy dry-run { outformat native }
network-slice-services slice-service bf-gold re-deploy
network-slice-services slice-service bf-gold check-sync
EOF

printf "\n\n${PURPLE}##### Re-run only the previously non-compliant compliance-report scope after repair\n${NC}"
printf "${PURPLE}##### NSO verifies the fix without rechecking the whole network or even both migrated PEs\n${NC}"
pause
COMPLIANCE_RERUN_OUTPUT=$(ncs_cli -n -u admin -C << EOF
compliance report-results report ${COMPLIANCE_REPORT_TIME} re-run outformat text
EOF
)
printf "%s\n" "$COMPLIANCE_RERUN_OUTPUT"

printf "\n\n${PURPLE}##### Run package-based XML compliance templates against the migrated SRv6 service state\n${NC}"
printf "${PURPLE}##### The checks prove the IOS XR and Junos PE artifacts expected after migration are present and still owned by NSO\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
compliance xml-templates check template-name srv6-migration-xr-postcheck device [ pe-02 ]
compliance xml-templates check template-name srv6-migration-junos-postcheck device [ pe-01 ]
EOF

printf "\n\n${GREEN}##### Key evaluation point: NSO owns the service lifecycle and uses PM/assurance evidence to plan, block, roll back, and finally migrate safely\n${NC}"

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    pause
    printf "${PURPLE}##### Stop all daemons and clean generated files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
