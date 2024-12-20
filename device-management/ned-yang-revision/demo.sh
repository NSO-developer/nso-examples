#!/bin/sh

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

printf "\n${PURPLE}##### Reset and setup the example\n${NC}"

make stop &> /dev/null
make clean all start

printf "\n${PURPLE}##### Create and initialize a VPN link service\n${NC}"

ncs_cli -n -u admin -C << EOF
show packages | nomore
config
devices sync-from
top
link t2 unit 17 vlan-id 1
link t2 endpoints ex0 eth0 ex1 eth0
commit dry-run
commit
top
exit
link t2 get-modifications
EOF

printf "\n\n${PURPLE}##### Stop NSO and netsim devices. Backup both NSO and the netsim devices before upgrading\n##### Since we are using a local NSO install, we backup the runtime directory for potential disaster recovery.\n${NC}"
make stop backup

printf "\n\n${PURPLE}##### Upgrade ex0 using revision-merge. Non-backward compatible changes are not allowed\n${NC}"
make revmerge start
ncs_cli -n -u admin -C << EOF
show packages | nomore
config
link t2 re-deploy dry-run
link t2 re-deploy
devices device ex0 sync-to dry-run
devices device ex0 sync-to
end
show running-config link | nomore
show running-config devices device config sys interfaces | nomore
EOF
printf "\n\n${PURPLE}netconf-console --port=\$(ncs-netsim get-port ex0 netconf) --get-config -x /sys/interfaces\n${NC}"
netconf-console --port=$(ncs-netsim get-port ex0 netconf) --get-config -x /sys/interfaces

printf "\n\n${PURPLE}##### Revision merge will silently drop data that is not supported by the device\n##### with an older revision of the YANG mode, unless we use the no-revision-drop parameter\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex1 config sys interfaces interface eth2 type ethernetCsmacd
commit dry-run
commit no-revision-drop
commit
show full-configuration devices device ex1 config | nomore
EOF
printf "\n\n${PURPLE}netconf-console --port=\$(ncs-netsim get-port ex1 netconf) --get-config -x /sys/interfaces\n${NC}"
netconf-console --port=$(ncs-netsim get-port ex1 netconf) --get-config -x /sys/interfaces


printf "\n${PURPLE}##### Upgrade ex0 using CDM.\n##### I.e. split the NED into a version 1.0 for ex1\n##### and migrate to a 1.1 NED for ex0 to allow non-backward compatible YANG model changes\n${NC}"
make stop-nso cdm start-nso
ncs_cli -n -u admin -C << EOF
show packages | nomore
config
devices device ex0 migrate new-ned-id router-nc-1.1 dry-run verbose
devices device ex0 migrate new-ned-id router-nc-1.1 verbose
commit
link t2 re-deploy dry-run
link t2 re-deploy
end
show running-config link | nomore
show running-config devices device config sys interfaces | nomore
EOF

printf "\n\n${PURPLE}netconf-console --port=\$(ncs-netsim get-port ex0 netconf) --get-config -x /sys/interfaces\n${NC}"
netconf-console --port=$(ncs-netsim get-port ex0 netconf) --get-config -x /sys/interfaces

printf "\n${GREEN}##### Done!\n${NC}"
