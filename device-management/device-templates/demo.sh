#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Using device templates demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs-netsim stop
ncs --stop
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Build the example\n${NC}"
make all

printf "\n${PURPLE}##### Start the "network" of three simulated "router" devices\n${NC}"
ncs-netsim start

printf "\n${PURPLE}##### Start NSO with the default configuration\n${NC}"
ncs

printf "\n${PURPLE}##### Sync from the devices\n${NC}"
ncs_cli -n -u admin -C << EOF
devices sync-from
EOF

printf "\n\n${GREEN}##### Static templates\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Create a 'servers-static' template\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices template servers-static ned-id router-nc-1.0 config r:sys dns server 93.188.0.20
top
tag add devices template servers-static ned-id router-nc-1.0 config r:sys dns replace
devices template servers-static ned-id router-nc-1.0 config r:sys ntp server 83.227.219.208
top
tag add devices template servers-static ned-id router-nc-1.0 config r:sys ntp replace
devices template servers-static ned-id router-nc-1.0 config r:sys ntp server 83.227.219.208 key 2
devices template servers-static ned-id router-nc-1.0 config r:sys ntp controlkey 2 key 2
devices template servers-static ned-id router-nc-1.0 config r:sys syslog server 192.168.2.14
top
tag add devices template servers-static ned-id router-nc-1.0 config r:sys syslog replace
devices template servers-static ned-id router-nc-1.0 config r:sys syslog server 192.168.2.14 enabled true
devices template servers-static ned-id router-nc-1.0 config r:sys syslog server 192.168.2.14 selector 8 facility [ auth authpriv ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Change DNS, NTP, and Syslog servers by applying the template to the devices (without committing the changes)\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0..2 apply-template template-name servers-static
commit dry-run
EOF

printf "\n\n${GREEN}##### Templates with variables\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Create a 'servers-variables' template\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices template servers-variables ned-id router-nc-1.0 config r:sys dns server {\$dns}
top
tag add devices template servers-variables ned-id router-nc-1.0 config r:sys dns replace
devices template servers-variables ned-id router-nc-1.0 config r:sys ntp server {\$ntp}
top
tag add devices template servers-variables ned-id router-nc-1.0 config r:sys ntp replace
devices template servers-variables ned-id router-nc-1.0 config r:sys ntp server {\$ntp} key 2
devices template servers-variables ned-id router-nc-1.0 config r:sys ntp controlkey 2 key 2
devices template servers-variables ned-id router-nc-1.0 config r:sys syslog server {\$syslog}
top
tag add devices template servers-variables ned-id router-nc-1.0 config r:sys syslog replace
devices template servers-variables ned-id router-nc-1.0 config r:sys syslog server {\$syslog} enabled true
devices template servers-variables ned-id router-nc-1.0 config r:sys syslog server {\$syslog} selector 8 facility [ auth authpriv ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Apply the template to the 'ex0' device without commiting the changes\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 apply-template template-name servers-variables variable { name syslog value '192.168.2.14' } variable { name ntp value '83.227.219.208' } variable { name dns value '93.188.0.20' }
commit dry-run
EOF

printf "\n\n${GREEN}##### Templates with expressions\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Evaluate XPath expressions for debug purposes\n${NC}"
ncs_cli -n -u admin -C << EOF
devtools true
config
xpath eval /devices/device[name='ex0']/config/r:sys/dns/server/address
xpath eval /devices/device[name='ex1']/config/r:sys/syslog/server/*
EOF

printf "\n\n${PURPLE}##### Add some new values for device 'ex0' using the previous template\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex0 apply-template template-name servers-variables variable { name syslog value '192.168.2.14' } variable { name ntp value '83.227.219.208' } variable { name dns value '93.188.0.20' }
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Define a template using 'ex0' as the primary for DNS, NTP, and Syslog using XPath expressions\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices template servers-expr ned-id router-nc-1.0 config r:sys dns server {/devices/device[name='ex0']/config/r:sys/dns/server/address}
top
tag add devices template servers-expr ned-id router-nc-1.0 config r:sys dns replace
devices template servers-expr ned-id router-nc-1.0 config r:sys ntp server {/devices/device[name='ex0']/config/r:sys/ntp/server/name}
top
tag add devices template servers-expr ned-id router-nc-1.0 config r:sys ntp replace
devices template servers-expr ned-id router-nc-1.0 config r:sys ntp server {/devices/device[name='ex0']/config/r:sys/ntp/server/name} key {key}
devices template servers-expr ned-id router-nc-1.0 config r:sys ntp key {/devices/device[name='ex0']/config/r:sys/ntp/key/name} trusted {trusted}
devices template servers-expr ned-id router-nc-1.0 config r:sys ntp controlkey {/devices/device[name='ex0']/config/r:sys/ntp/controlkey}
devices template servers-expr ned-id router-nc-1.0 config r:sys syslog server {/devices/device[name='ex0']/config/r:sys/syslog/server/name}
top
tag add devices template servers-expr ned-id router-nc-1.0 config r:sys syslog replace
devices template servers-expr ned-id router-nc-1.0 config r:sys syslog server {/devices/device[name='ex0']/config/r:sys/syslog/server/name} enabled true
devices template servers-expr ned-id router-nc-1.0 config r:sys syslog server {/devices/device[name='ex0']/config/r:sys/syslog/server/name} selector {selector/name} facility [ {facility} security ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Use the template to setup 'ex1' and 'ex2' with values from 'ex0' without committing the changes\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex1..2 apply-template template-name servers-expr
commit dry-run
EOF

printf "\n\n${GREEN}##### Templates combined\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Combining the usage of variables and selections\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices template servers-combined ned-id router-nc-1.0 config r:sys dns server {/devices/device[name=\$primary]/config/r:sys/dns/server/address}
top
tag add devices template servers-combined ned-id router-nc-1.0 config r:sys dns replace
devices template servers-combined ned-id router-nc-1.0 config r:sys ntp server {/devices/device[name=\$primary]/config/r:sys/ntp/server/name}
top
tag add devices template servers-combined ned-id router-nc-1.0 config r:sys ntp replace
devices template servers-combined ned-id router-nc-1.0 config r:sys ntp server {/devices/device[name=\$primary]/config/r:sys/ntp/server/name} key {key}
devices template servers-combined ned-id router-nc-1.0 config r:sys ntp key {/devices/device[name=\$primary]/config/r:sys/ntp/key/name} trusted {trusted}
devices template servers-combined ned-id router-nc-1.0 config r:sys ntp controlkey {/devices/device[name=\$primary]/config/r:sys/ntp/controlkey}
devices template servers-combined ned-id router-nc-1.0 config r:sys syslog server {/devices/device[name=\$primary]/config/r:sys/syslog/server/name}
top
tag add devices template servers-combined ned-id router-nc-1.0 config r:sys syslog replace
devices template servers-combined ned-id router-nc-1.0 config r:sys syslog server {/devices/device[name=\$primary]/config/r:sys/syslog/server/name} enabled true
devices template servers-combined ned-id router-nc-1.0 config r:sys syslog server {/devices/device[name=\$primary]/config/r:sys/syslog/server/name} selector {selector/name} facility [ security {facility} ]
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Apply the template to 'ex1' and 'ex2' and use 'ex0' as the primary\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device ex1..2 apply-template template-name servers-combined variable { name primary value 'ex0' }
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### The resulting NSO XPath trace log\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
cat logs/xpath.trace

printf "\n${GREEN}##### RESTCONF\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Apply the created templates over RESTCONF using the 'curl' tool\n${NC}"
if hash curl 2> /dev/null; then
printf "\n${PURPLE}##### Apply the servers-static template\n${NC}"
curl -s -X POST -T RESTCONF/servers-static.xml admin:admin@localhost:8080/restconf/data/tailf-ncs:devices/device=ex0/apply-template
printf "\n${PURPLE}##### Apply the servers-variables template\n${NC}"
curl -s -X POST -T RESTCONF/servers-variables.xml admin:admin@localhost:8080/restconf/data/tailf-ncs:devices/device=ex0/apply-template
printf "\n${PURPLE}##### Apply the servers-expr template\n${NC}"
curl -s -X POST -T RESTCONF/servers-expr.xml admin:admin@localhost:8080/restconf/data/tailf-ncs:devices/device=ex0/apply-template
printf "\n${PURPLE}##### Apply the servers-combined template\n${NC}"
curl -s -X POST -T RESTCONF/servers-combined.xml admin:admin@localhost:8080/restconf/data/tailf-ncs:devices/device=ex0/apply-template
else
    printf "${RED}##### No 'curl' command available, skip\n${NC}"
fi

printf "\n${GREEN}##### NETCONF\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "\n${PURPLE}##### Apply the created templates over NETCONF using the NSO 'netconf-console' tool\n${NC}"
printf "\n${PURPLE}##### Apply the servers-static template\n${NC}"
netconf-console --rpc=NETCONF/nc-servers-static.xml
printf "\n${PURPLE}##### Apply the servers-variables template\n${NC}"
netconf-console --rpc=NETCONF/nc-servers-variables.xml
printf "\n${PURPLE}##### Apply the servers-expr template\n${NC}"
netconf-console --rpc=NETCONF/nc-servers-expr-ex1.xml
printf "\n${PURPLE}##### Apply the servers-combined template\n${NC}"
netconf-console --rpc=NETCONF/nc-servers-combined-ex2.xml

printf "\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim stop

    printf "\n${PURPLE}##### Reset the example to its original files\n${NC}"
    if [ -z "$NONINTERACTIVE" ]; then
        printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
        read -n 1 -s -r
    fi
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"