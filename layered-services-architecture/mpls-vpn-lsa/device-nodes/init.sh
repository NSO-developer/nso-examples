#!/bin/sh

set -e

ncs_cli -u admin <<EOF
request devices fetch-ssh-host-keys
request devices sync-from
exit
exit
EOF




