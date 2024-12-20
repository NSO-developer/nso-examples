#!/usr/bin/env bash

# NSO NETCONF NED example.
#
# ncs_cli command over IPC demo script
#
# See the README file for more information

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

DEVICE_NETCONF_PORT=12022
DEVICE_IPC_PORT=4565

printf "\n${PURPLE}###### Reset the demo\n${NC}"
rm -rf dev-yang nso-rundir
set +e
ncs --stop &> /dev/null
set -e

set +u
if [ -f ${NCS_DIR}/netsim/confd/confdrc ]; then
    source ${NCS_DIR}/netsim/confd/confdrc
fi
set -u

make -C devsim stop &> /dev/null

printf "\n${PURPLE}###### Setup the simulated device\n${NC}"
make -C devsim clean all start nc-cfg-card

printf "\n${PURPLE}###### To get the device YANG models we can, for example, either:\n${NC}"
printf "1. Get a copy of the YANG models ${GREEN}from the device vendor${NC} with the revision matching what is on the device\n${NC}"
printf "2. Use the NSO ${GREEN}netconf-console${NC} tool with the NETCONF <get-schema> operation if the device supports it\n${NC}"
printf "3. Use the NSO ${GREEN}NETCONF NED builder${NC} tool for devices that support the NETCONF <get-schema> operation\n"

printf "\n\n${GREEN}*****This demo script will use the NSO NETCONF NED builder tool*****\n\n${NC}"

printf "\n${PURPLE}###### Setup the NSO run-time directory using the ncs-setup script\n${NC}"
ncs-setup  --no-netsim --dest nso-rundir

printf "\n${PURPLE}###### Start NSO\n${NC}"
ncs --cd ./nso-rundir

printf "\n${PURPLE}###### To connect to the device, there needs to be a mapping between the NSO user and the device authentication\n${NC}"
printf "${PURPLE}###### This is controlled by configuring NSO authgroups\n${NC}"
printf "${PURPLE}###### The ${GREEN}default${PURPLE} group works here as our NSO ${GREEN}admin${PURPLE} user can authenticate as the device user ${GREEN}admin${NC} with pass ${GREEN}admin\n${NC}"
echo "show running-config devices authgroups group" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}###### Configure the NSO device connnection. Set the temporary NED identity to NETCONF as the NED package has not yet been built\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device hw0 address 127.0.0.1 port 12022 authgroup default
devices device hw0 trace pretty
state admin-state unlocked
device-type netconf ned-id netconf
commit
EOF

printf "\n\n${PURPLE}###### Configure the NETCONF NED Builder\n${NC}"
ncs_cli -n -u admin -C << EOF
devtools true
paginate false
config
netconf-ned-builder project hardware 1.0 device hw0 local-user admin vendor Tail-f
commit
end
show netconf-ned-builder project hardware
EOF
printf "\n\n${PURPLE}###### NOTE: The cache directory above is where we can add additional YANG and YANG annotation files. Files added need to be configured with the NED builder to be included with the project\n${NC}"

printf "${PURPLE}###### Fetch the public SSH host key from the device and use the NETCONF NED Builder with the <get-schema> operation to get the YANG models\n${NC}"
ncs_cli -n -u admin -C << EOF
devtools true
paginate false
devices fetch-ssh-host-keys
netconf-ned-builder project hardware 1.0 fetch-module-list
show netconf-ned-builder project hardware 1.0 module
EOF

printf "\n\n${PURPLE}###### We are interested in the ${GREEN}ietf-hardware.yang${PURPLE} model as we want to manage the device hardware\n${NC}"
printf "${PURPLE}###### We also need the ${GREEN}iana-hardware.yang${PURPLE} model, imported by ${GREEN}ietf-hardware.yang${PURPLE}, and ${GREEN}timestamp-hardware.yang${PURPLE} that augments ${GREEN}ietf-hardware.yang${PURPLE}\n${NC}"

ncs_cli -n -u admin -C << EOF
devtools true
paginate false
netconf-ned-builder project hardware 1.0 module ietf-hardware 2018-03-13 select
netconf-ned-builder project hardware 1.0 module timestamp-hardware "" select
show netconf-ned-builder project hardware 1.0 module status
EOF

STATUS="pending"
PENDING="pending"
echo ""
while [[ $STATUS == *$PENDING* ]]; do
    printf "${RED}Waiting for NSO to download the selected YANG models\n${NC}"
    sleep 1
    STATUS=$(echo "devtools true; show netconf-ned-builder project hardware 1.0 module status | nomore" | ncs_cli -u admin -C)
done
echo "devtools true; show netconf-ned-builder project hardware 1.0 module status | nomore" | ncs_cli -u admin -C

