#!/bin/sh
set -eu

PURPLE='\033[0;35m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'
NONINTERACTIVE=${NONINTERACTIVE-}
NCS_IPC_PATH=${NCS_IPC_PATH:-/tmp/nso/ztp-base-config-ipc}
IOSXR_NED_ID=${IOSXR_NED_ID:-cisco-iosxr-netsim-cli-1.0}
DEVICES='xr-1 xr-2 xr-3 xr-4'
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

pause() {
    prompt=${1-}
    if [ -z "$prompt" ]; then
        prompt="${RED}##### Press ENTER to clean up or ctrl-c to keep the example running\n${NC}"
    fi
    if [ -z "$NONINTERACTIVE" ]; then
        printf '%b' "$prompt"
        read -r _
    fi
}

if [ "${CREATE_CALLBACK_APPLY:-0}" = 1 ]; then
    NSO_COMMIT_LABEL=create-callback-apply
    export NSO_COMMIT_LABEL
    APPLY_MODE='nano create() callbacks with commit label create-callback-apply'
else
    unset NSO_COMMIT_LABEL
    APPLY_MODE='pre-modification callback (default)'
fi

plan_value() {
    NCS_IPC_PATH="$NCS_IPC_PATH" ncs_cmd -o -c "mget \"$1\"" \
        2>/dev/null || true
}

wait_for_router() {
    device=$1
    plan="/brfs:base-rfs{$device}/plan"
    ready="$plan/component{brfs:base-router $device}/state{ncs:ready}/status"
    error="$plan/error-info/message"
    remaining=300

    while [ "$(plan_value "$ready")" != reached ]; do
        failure=$(plan_value "$error")
        case "$failure" in
            ''|FAILED:*) ;;
            *)
                printf '%s Day0 plan failed: %s\n' "$device" "$failure" >&2
                exit 1
                ;;
        esac
        if [ "$remaining" -eq 0 ]; then
            printf 'Timed out waiting for %s Day0 plan\n' "$device" >&2
            exit 1
        fi
        sleep 1
        remaining=$((remaining - 1))
    done
    printf "${GREEN}##### %s reached the nano plan ready state${NC}\n" "$device"
}

run_cli() {
    set +e
    output=$(ncs_cli -n -C --socket-path "$NCS_IPC_PATH" -u admin 2>&1)
    status=$?
    set -e
    printf '%s\n' "$output"
    if [ "$status" -ne 0 ] ||
       printf '%s\n' "$output" | grep -Eq '^(Aborted:|Error:|.*syntax error)'; then
        return 1
    fi
}

assert_contains() {
    output=$1
    expected=$2
    if ! printf '%s\n' "$output" | grep -Fq "$expected"; then
        printf 'Missing expected configuration: %s\n' "$expected" >&2
        exit 1
    fi
}

assert_not_contains() {
    output=$1
    unexpected=$2
    if printf '%s\n' "$output" | grep -Fq "$unexpected"; then
        printf 'Found unexpected configuration: %s\n' "$unexpected" >&2
        exit 1
    fi
}

printf "\n${PURPLE}##### ZTP base-config example${NC}\n"
printf "${PURPLE}##### RFS device configuration mode: %s${NC}\n" "$APPLY_MODE"
make stop >/dev/null 2>&1 || true
make clean all

printf "\n${PURPLE}##### Start NSO${NC}\n"
make start-nso

printf "\n${PURPLE}##### Configure shared Day0 settings and bootstrap inventory${NC}\n"
pause
run_cli <<EOF
config
services global-settings collect-forward-diff true
ssh host-key-verification none
base-settings snmpv3-auth-password AuthPassword1
base-settings snmpv3-priv-password PrivPassword1
base-settings permanent-password nso
bootstrap-inventory router xr-1 port 21022 ned-id $IOSXR_NED_ID loopback0-ipv4 198.51.100.1 loopback0-ipv6 2001:db8::1 loopback100-ipv4 203.0.113.1
bootstrap-inventory router xr-2 port 21023 ned-id $IOSXR_NED_ID loopback0-ipv4 198.51.100.2 loopback0-ipv6 2001:db8::2 loopback100-ipv4 203.0.113.2
bootstrap-inventory router xr-3 port 21024 ned-id $IOSXR_NED_ID loopback0-ipv4 198.51.100.3 loopback0-ipv6 2001:db8::3 loopback100-ipv4 203.0.113.3
bootstrap-inventory router xr-4 port 21025 ned-id $IOSXR_NED_ID loopback0-ipv4 198.51.100.4 loopback0-ipv6 2001:db8::4 loopback100-ipv4 203.0.113.4
commit
EOF

