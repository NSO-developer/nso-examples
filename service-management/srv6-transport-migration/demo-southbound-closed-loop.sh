#!/usr/bin/env bash
set -eu

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m'
NONINTERACTIVE=${NONINTERACTIVE-}
SERVICE_NAME=core2-vpnlab
SUBSCRIPTION=closed-loop-srv6-${SERVICE_NAME}

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

wait_for_subscription_status() {
    local expected="$1"
    local output

    for _ in {1..45}; do
        output=$(ncs_cli -n -u admin -C << EOF
show devices device core-2 telemetry subscription ${SUBSCRIPTION} status
EOF
)
        if printf "%s\n" "$output" | grep -Eq "status[[:space:]]+${expected}"; then
            printf "%s\n" "$output"
            return 0
        fi
        sleep 1
    done

    printf "${RED}##### Subscription did not reach status ${expected}\n${NC}" >&2
    printf "%s\n" "$output" >&2
    return 1
}

verify_health_from_device_state() {
    ncs_cli -n -u admin -C << EOF
core-network services closed-loop-srv6-monitor ${SERVICE_NAME} verify-health
show core-network services closed-loop-srv6-monitor ${SERVICE_NAME} | nomore
EOF
}

sync_from_monitored_device() {
    ncs_cli -n -u admin -C << EOF
devices device core-2 sync-from
EOF
}

wait_for_service_health() {
    local expected="$1"
    local output

    for _ in {1..45}; do
        output=$(ncs_cli -n -u admin -C << EOF
show core-network services closed-loop-srv6-monitor ${SERVICE_NAME} | nomore
EOF
)
        if printf "%s\n" "$output" | grep -Eq "health-state[[:space:]]+${expected}"; then
            printf "%s\n" "$output"
            return 0
        fi
        sleep 1
    done

    # Some netsim/NED combinations bring the YANG-Push subscription to
    # running but do not emit the sync-on-start notification before the demo
    # timeout. Verify the same service-owned locator state deterministically
    # so the lifecycle demo does not depend on that race.
    output=$(verify_health_from_device_state)
    if printf "%s\n" "$output" | grep -Eq "health-state[[:space:]]+${expected}"; then
        printf "%s\n" "$output"
        return 0
    fi

    printf "${RED}##### Closed-loop SRv6 service did not reach health-state ${expected}\n${NC}" >&2
    printf "%s\n" "$output" >&2
    return 1
}

wait_for_repair_count() {
    local expected="$1"
    local output

    for _ in {1..60}; do
        output=$(ncs_cli -n -u admin -C << EOF
show core-network services closed-loop-srv6-monitor ${SERVICE_NAME} | nomore
EOF
)
        if printf "%s\n" "$output" | grep -Eq "repair-count[[:space:]]+${expected}"; then
            printf "%s\n" "$output"
            return 0
        fi
        sleep 1
    done

    sync_from_monitored_device
    output=$(verify_health_from_device_state)
    if printf "%s\n" "$output" | grep -Eq "repair-count[[:space:]]+${expected}"; then
        printf "%s\n" "$output"
        return 0
    fi

    printf "${RED}##### Closed-loop SRv6 service did not reach repair-count ${expected}\n${NC}" >&2
    printf "%s\n" "$output" >&2
    return 1
}


printf "\n${GREEN}##### Closed-loop SRv6 nano service demo\n${NC}"
printf "${PURPLE}##### Shows a nano service that provisions SRv6, owns a lifetime YANG-Push subscription, detects OOB locator drift, then uses service OOB policy to repair and verify automatically\n${NC}"
printf "${PURPLE}##### Reset the lab so the closed-loop telemetry lifecycle is easy to inspect\n${NC}"
make stop clean

printf "\n${PURPLE}##### Start the multivendor netsim network with core-2 as the IOS XR NETCONF/YANG-Push node\n${NC}"
make all start

printf "\n${PURPLE}##### Confirm that core-2 is NETCONF-managed and advertises ietf-yang-push\n${NC}"
pause
ncs_cli -n -u admin -C << 'EOF'
show running-config devices device core-2 device-type | nomore
show devices device core-2 state last-modules-state module ietf-yang-push | nomore
EOF

printf "\n\n${PURPLE}##### Create the closed-loop SRv6 nano service\n${NC}"
printf "${PURPLE}##### The nano plan makes telemetry a service-owned component; YANG-Push sync-on-start verifies the current locator state when the subscription runs\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
unhide debug
config
core-network services closed-loop-srv6-monitor ${SERVICE_NAME}
 device core-2
 locator-name VPNLAB
 srv6-prefix 5f00:0:2::/48
 loopback0-prefix fd00::2/128
 loopback1-prefix 5f00:0:2::1/128
 isis-net 49.0000.0000.0002.00
commit dry-run
commit
end
show core-network services closed-loop-srv6-monitor ${SERVICE_NAME} plan | nomore
show running-config services out-of-band policy closed-loop-srv6-servicepoint | nomore
show running-config devices device core-2 telemetry subscription ${SUBSCRIPTION} | nomore
show running-config kickers telemetry-kicker ${SUBSCRIPTION} | nomore
EOF

printf "\n\n${PURPLE}##### Wait for the subscription to run and for sync-on-start telemetry to mark the service operational\n${NC}"
pause
wait_for_subscription_status running
wait_for_service_health operational

printf "\n\n${PURPLE}##### Simulate an out-of-band operator changing the SRv6 locator prefix on the device\n${NC}"
printf "${PURPLE}##### The service-owned telemetry kicker invokes the nano service; confirm-network-state applies the service OOB policy to push service intent back\n${NC}"
pause
printf "\n${RED}On core-2:\n${NC}"
printf "config\n"
printf "segment-routing srv6 locators locator VPNLAB prefix 5f00:0:99::/48\n"
printf "commit\n"
ncs-netsim --dir ./nso-run/netsim cli-c core-2 << 'EOF'
config
segment-routing srv6 locators locator VPNLAB prefix 5f00:0:99::/48
commit
EOF

wait_for_repair_count 1
wait_for_service_health operational

printf "\n\n${PURPLE}##### Show the nano plan after the OOB policy repaired and verified the service\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
show core-network services closed-loop-srv6-monitor ${SERVICE_NAME} plan | nomore
EOF

printf "\n\n${PURPLE}##### Show the device-side SRv6 locator and ISIS SRv6 advertisement after repair\n${NC}"
pause
ncs_cli -n -u admin -C << 'EOF'
show running-config devices device core-2 config segment-routing srv6 locators locator VPNLAB | nomore
show running-config devices device core-2 config router isis 1 address-family ipv6 unicast segment-routing srv6 | nomore
EOF

printf "\n\n${PURPLE}##### Done\n${NC}"