#Temporary NSO issue workaround (augments on notifications are removed when transformed)
sed -i.bak 's/entConfigChange";/entConfigChange"; leaf last-change { type yang:date-and-time; } /' nso-rundir/state/netconf-ned-builder/cache/hardware-nc-1.0/ietf-hardware@2018-03-13.yang; rm -f nso-rundir/state/netconf-ned-builder/cache/hardware-nc-1.0/ietf-hardware@2018-03-13.yang.bak

printf "\n${PURPLE}###### Build the NED from the YANG data models\n${NC}"
ncs_cli -n -u admin -C << EOF
devtools true
paginate false
netconf-ned-builder project hardware 1.0 build-ned
show netconf-ned-builder project hardware 1.0 build-status
show netconf-ned-builder project hardware 1.0 module build-warning
show netconf-ned-builder project hardware 1.0 module build-error
unhide debug
show netconf-ned-builder project hardware 1.0 compiler-output
EOF

printf "\n\n${PURPLE}###### Export the NED to the packages directory\n${NC}"
MY_PWD=$(pwd)
MY_UID=$(id -u)
ncs_cli -n -u admin -C << EOF
devtools true
config
aaa authentication users user admin uid $MY_UID
commit
end
netconf-ned-builder project hardware 1.0 export-ned to-directory $MY_PWD/nso-rundir/packages
EOF

printf "\n\n${PURPLE}###### Load the NSO NETCONF NED package for the device\n${NC}"
ncs_cli -n -u admin -C << EOF
packages reload
show packages | nomore
EOF

printf "\n\n${PURPLE}###### Update the NED ID for the hw0 device to match the package NED ID and sync the device configuration to NSO\n${NC}"
ncs_cli -n -u admin -C << EOF
show packages package hardware-nc-1.0 component hardware ned netconf ned-id
config
devices device hw0 device-type netconf ned-id hardware-nc-1.0
commit
end
devices device hw0 sync-from
EOF

printf "\n\n${PURPLE}###### Show the device configuration\n${NC}"
echo "show running-config devices device hw0 config | nomore" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}###### Setup receiving NETCONF notifications for the \"hardware state\" and NETCONF streams as defined by the ietf-hardware.yang model\n${NC}"
ncs_cli -n -u admin -C << EOF
show devices device hw0 notifications stream | nomore
config
devices device hw0 notifications subscription hw-state-changes stream hardware_state local-user admin
commit
EOF

printf "\n\n${GREEN}###### NETCONF NED installed for the hw0 device. Continue demo how NSO can receive notifications, read operational state data, and configure the device through the NED\n${NC}"

printf "${GREEN}##### Showcase managing the device's simulated hardware system from NSO\n${NC}"
if [ $# -eq 0 ] ; then # Ask for input only if an argument was passed to this script
    printf "${PURPLE}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "\n\n${PURPLE}###### "Insert a new card to the subrack slot"\n${NC}"
echo ""
make -C devsim -f Makefile start-card

printf "\n${PURPLE}###### Show received device hardware-state-change notifications\n${NC}"
NOTIFS=""
HWSC="hardware-state-change"
while [[ $NOTIFS != *$HWSC* ]]; do
    printf "${RED}Waiting for NSO to receive the hardware-state-change notification\n${NC}"
    sleep 1
    NOTIFS=$(echo "show devices device hw0 notifications received-notifications | nomore" | ncs_cli -u admin -C)
done

ncs_cli -n -u admin -C << EOF
show devices device hw0 notifications received-notifications | nomore
devices device hw0 notifications received-notifications clear
EOF

printf "\n\n${PURPLE}###### Show the device "card" hardware operational state data\n${NC}"
echo "show devices device hw0 live-status hardware component | select class module | nomore" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}###### Show the device "card" hardware intended configuration\n${NC}"
echo "show running-config devices device hw0 config hardware component | select class module | nomore" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}###### The \"clone\" card is not configured by NSO yet, and is thus shown only in the device operational state\n${NC}"
printf "${PURPLE}###### Let's create the \"clone\" card and port device configuration and change the card operational state name to "hydrogen"\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device hw0 config hardware component hydrogen class module
parent slot-1-4-2
parent-rel-pos 1040200
alias clone
asset-id clone
uri [ urn:clone ]
commit dry-run outformat native
commit
EOF

printf "\n${PURPLE}###### Show received device hardware-state-change notifications\n${NC}"
NOTIFS=""
HWSC="hardware-state-change"
while [[ $NOTIFS != *$HWSC* ]]; do
    printf "${RED}Waiting for NSO to receive the hardware-state-change notification\n${NC}"
    sleep 1
    NOTIFS=$(echo "show devices device hw0 notifications received-notifications | nomore" | ncs_cli -u admin -C)
done
echo "show devices device hw0 notifications received-notifications | nomore" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}###### Show the device "card" hardware operational state data. The operational state card name was implemented by the device\n${NC}"
echo "show devices device hw0 live-status hardware component | select class module | nomore" | ncs_cli -n -u admin -C

printf "\n\n${GREEN}###### Done!\n${NC}"
