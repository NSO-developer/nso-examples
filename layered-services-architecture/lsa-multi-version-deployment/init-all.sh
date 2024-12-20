#!/bin/sh

set -e

if [ -n "$NCS_IPC_PATH" ]; then
ENV="--socket-path ${NCS_IPC_PATH}."
else
ENV="--port "
fi

echo "Initialize NSO nodes:"

echo "On lower-nso-1: fetch ssh keys from devices"
echo "On lower-nso-1: perform sync-from"
ncs_cli ${ENV}4570 -u admin  >/dev/null <<EOF
request ncs:devices fetch-ssh-host-keys
request ncs:devices sync-from
EOF

echo "On lower-nso-2: fetch ssh keys from devices"
echo "On lower-nso-2: perform sync-from"
ncs_cli -u admin ${ENV}4571 >/dev/null <<EOF
request ncs:devices fetch-ssh-host-keys
request ncs:devices sync-from
EOF

## Must sync-from nso-upper last, since their sync-froms
## change their CDB

echo "On upper-nso: fetch ssh keys from devices"
echo "On upper-nso: perform sync-from"
echo "On upper-nso: configure cluster remote nodes: lower-nso-1 and lower-nso-2"
echo "On upper-nso: enable cluster device-notifications and cluster commit-queue"
echo "On upper-nso: fetch ssh keys from cluster remote nodes"
ncs_cli ${ENV}4569 -u admin <<EOF
config
set cluster device-notifications enabled
set cluster remote-node lower-nso-1 address 127.0.0.1 port 2023 authgroup default username admin
set cluster remote-node lower-nso-2 address 127.0.0.1 port 2024 authgroup default username admin
set cluster commit-queue enabled
commit
request cluster remote-node lower-nso-1 ssh fetch-host-keys
request cluster remote-node lower-nso-2 ssh fetch-host-keys
set ncs:devices device lower-nso-1 device-type netconf ned-id $MNAME
set ncs:devices device lower-nso-1 authgroup default
set ncs:devices device lower-nso-1 lsa-remote-node lower-nso-1
set ncs:devices device lower-nso-1 state admin-state unlocked
set ncs:devices device lower-nso-2 device-type netconf ned-id $MNAME
set ncs:devices device lower-nso-2 authgroup default
set ncs:devices device lower-nso-2 lsa-remote-node lower-nso-2
set ncs:devices device lower-nso-2 state admin-state unlocked
commit
request ncs:devices fetch-ssh-host-keys
request ncs:devices sync-from
exit
EOF
