#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Using the NSO RESTCONF API demo\n${NC}"
if hash curl 2> /dev/null; then
    printf "${RED}##### curl command available, continuing with demo\n${NC}"
else
    printf "${RED}##### No curl command available, exit\n${NC}"
    exit 1
fi

printf "${PURPLE}##### Start clean\n${NC}"
set +e
make -C ../../service-management/website-service stop
set -e
make -C ../../service-management/website-service clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Setup the website-server example simulated network and build the packages\n${NC}"
make -C ../../service-management/website-service all

printf "\n${PURPLE}##### Start the website-server example netsim network and NSO\n${NC}"
make -C ../../service-management/website-service start

printf "\n${PURPLE}##### Do root resource discovery\n${NC}"
curl -v -u admin:admin http://localhost:8080/.well-known/host-meta

printf "\n${PURPLE}##### Do a top-level GET\n${NC}"
curl -v -u admin:admin http://localhost:8080/restconf

printf "\n${PURPLE}##### Do a top-level GET with JSON format reply\n${NC}"
curl -u admin:admin -H "Accept: application/yang-data+json" http://localhost:8080/restconf

printf "\n\n${PURPLE}##### GET the datastore\n${NC}"
curl -s -u admin:admin http://localhost:8080/restconf/data

printf "\n\n${PURPLE}##### Sync the configuration from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${PURPLE}##### Show the lb0 device config using the CLI\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device lb0 config lb:lbConfig | nomore
EOF

printf "\n\n${PURPLE}##### Show device configuration over RESTCONF\n${NC}"
curl -s -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig

printf "\n${PURPLE}##### Do the same GET with a depth=2 selector\n${NC}"
curl -s -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig?depth=2

printf "\n${PURPLE}##### Find a resource to delete. For example, the NTP server\n${NC}"
curl -s -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/ntp-server

printf "\n${PURPLE}##### Save the resource we want to delete to a local file\n${NC}"
curl -s -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/ntp-server > saved-ntp-server.xml

printf "\n${PURPLE}##### Delete the resource\n${NC}"
curl -v -X DELETE -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/ntp-server

printf "\n${PURPLE}##### Use the CLI to verify it was deleted\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device lb0 config lb:lbConfig | nomore
EOF

printf "\n\n${PURPLE}##### Use RESTCONF to verify it was deleted\n${NC}"
curl -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/

printf "\n${PURPLE}##### Re-create the resource that was just deleted using the saved file\n${NC}"
curl -v -X POST -T saved-ntp-server.xml -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system

printf "\n${PURPLE}##### Use the CLI to verify it was re-created\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device lb0 config lb:lbConfig | nomore
EOF

printf "\n\n${PURPLE}##### Use RESTCONF to verify it was re-created\n${NC}"
curl -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/

printf "\n${PURPLE}##### Prepare a patch-search.xml file\n${NC}"
echo "<search>foo.com</search>"
echo "<search>foo.com</search>" > patch-search.xml

printf "\n${PURPLE}##### Modify the search leaf\n${NC}"
curl -v -X PATCH -T patch-search.xml -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/resolver/search

printf "\n${PURPLE}##### Use the CLI to verify the changes\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device lb0 config lb:lbConfig system resolver | nomore
EOF

printf "\n\n${PURPLE}##### Verify the change in RESTCONF, note the use of ?fields=search to select one leaf\n${NC}"
curl -u admin:admin  http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/resolver\?fields=search

printf "\n${PURPLE}##### Save the resolver settings to a file\n${NC}"
curl -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/resolver > saved-resolver.xml

printf "\n${PURPLE}##### Modify the values in the saved-resolver.xml file\n${NC}"
sed -i.bak 's|18.4.5.6|42.42.42.42|' saved-resolver.xml

printf "\n${PURPLE}##### Apply the modifications by using PUT\n${NC}"
curl -vs -X PUT -T saved-resolver.xml -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/resolver

printf "\n${PURPLE}##### Verify the changes\n${NC}"
curl -u admin:admin http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/resolver

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n\n${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "${PURPLE}##### Stop all daemons and clean all created files\n${NC}"
    rm -vf *.xml *.bak
    set +e
    make -C ../../service-management/website-service stop
    set -e
    make -C ../../service-management/website-service clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
