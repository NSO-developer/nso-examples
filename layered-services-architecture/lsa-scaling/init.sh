#!/bin/sh

set -e

if [ -n "$NCS_IPC_PATH" ]; then
NODE1="NCS_IPC_PATH=${NCS_IPC_PATH}.4569"
NODE2="NCS_IPC_PATH=${NCS_IPC_PATH}.4570"
NODE3="NCS_IPC_PATH=${NCS_IPC_PATH}.4571"
NODE4="NCS_IPC_PATH=${NCS_IPC_PATH}.4572"
else
# All nodes use the same IP for IPC but different ports
export NCS_IPC_ADDR=127.0.0.1
NODE1=NCS_IPC_PORT=4569
NODE2=NCS_IPC_PORT=4570
NODE3=NCS_IPC_PORT=4571
NODE4=NCS_IPC_PORT=4572
fi

echo "Initialize NSO nodes:"

echo "On lower-nso-1: fetch ssh keys from devices"
echo "On lower-nso-1: perform sync-from"
env $NODE2 ncs_cli -u admin >/dev/null <<EOF
request devices fetch-ssh-host-keys
request devices sync-from
EOF

echo "On lower-nso-2: fetch ssh keys from devices"
echo "On lower-nso-2: perform sync-from"
env $NODE3 ncs_cli -u admin >/dev/null <<EOF2
request devices fetch-ssh-host-keys
request devices sync-from
EOF2

echo "On lower-nso-3: fetch ssh keys from devices"
echo "On lower-nso-3: perform sync-from"
env $NODE4 ncs_cli -u admin >/dev/null <<EOF2
request devices fetch-ssh-host-keys
request devices sync-from
EOF2

## Must sync-from nso-upper last, since their sync-froms
## change their CDB

echo "On upper-nso: fetch ssh keys from devices"
echo "On upper-nso: perform sync-from"
echo "On upper-nso: configure cluster remote nodes: lower-nso-1 and lower-nso-2"
echo "On upper-nso: enable cluster device-notifications and cluster commit-queue"
echo "On upper-nso: fetch ssh keys from cluster remote nodes"
env $NODE1 ncs_cli -u admin >/dev/null <<EOF2
request devices fetch-ssh-host-keys
request devices sync-from
config
set cluster device-notifications enabled
set cluster remote-node lower-nso-1 address 127.0.0.1 port 2023 authgroup default username admin
set cluster remote-node lower-nso-2 address 127.0.0.1 port 2024 authgroup default username admin
commit
set cluster commit-queue enabled
commit
request cluster remote-node lower-nso-1 ssh fetch-host-keys
request cluster remote-node lower-nso-2 ssh fetch-host-keys
exit
EOF2
