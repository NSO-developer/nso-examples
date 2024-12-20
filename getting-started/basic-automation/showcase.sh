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

printf "\n${PURPLE}##### Create an NSO local install with a fresh runtime directory\n${NC}"
make clean all

printf "\n${PURPLE}##### Have the environment variable NSO_RUNDIR point to the runtime directory\n${NC}"
export NSO_RUNDIR=$EXAMPLE_DIR/nso-lab-rundir
cd "$NSO_RUNDIR"

printf "\n${GREEN}##### Showcase: Configuring DNS with Python\n${NC}"

printf "\n${PURPLE}##### Step 1: Start the routers\n\n${NC}"
make stop &> /dev/null
make clean all && ncs-netsim -a start
ncs

printf "\n${PURPLE}##### Step 2: Inspect the device data model\n${NC}"

ncs_cli -n -C -u admin << EOF
show devices list
devices sync-from
config
devices device ex1 config sys dns server 198.51.100.1
abort
exit
EOF

printf "\n${RED}packages/route-nc-1.1r/src/yang/router-dns.yang |grep -A7 \"list server\"\n${NC}"
cat packages/router-nc-1.1/src/yang/router-dns.yang |grep -A7 "list server"

printf "\n\n${PURPLE}##### Step 3: Create the script\n${NC}"
printf "import ncs
with ncs.maapi.single_write_trans('admin', 'python') as t:
    root = ncs.maagic.get_root(t)
    dns_server_list = root.devices.device['ex1'].config.sys.dns.server
    dns_server_list.create('192.0.2.1')
    params = t.get_params()
    params.dry_run_native()
    result = t.apply_params(True, params)
    print(result['device']['ex1'])
    t.apply_params(True, t.get_params())
    print('Done!')
" > ex1-dns.py
printf "\n${RED}$(cat ex1-dns.py)${NC}"

printf "\n\n${PURPLE}##### Step 4: Run and verify the script\n\n${NC}"
python3 ex1-dns.py

ncs_cli -n -C -u admin << EOF
show running-config devices device ex1 config sys dns server
EOF

printf "\n\n${GREEN}##### Done\n${NC}"
