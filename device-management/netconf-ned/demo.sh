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

printf "\n\n${GREEN}*****This demo script will use the NSO netconf-console and ncs-make-package tools*****\n\n${NC}"

printf "\n${PURPLE}###### List the YANG version 1.0 models supported by the device using NETCONF hello message\n${NC}"
netconf-console --port $DEVICE_NETCONF_PORT --hello | grep "module="

printf "\n${PURPLE}###### List the YANG version 1.1 models supported by the device from the device yang-library\n${NC}"
netconf-console --port=$DEVICE_NETCONF_PORT --get -x /yang-library/module-set/module/name

printf "\n${PURPLE}###### We are interested in the ${GREEN}ietf-hardware.yang${PURPLE} model as we want to manage the device hardware\n${NC}"
printf "${PURPLE}###### Use the netconf-console NETCONF <get-schema> operation to get the ietf-hardware.yang model\n${NC}"
mkdir dev-yang
netconf-console --port=$DEVICE_NETCONF_PORT --get-schema=ietf-hardware > dev-yang/ietf-hardware.yang

printf "\n${PURPLE}###### The ietf-hardware.yang import a few YANG models\n${NC}"
cat dev-yang/ietf-hardware.yang | grep import

printf "\n${PURPLE}###### Two of the imported YANG models are shipped with NSO\n${NC}"
find ${NCS_DIR} \( -name "ietf-inet-types.yang" -o -name "ietf-yang-types.yang" -o -name "iana-hardware.yang" \)

printf "\n${PURPLE}###### Use the netconf-console NETCONF <get-schema> operation to get the ${GREEN}iana-hardware.yang${PURPLE} model\n${NC}"
netconf-console --port=$DEVICE_NETCONF_PORT --get-schema=iana-hardware > dev-yang/iana-hardware.yang

printf "\n${PURPLE}###### One YANG model, timestamp-hardware.yang, augment a node on to the ietf-hardware.yang model. This is not visible in the YANG library.\n${NC}"
printf "\n${PURPLE}###### Therefore, we either need be informed of the augment dependency or we need to download all YANG models and check for augments of the ietf-hardware.yang model to make use of the augmented node(s)\n${NC}"
netconf-console --port=$DEVICE_NETCONF_PORT --get-schema=timestamp-hardware > dev-yang/timestamp-hardware.yang

#Temporary NSO issue workaround (augments on notifications are removed when transformed)
sed -i.bak 's/entConfigChange";/entConfigChange"; leaf last-change { type yang:date-and-time; } /' dev-yang/ietf-hardware.yang; rm dev-yang/ietf-hardware.yang.bak

printf "\n${PURPLE}###### Setup the NSO run-time directory using the ncs-setup script\n${NC}"
ncs-setup  --no-netsim --dest nso-rundir

printf "\n${PURPLE}###### Create and build the NETCONF NED package from the device YANG models using the ncs-make-package script\n${NC}"
ncs-make-package --netconf-ned dev-yang --dest nso-rundir/packages/devsim --build --verbose --no-test --no-java --no-netsim --no-python --no-template --vendor "Tail-f" --package-version "1.0" devsim

printf "\n${PURPLE}###### Start NSO\n${NC}"
ncs --cd ./nso-rundir

printf "\n${PURPLE}###### To connect to the device, there needs to be a mapping between the NSO user and the device authentication\n${NC}"
printf "${PURPLE}###### This is controlled by configuring NSO authgroups\n${NC}"
printf "${PURPLE}###### The ${GREEN}default${PURPLE} group works here as our NSO ${GREEN}admin${PURPLE} user can authenticate as the device user ${GREEN}admin${NC} with pass ${GREEN}admin\n${NC}"
echo "show running-config devices authgroups group" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}###### Configure the NSO device connnection. For NED identity, select the identity created when building the package\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device hw0 address 127.0.0.1 port 12022 authgroup default
devices device hw0 trace pretty
state admin-state unlocked
device-type netconf ned-id devsim-nc-1.0
commit
EOF

printf "\n\n${PURPLE}###### Fetch the public SSH host key from the device and sync the configuration covered by the ietf-hardware.yang from the device\n${NC}"
ncs_cli -n -u admin -C << EOF
devices fetch-ssh-host-keys
device device hw0 sync-from
EOF

printf "\n\n${PURPLE}###### Show the device configuration\n${NC}"
echo "show running-config devices device hw0 config | nomore" | ncs_cli -n -u admin -C

printf "\n\n${PURPLE}###### Setup receiving NETCONF notifications for the \"hardware state\" NETCONF stream as defined by the ietf-hardware.yang model\n${NC}"
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

printf "\n${PURPLE}###### "Insert a new card into the chassis subrack slot"\n${NC}"
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

printf "\n\n${PURPLE}###### Show the device "card" hardware operational state data. The operational state card name was implemented by the device\n${NC}"
echo "show devices device hw0 live-status hardware component | select class module | nomore" | ncs_cli -n -u admin -C

printf "\n\n${GREEN}###### Done!\n${NC}"
