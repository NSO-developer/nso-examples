#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Set up NSO with a real CLI device demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim --dir ./devsim stop &> /dev/null
set -e
rm -rf nso-rundir devsim

printf "${PURPLE}##### Setting up and running a simulated Cisco IOS device without generating NSO device setup configuration\n${NC}"
ncs-netsim --dir devsim create-network ${NCS_DIR}/examples.ncs/common/packages/cisco-ios-netsim-cli-1.0 1 myrouter
ncs-netsim --dir devsim start

printf "\n${GREEN}##### Set up NSO with the Network Element Driver (NED) package for the device\n${NC}"
ncs-setup --no-netsim --dest ./nso-rundir --use-copy --package ${NCS_DIR}/examples.ncs/common/packages/cisco-ios-netsim-cli-1.0

printf "${PURPLE}##### Start NSO\n${NC}"
ncs --cd ./nso-rundir

printf "${PURPLE}##### NSO loads the Network Rlement Driver (NED) for the device\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
show packages | nomore
EOF

printf "\n\n${PURPLE}##### Add configuration to NSO for a new authentication group\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices authgroups group mygroup
default-map remote-name admin
default-map remote-password admin
commit
EOF

printf "\n\n${PURPLE}##### Add configuration to NSO for the simulated device\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices device myrouter0
address 127.0.0.1
port 10022
authgroup mygroup
device-type cli ned-id cisco-ios-netsim-cli-1.0
ssh-algorithms public-key [ ssh-ed25519 ssh-rsa ]
state admin-state unlocked
commit
devices device myrouter0 ssh fetch-host-keys
devices device myrouter0 connect
EOF

printf "\n\n${PURPLE}##### Show NSO's configuration for managing the device using the cisco-ios CLI NED\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
show running-config devices authgroups | nomore
show running-config devices device myrouter0 | nomore
EOF

printf "\n\n${PURPLE}##### Save the config to files in case you want to restore it later\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
show running-config devices authgroups | display xml | save authgr.xml
show running-config devices device myrouter0 | display xml | save dev.xml
EOF

printf "\n\n${PURPLE}##### Delete NSO's device setup configuration\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
no devices device myrouter0
no devices authgroups group mygroup
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Restore the device setup configuration from the backup files\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
load merge authgr.xml
load merge dev.xml
commit dry-run
commit
EOF

printf "\n\n${GREEN}##### Configuring the router\n${NC}"
printf "${PURPLE}##### Test connecting to the device\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
devices device myrouter0 connect
EOF

printf "\n\n${PURPLE}##### Get the current configuration from the device covered by the YANG model\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
devices device myrouter0 sync-from
EOF

printf "\n\n${PURPLE}##### Show the device configuration\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
show running-config device device myrouter0 config | nomore
EOF

printf "\n\n${PURPLE}##### Add device configuration through NSO\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices device myrouter0 config ios:interface GigabitEthernet 1 ip address 192.168.1.1 255.255.255.0
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Bypass NSO and make a configuration change on the device (out-of-band change)\n${NC}"
ncs-netsim --dir ./devsim cli-i myrouter0 << EOF
enable
config
interface FastEthernet 1 ip address 192.168.2.1 255.255.255.0
show configuration commit changes | nomore
EOF

printf "\n${PURPLE}##### Sync the configuration change on the device with NSO\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
devices device myrouter0 sync-from dry-run
device device myrouter0 sync-from
show running-config device device myrouter0 config | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim --dir ./devsim stop

    printf "\n${PURPLE}##### Reset NSO to the initial configuration\n${NC}"
    ncs-setup --dest ./nso-rundir --reset

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    rm -rf ./nso-rundir
    rm -rf ./devsim
fi

printf "\n${GREEN}##### Done!\n${NC}"