#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Simulate a Cisco IOS device demo\n${NC}"

printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim --dir nso-rundir/netsim stop &> /dev/null
set -e
rm -rf nso-rundir

printf "\n${GREEN}##### Print an IETF RFC 8340 tree structure of the Cisco IOS YANG model\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
yanger -W none -f tree ${NCS_DIR}/examples.ncs/common/packages/cisco-ios-netsim-cli-1.0/src/yang/netsim-ned-cisco-ios.yang

printf "\n${GREEN}##### Setting up and Running the Simulator\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Create the simulated network\n${NC}"
ncs-netsim --dir nso-rundir/netsim create-network ${NCS_DIR}/examples.ncs/common/packages/cisco-ios-netsim-cli-1.0 3 c

printf "\n${PURPLE}##### Start the simulated devices\n${NC}"
ncs-netsim --dir nso-rundir/netsim start

printf "\n${PURPLE}##### Run the I-style CLI towards one of the devices\n${NC}"
ncs-netsim --dir nso-rundir/netsim cli-i c1 << EOF
enable
show running-config | nomore
EOF

printf "\n${PURPLE}##### Run 'ncs-netsim -h' to list all available commands\n${NC}"
ncs-netsim -h

printf "${GREEN}##### Setting up and Starting NSO\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Set up and configure NSO with the devices\n${NC}"
ncs-setup --netsim-dir ./nso-rundir/netsim --dest ./nso-rundir

printf "\n${PURPLE}##### Start NSO\n${NC}"
ncs --cd ./nso-rundir

printf "${PURPLE}##### Ensure that the netsim devices still exist and are running from the previous steps\n${NC}"
ncs-netsim --dir ./nso-rundir/netsim list
ncs-netsim --dir ./nso-rundir/netsim is-alive

printf "\n${PURPLE}##### Start the NSO J-style CLI\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
show packages package cisco-ios | nomore
show configuration devices device | nomore
EOF

printf "\n\n${PURPLE}##### Connect to the devices and read up their configurations into NSO\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
request devices connect
request devices sync-from
EOF

printf "\n\n${PURPLE}##### View the configuration of the 'c0' device\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
show configuration devices device c0 config | nomore
EOF

printf "\n\n${PURPLE}##### Show a particular piece of configuration from several devices\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
show configuration devices device c0..2 config ios:router | nomore
EOF

printf "\n\n${PURPLE}##### Enter configuration mode and add some configuration across the devices. Preview the changes before committing them\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
configure
set devices device c0..2 config ios:router bgp 64512 neighbor 1.2.3.4 remote-as 2
commit dry-run
commit | details
EOF

printf "\n\n${PURPLE}##### Take a look at the 'rollback' file\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
file show logs/rollback10005 | nomore
EOF

printf "\n\n${PURPLE}##### Load the rollback file and preview the changes before committing them\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
configure
request rollback-files apply-rollback-file fixed-number 10005
compare running brief | nomore
commit
EOF

printf "\n\n${PURPLE}##### Enable the trace to see what is going on between NSO and the device CLI\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
configure
set devices global-settings trace raw trace-dir logs
commit
EOF

printf "\n\n${PURPLE}##### Trace settings only take effect for new connections so 'disconnect'\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
request devices disconnect
EOF

printf "\n\n${PURPLE}##### Make a change to, for example, 'c0'\n${NC}"
ncs_cli -n --cwd ./nso-rundir -u admin << EOF
configure
set devices device c0 config ios:interface FastEthernet 1 ip address primary address 192.168.1.1 mask 255.255.255.0
commit dry-run
commit
EOF

printf "\n\n${PURPLE}##### Inspect the CLI trace from the 'c0' device communication\n${NC}"
cat ./nso-rundir/logs/ned-cisco-ios-netsim-cli-1.0-c0.trace

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r

    printf "\n${PURPLE}##### Stop NSO and the netsim devices\n${NC}"
    ncs --stop
    ncs-netsim --dir ./nso-rundir/netsim stop

    printf "\n${PURPLE}##### Restore the example to the initial configuration\n${NC}"
    ncs-netsim --dir ./nso-rundir/netsim reset
    ncs-setup --dest ./nso-rundir --reset

    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    rm -rf ./nso-rundir
fi

printf "\n${GREEN}##### Done!\n${NC}"
