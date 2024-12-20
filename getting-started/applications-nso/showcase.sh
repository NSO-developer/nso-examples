#!/bin/sh

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
EXAMPLE_DIR=$(pwd)

printf "\n${GREEN}##### Setup the demo\n${NC}"

printf "\n${PURPLE}##### Make sure no previous NSO or netsim processes are running\n${NC}"
make stop &> /dev/null

printf "\n${PURPLE}##### Create an NSO local install with a fresh runtime directory\n\n${NC}"
make clean all

printf "\n${PURPLE}##### Have the environment variable NSO_RUNDIR point to the runtime directory\n${NC}"
export NSO_RUNDIR=$EXAMPLE_DIR/nso-lab-rundir

printf "\n${GREEN}##### Showcase: Implementing Device Count Action\n${NC}"

printf "\n${PURPLE}##### Step 1: Create a new Python package\n\n${NC}"
cd "$NSO_RUNDIR/packages"
ncs-make-package --service-skeleton python --action-example count-devices
sed -i.bak '/  description/,$d' count-devices/src/yang/count-devices.yang
printf "\n${RED}$(cat count-devices/src/yang/count-devices.yang)\n\n${NC}"

printf "\n${PURPLE}##### Step 2: Define a new action in YANG\n${NC}"
sed -i.bak '1 a\
  yang-version 1.1;' count-devices/src/yang/count-devices.yang
rm count-devices/src/yang/count-devices.yang.bak
printf "  container custom-actions {
    action count-devices {
      tailf:actionpoint count-devices-action;
      input {
        leaf in-subnet {
          type inet:ipv4-prefix;
        }
      }
      output {
        leaf result {
          type uint16;
        }
      }
    }
  }
}
" >> count-devices/src/yang/count-devices.yang
printf "\n${RED}$(cat count-devices/src/yang/count-devices.yang)\n\n${NC}"

printf "\n${PURPLE}##### Step 3: Implement the action logic\n${NC}"
printf "from ipaddress import IPv4Address, IPv4Network
import socket
import ncs
from ncs.dp import Action

class CountDevicesAction(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        count = 0
        root = ncs.maagic.get_root(trans)
        for device in root.devices.device:
            address = socket.gethostbyname(device.address)
            if IPv4Address(address) in IPv4Network(input.in_subnet):
                count = count + 1
        output.result = count
" > count-devices/python/count_devices/count_devices_action.py
printf "\n${RED}$(cat count-devices/python/count_devices/count_devices_action.py)\n\n${NC}"

printf "\n${PURPLE}##### Step 4: Register callback\n${NC}"
printf "import ncs
from .count_devices_action import CountDevicesAction

class Main(ncs.application.Application):
    def setup(self):
        self.register_action('count-devices-action', CountDevicesAction)
" > count-devices/python/count_devices/main.py
printf "\n${RED}$(cat count-devices/python/count_devices/main.py)\n\n${NC}"

printf "\n${PURPLE}##### Step 5: And... action!\n${NC}"
cd "$NSO_RUNDIR"
make -C packages/router/src

make -C packages/count-devices/src
ncs --with-package-reload
ncs_cli -n -C -u admin << EOF
custom-actions count-devices in-subnet 127.0.0.0/16
show devices list
config
devices device ex0 address localhost
commit
do show devices list
custom-actions count-devices in-subnet 127.0.0.0/16
EOF

printf "\n\n${GREEN}##### Done\n${NC}"
