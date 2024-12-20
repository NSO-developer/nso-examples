#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Upgrade a Service with Non-backward Compatible Changes Demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the packages\n${NC}"
make all

printf "${PURPLE}##### Copy the vlan package to the packages directory\n${NC}"
cp -r ./package-store/vlan ./packages

printf "\n${PURPLE}##### Start the netsim network\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### The XML init file containing the device configuration\n${NC}"
cat ./ncs-cdb/ncs_init.xml

printf "\n\n${PURPLE}##### Initial sync from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Create two service instances\n${NC}"
ncs_cli -n -u admin -C << EOF
config
services vlan s1 description x iface ethX unit 1 vid 3
services vlan s2 description x iface ethX unit 2 vid 4
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Check the service configuration data\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config services vlan | nomore
EOF

printf "\n\n${GREEN}##### Upgrade the vlan package with the vlan_v2 package\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs --stop
rm -rf packages/vlan
cp -r ./package-store/vlan_v2 ./packages/vlan
make vlan

printf "\n${PURPLE}##### Start NSO and have NSO reload packages to perform the CDB upgrade\n${NC}"
ncs --with-package-reload

printf "\n${PURPLE}##### Check the upgraded service data\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config services vlan | nomore
EOF

printf "\n\n${PURPLE}##### Review the changes made by the vlan service for the ex0 device\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device ex0 | display service-meta-data | nomore
EOF

printf "\n\n${GREEN}##### Upgrade the vlan_v2 package with the tunnel package\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "\n\n${PURPLE}##### Undeploy the service instances with no-networking to remove the vlan service meta-data before upgrading to the tunnel package\n${NC}"
ncs_cli -n -u admin -C << EOF
services vlan s1..2 un-deploy no-networking
show running-config devices device ex0 | display service-meta-data | nomore
EOF

printf "\n\n${PURPLE}##### Replace the vlan_v2 package with the tunnel package\n${NC}"
ncs --stop
rm -rf packages/vlan
cp -r ./package-store/tunnel ./packages/
make tunnel

printf "\n${PURPLE}##### Start NSO and have NSO force reload packages to perform the CDB upgrade\n${NC}"
ncs --with-package-reload-force

printf "\n${PURPLE}##### Check the upgraded service data\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config services tunnel | nomore
EOF

printf "\n${PURPLE}##### Re-deploy the service instances to own the device configuration again. Check that the re-deploy does not change the device configuration before the actual re-deploy\n${NC}"
ncs_cli -n -u admin -C << EOF
services tunnel s1..2 re-deploy no-networking
devices device ex0..2 compare-config
EOF

printf "\n \n${PURPLE}##### Check that the services are still in sync with the device configuration\n${NC}"
ncs_cli -n -u admin -C << EOF
services tunnel s1 check-sync
services tunnel s2 check-sync
EOF

printf "\n\n${PURPLE}##### No diff and in sync. Re-deploy. Will cause no change on the devices as intended\n${NC}"
ncs_cli -n -u admin -C << EOF
services tunnel s1..2 re-deploy dry-run
services tunnel s1..2 re-deploy
EOF

printf "\n\n${PURPLE}##### Review the device configuration data\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device ex0 | display service-meta-data | nomore
EOF

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim stop

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
