#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Datacenter Q-in-Q Tunneling demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network and NSO\n${NC}"
make start

printf "\n${PURPLE}##### The XML init file containing the device configuration\n${NC}"
cat ./ncs-cdb/netsim_devices_init.xml

printf "\n${PURPLE}##### The same device configuration in CDB\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device | nomore
EOF

printf "\n\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Show the device configuration synced from the devices to CDB\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device config | nomore
EOF

printf "\n\n${GREEN}##### Configuring the Service\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Configure the S-VLAN that the first customer network will use, the edge devices and interfaces to be used, and core switches\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services service customer1 type qinq s-vlan 444
services service customer1 type qinq edge-switch p0 trunk-interface ae10
services service customer1 type qinq edge-switch p0 edge-interface ge-1/1/1 c-vlan [ 13 15 ]
services service customer1 type qinq edge-switch p1 trunk-interface ae10
services service customer1 type qinq edge-switch p1 edge-interface ge-1/1/1 c-vlan [ 15 ]
services service customer1 type qinq edge-switch p2 trunk-interface ae10
services service customer1 type qinq edge-switch p2 edge-interface ge-1/1/1 c-vlan [ 13 ]
services service customer1 type qinq core-switch c1 trunk-interface [ "FastEthernet 1/1" "FastEthernet 1/2" ]
services service customer1 type qinq core-switch c2 trunk-interface [ "FastEthernet 1/1" "FastEthernet 1/2" ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Configure customer2, edge devices and interfaces, and core switches\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services service customer2 type qinq s-vlan 777
services service customer2 type qinq edge-switch p2 trunk-interface ae10
services service customer2 type qinq edge-switch p2 edge-interface ge-1/1/2 c-vlan [ 13 ]
services service customer2 type qinq edge-switch p2 edge-interface ge-1/1/3 c-vlan [ 15 ]
services service customer2 type qinq edge-switch c0 trunk-interface "FastEthernet 1/2"
services service customer2 type qinq edge-switch c0 edge-interface "FastEthernet 1/0" c-vlan [ 13 15 ]
services service customer2 type qinq core-switch c1 trunk-interface [ "FastEthernet 1/1" "FastEthernet 1/2" ]
services service customer2 type qinq core-switch c2 trunk-interface [ "FastEthernet 1/1" "FastEthernet 1/2" ]
commit dry-run outformat native
commit
EOF

printf "\n\n${GREEN}##### NSO power tools\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Change the S-VLAN for customer2 to 200 and use commit dry-run to review the resulting device configuration changes showing how the NSO service will automatically reconfigure all affected devices\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services service customer2 type qinq s-vlan 200
commit dry-run outformat native
commit
EOF

printf "\n\n${GREEN}##### Out-of-band configuration\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Connect directly to the device c0 and reconfigure an edge interface that was previously configured using NSO\n${NC}"
ncs-netsim cli-c c0 << EOF
config
vlan 1234
exit
interface FastEthernet 1/0
switchport access vlan 1234
commit
EOF

printf "\n${PURPLE}##### Check the status of the network configuration from NSO\n${NC}"
ncs_cli -n -u admin -C << EOF
devices check-sync
EOF

printf "\n\n${PURPLE}##### Try adding a new service instance that uses the out-of-sync device\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services service customer3 type qinq s-vlan 999
services service customer3 type qinq edge-switch c0 trunk-interface "FastEthernet 1/2"
services service customer3 type qinq edge-switch c0 edge-interface "FastEthernet 1/1" c-vlan [ 13 15 ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Sync from the device and see how it affects our *services*\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device c0 sync-from
devices check-sync
services check-sync
EOF

printf "\n\n${PURPLE}##### Service customer2 is out-of-sync with the device configuration. Debug the issue\n${NC}"
ncs_cli -n -u admin -C << EOF
services service customer2 type qinq check-sync outformat cli
EOF

printf "\n\n${PURPLE}##### Repair the service by having NSO re-deploy the service\n${NC}"
ncs_cli -n -u admin -C << EOF
services service customer2 type qinq re-deploy
devices check-sync
services check-sync
EOF

printf "\n\n${PURPLE}##### List what services are configured on what devices\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices device services | nomore
EOF

printf "\n\n${GREEN}##### Must validation\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Configure a service instance that triggers a must validation error\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services service customer3 type qinq s-vlan 444
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Test triggering the other must statement with a non unique edge interface\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services service customer3 type qinq s-vlan 555
services service customer3 type qinq edge-switch c0 trunk-interface "FastEthernet 1/2"
services service customer3 type qinq edge-switch c0 edge-interface "FastEthernet 1/0" c-vlan [ 13 15 ]
services service customer3 type qinq core-switch c1 trunk-interface [ "FastEthernet 1/1" "FastEthernet 1/2" ]
services service customer3 type qinq core-switch c2 trunk-interface [ "FastEthernet 1/1" "FastEthernet 1/2" ]
commit dry-run
commit
EOF

printf "\n\n${GREEN}##### Alarms\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Generate an alarm\n${NC}"
make -C ./alarms alarm-1
ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
EOF

printf "\n\n${PURPLE}##### Toggle the alarm\n${NC}"
make -C ./alarms alarm-1
make -C ./alarms clear-1
ncs_cli -n -u admin -C << EOF
show alarms alarm-list | nomore
EOF

printf "\n\n${PURPLE}##### Set state on the alarms\n${NC}"
ncs_cli -n -u admin -C << EOF
alarms alarm-list alarm c0 spanning-tree-alarm /devices/device[name='c0']/config/ios:interface/FastEthernet[name='1/0']/switchport spanning-tree handle-alarm state ack description "Joe will check"
alarms alarm-list alarm c0 spanning-tree-alarm /devices/device[name='c0']/config/ios:interface/FastEthernet[name='1/0'] spanning-tree handle-alarm state closed description "Fixed"
show alarms alarm-list | nomore
EOF

printf "\n\n${PURPLE}##### Purge an individual alarm\n${NC}"
ncs_cli -n -u admin -C << EOF
alarms alarm-list alarm c0 spanning-tree-alarm /devices/device[name='c0']/config/ios:interface/FastEthernet[name='1/0'] spanning-tree purge
EOF

printf "\n\n${PURPLE}##### Purging the alarm-list with a criteria\n${NC}"
ncs_cli -n -u admin -C << EOF
alarms purge-alarms ?
EOF

printf "\n\n${PURPLE}##### Compress the list of state-changes. Removes all state-changes except the last\n${NC}"
ncs_cli -n -u admin -C << EOF
alarms compress-alarms
EOF

printf "\n\n${GREEN}##### Template based service demo\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network and NSO\n${NC}"
make start

printf "\n\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Set the S-VLAN customer1's network will use and configure the edge and core switches\n${NC}"
ncs_cli -n -u admin -C << EOF
config
qinq-template customer1 s-vlan 444
qinq-template customer1 edge-switch p0 trunk-interface ae10
qinq-template customer1 edge-switch p0 edge-interface ge-1/1/1 c-vlan [ 13 15 ]
qinq-template customer1 edge-switch p1 trunk-interface ae10
qinq-template customer1 edge-switch p1 edge-interface ge-1/1/1 c-vlan [ 15 ]
qinq-template customer1 edge-switch p2 trunk-interface ae10
qinq-template customer1 edge-switch p2 edge-interface ge-1/1/1 c-vlan [ 13 ]
qinq-template customer1 core-switch c1 trunk-interface [ "1/1" "1/2" ]
qinq-template customer1 core-switch c2 trunk-interface [ "1/1" "1/2" ]
commit dry-run
commit
EOF


printf "\n\n${PURPLE}##### Configure customer2. When committing, use the debug template pipe command to enable debug information\n${NC}"
ncs_cli -n -u admin -C << EOF
config
qinq-template customer2 s-vlan 777
qinq-template customer2 edge-switch c0 trunk-interface "1/2"
qinq-template customer2 edge-switch c0 edge-interface "1/0" c-vlan [ 13 15 ]
qinq-template customer2 edge-switch p2 trunk-interface ae10
qinq-template customer2 edge-switch p2 edge-interface ge-1/1/2 c-vlan [ 13 ]
qinq-template customer2 edge-switch p2 edge-interface ge-1/1/3 c-vlan [ 15 ]
qinq-template customer2 core-switch c1 trunk-interface [ "1/1" "1/2" ]
qinq-template customer2 core-switch c2 trunk-interface [ "1/1" "1/2" ]
commit dry-run outformat native | debug template
commit
EOF

printf "\n\n${PURPLE}##### Change the S-VLAN for customer2 to 200. When committing, use the debug template and debug xpath pipe commands to enable extensive debug information\n${NC}"
ncs_cli -n -u admin -C << EOF
config
qinq-template customer2 s-vlan 200
commit dry-run outformat native | debug template | debug xpath
commit
EOF

printf "\n\n${GREEN}##### Java code and template combination service demo\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${PURPLE}##### Setup the simulated network and build the packages\n${NC}"
make all

printf "\n${PURPLE}##### Start the netsim network and NSO\n${NC}"
make start

printf "\n\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Configure a customer with VLAN ID 444 and configure the edge and core switches\n${NC}"
ncs_cli -n -u admin -C << EOF
config
qinq-java-template customer_444 edge-switch p0 edge-interface ge-1/1/1 c-vlan [ 13 15 ]
qinq-java-template customer_444 edge-switch p1 trunk-interface ae10
qinq-java-template customer_444 edge-switch p1 edge-interface ge-1/1/1 c-vlan [ 15 ]
qinq-java-template customer_444 edge-switch p2 trunk-interface ae10
qinq-java-template customer_444 edge-switch p2 edge-interface ge-1/1/1 c-vlan [ 13 ]
qinq-java-template customer_444 core-switch c1 trunk-interface [ "1/1" "1/2" ]
qinq-java-template customer_444 core-switch c2 trunk-interface [ "1/1" "1/2" ]
commit dry-run outformat native | debug template
commit
EOF

printf "\n\n${PURPLE}##### Change the S-VLAN for customer_444 to 200. When committing, use the debug template and debug xpath pipe commands to enable extensive debug information\n${NC}"
ncs_cli -n -u admin -C << EOF
config
rename qinq-java-template customer_444 customer_200
commit dry-run outformat native | debug template | debug xpath
commit
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
