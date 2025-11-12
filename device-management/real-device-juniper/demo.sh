#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Set up NSO with a real NETCONF device demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim --dir ./devsim stop &> /dev/null
set -e
rm -rf nso-rundir devsim

printf "${PURPLE}##### Setting up and running a simulated Junos device without generating NSO device setup configuration\n${NC}"
ncs-netsim --dir devsim create-network ${NCS_DIR}/examples.ncs/common/packages/juniper-junos-netsim-nc-1.0 2 olive
ncs-netsim --dir devsim start

printf "\n${GREEN}##### Set up NSO with the Network Element Driver (NED) package for the device\n${NC}"
ncs-setup --no-netsim --dest ./nso-rundir --use-copy --package ${NCS_DIR}/examples.ncs/common/packages/juniper-junos-netsim-nc-1.0

printf "${PURPLE}##### Start NSO\n${NC}"
ncs --cd ./nso-rundir

printf "${PURPLE}##### NSO loads the Network Rlement Driver (NED) for the device\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
show packages | nomore
EOF

printf "\n\n${PURPLE}##### Add configuration to NSO for a new authentication group\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices authgroups group junipers
default-map remote-name admin
default-map remote-password admin
commit
EOF

printf "\n\n${PURPLE}##### Add configuration to NSO for the simulated device\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices device olive0
address 127.0.0.1
port 12022
authgroup junipers
device-type netconf ned-id juniper-junos-netsim-nc-1.0
ssh-algorithms public-key [ ssh-ed25519 ssh-rsa ]
state admin-state unlocked
devices device olive1
address 127.0.0.1
port 12023
authgroup junipers
device-type netconf ned-id juniper-junos-netsim-nc-1.0
ssh-algorithms public-key [ ssh-ed25519 ssh-rsa ]
state admin-state unlocked
commit
devices device olive* ssh fetch-host-keys
EOF

printf "\n\n${PURPLE}##### Show NSO's configuration for managing the device using the Junos NETCONF NED\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
show running-config devices authgroups | nomore
show running-config devices device olive* | nomore
EOF

printf "\n\n${GREEN}##### Configuring the devices\n${NC}"
printf "${PURPLE}##### Test connecting to the devices\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
devices device olive* connect
EOF

printf "\n\n${PURPLE}##### Get the current configuration from the device covered by the YANG model\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
devices device olive* sync-from
EOF

printf "\n\n${PURPLE}##### Show the device configuration\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
show running-config devices device olive0..1 config junos:configuration interfaces interface | nomore
EOF

printf "\n\n${PURPLE}##### Add device configuration through NSO\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices device olive0..1 config junos:configuration snmp contact the-boss
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Or use device groups to add device configuration through NSO\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices device-group olives device-name [ olive0 olive1 ]
top
devices template snmp ned-id juniper-junos-netsim-nc-1.0 config junos:configuration snmp contact the-boss2
commit
devices device-group olives apply-template template-name snmp
commit dry-run
commit dry-run outformat native
commit
EOF


printf "\n\n${PURPLE}##### Enable the trace to see what is going on between NSO and the NETCONF devices\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices global-settings trace pretty
commit
EOF

printf "\n\n${PURPLE}##### Trace settings only take effect for new connections so 'disconnect'\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
devices disconnect
EOF


printf "\n\n${PURPLE}##### Change some device configuration through NSO\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin -C << EOF
config
devices device olive0 config junos:configuration snmp contact the-boss3
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the NETCONF trace from the 'olive0' device communication\n${NC}"
cat ./nso-rundir/logs/netconf-olive0.trace

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