printf "\n${PURPLE}##### Start the netsim routers and trigger DHCP Option 67 ZTP${NC}\n"
pause
make start-netsim

printf "\n${PURPLE}##### Wait for all four router-initiated Day0 services${NC}\n"
for device in $DEVICES; do
    wait_for_router "$device"
done

printf "\n${PURPLE}##### Router DHCP, ZTP, and call-home logs${NC}\n"
for device in $DEVICES; do
    printf '\n--- %s ---\n' "$device"
    sed -n '1,240p' "netsim/$device/$device/logs/ztp.log"
done

ztp_log=$(cat netsim/xr-1/xr-1/logs/ztp.log)
for expected in \
    'DHCPDISCOVER interface=eth1 client-id=SIMXR0001 vendor-class=PXEClient:Arch:00009:PID:N540-SIM user-class=xr-config' \
    'DHCP server: 192.0.2.2 (eth1, isc-dhcp-server)' \
    'DHCPACK device=xr-1 dhcp-address=192.0.2.101' \
    'DHCP Option 67 (bootfile-name): http://127.0.0.1:30604/ztp/ztp-provision.py' \
    'Applied temporary DHCP state to MgmtEth0/RP0/CPU0/0' \
    'Downloaded Option 67 payload' \
    'Discovered chassis serial: SIMXR0001' \
    'Temporary DHCP address: 192.0.2.101' \
    'ZTP inventory mapping: hostname=xr-1 management-ipv4=192.0.2.201' \
    'Downloaded pre-VRF configuration: http://127.0.0.1:30604/ztp/ztp-pre.cli' \
    'Removed the temporary DHCP address before changing VRF' \
    'Downloaded ZTP configuration: http://127.0.0.1:30604/ztp/config/xr-1-ztp.cli' \
    'Reapplied DHCP addressing in the management VRF' \
    'Call-home accepted: HTTP 200'; do
    assert_contains "$ztp_log" "$expected"
done

printf "\n${PURPLE}##### Resulting stacked CFS and RFS services${NC}\n"
run_cli <<'EOF'
paginate false
show running-config base-cfs
show running-config base-rfs | display service-meta-data
show base-rfs plan
show running-config devices device | include "^device|address|port|authgroup|ned-id|admin-state"
show running-config devices authgroups group
EOF

printf "\n${PURPLE}##### Resulting IOS-XR base configuration for xr-1${NC}\n"
router_config=$(run_cli <<'EOF'
paginate false
show running-config devices device xr-1 config
EOF
)
printf '%s\n' "$router_config"

for expected in \
    'hostname xr-1' \
    'ipv4 address 192.0.2.201 255.255.255.0' \
    'interface Loopback 0' \
    'interface Loopback 100' \
    'ntp' \
    'snmp-server' \
    'grpc' \
    'logging' \
    'key chain BGP_AO_CHAIN' \
    'hw-module'; do
    assert_contains "$router_config" "$expected"
done
assert_not_contains "$router_config" 'ipv4 address dhcp'

authgroup_config=$(run_cli <<'EOF'
paginate false
show running-config devices authgroups group ztp-xr-1
EOF
)
assert_contains "$authgroup_config" 'remote-name     nso'

stack_metadata=$(run_cli <<'EOF'
paginate false
show running-config base-rfs xr-1 | display service-meta-data
EOF
)
assert_contains "$stack_metadata" "/bcfs:base-cfs[bcfs:device='xr-1']"

device_metadata=$(run_cli <<'EOF'
paginate false
show running-config devices device xr-1 config | display service-meta-data
EOF
)
if [ "${CREATE_CALLBACK_APPLY:-0}" = 1 ]; then
    assert_contains "$device_metadata" "/brfs:base-rfs[brfs:device='xr-1']"
else
    assert_not_contains "$device_metadata" "/brfs:base-rfs[brfs:device='xr-1']"
fi

printf "\n${GREEN}##### Verified four stacked base-cfs/base-rfs services and IOS-XR base config using %s${NC}\n" "$APPLY_MODE"

printf "\n${GREEN}##### Cleanup${NC}\n"
if [ -z "$NONINTERACTIVE" ]; then
    pause

    printf "${PURPLE}##### Stop NSO and the netsim routers${NC}\n"
    make stop

    printf "${PURPLE}##### Reset the example to its original files${NC}\n"
    make clean
fi

printf "${GREEN}##### Done!${NC}\n"
