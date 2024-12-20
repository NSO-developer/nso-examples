#!/bin/sh


set -e

if [ -n "$NCS_IPC_PATH" ]; then
    ENV="--socket-path $NCS_IPC_PATH."
else
    ENV="--port "
fi

echo "Initialize NSO nodes:"

echo "On lower-nso-1: fetch ssh keys from devices"
echo "On lower-nso-1: perform sync-from"
ncs_cli ${ENV}4570 -u admin >/dev/null <<EOF
request devices fetch-ssh-host-keys
request devices sync-from
EOF

echo "On lower-nso-2: fetch ssh keys from devices"
echo "On lower-nso-2: perform sync-from"
ncs_cli -u admin ${ENV}4571 >/dev/null <<EOF2
request devices fetch-ssh-host-keys
request devices sync-from
EOF2
