#!/bin/sh

set -e

ncs_cli -u admin <<EOF
configure
load merge init-data/nsos.xml
commit
request devices fetch-ssh-host-keys
request devices sync-from
load merge init-data/qos.xml
load merge init-data/topology.xml
commit
exit
EOF




