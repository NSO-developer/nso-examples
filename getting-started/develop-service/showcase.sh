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
export NSO_RUNDIR="$EXAMPLE_DIR/nso-lab-rundir"
cd "$NSO_RUNDIR"

printf "\n${GREEN}##### Showcase: A Simple DNS Configuration Service\n${NC}"

printf "\n${PURPLE}##### Step 1: Prepare simulated routers\n\n${NC}"
make showcase-clean-start

printf "\n${PURPLE}##### Step 2: Create a service package\n${NC}"
cd "$NSO_RUNDIR/packages"
ncs-make-package --service-skeleton python dns-config

printf "\n${PURPLE}##### Step 3: Add the DNS server parameter\n${NC}"
sed -i.bak 's/leaf dummy {/leaf dns-server {/' dns-config/src/yang/dns-config.yang
rm dns-config/src/yang/dns-config.yang.bak
printf "\n${RED}$(cat dns-config/src/yang/dns-config.yang)\n\n${NC}"
make -C dns-config/src

printf "\n${PURPLE}##### Step 4: Add Python code\n${NC}"
sed -i.bak '/self\.log\.info(.Service create(service=., service._path, .).)/a\
\ \ \ \ \ \ \ \ dns_ip = service.dns_server\
\ \ \ \ \ \ \ \ ex1_device = root.devices.device["ex1"]\
\ \ \ \ \ \ \ \ ex1_config = ex1_device.config\
\ \ \ \ \ \ \ \ dns_server_list = ex1_config.sys.dns.server\
\ \ \ \ \ \ \ \ dns_server_list.create(dns_ip)
' dns-config/python/dns_config/main.py
rm -f dns-config/python/dns_config/main.py.bak
printf "\n${RED}$(cat dns-config/python/dns_config/main.py)\n${NC}"

printf "\n${PURPLE}##### Step 5: Deploy the service\n${NC}"
cd "$NSO_RUNDIR"
ncs
ncs_cli -n -C -u admin << EOF
packages reload
devices sync-from
config
dns-config test dns-server 192.0.2.1
commit dry-run
commit
dns-server 192.0.2.8
commit dry-run
commit
top
show full-configuration dns-config test
show full-configuration devices device ex1 config sys dns server
EOF

printf "\n\n${GREEN}##### Done\n${NC}"
read -n 1 -s -r -p "##### [Press ENTER to continue to the next showcase]"

printf "\n${GREEN}##### Setup the demo\n${NC}"
cd "$EXAMPLE_DIR"

printf "\n${PURPLE}##### Make sure no previous NSO or netsim processes are running\n${NC}"
make stop &> /dev/null

printf "\n${PURPLE}##### Create an NSO local install with a fresh runtime directory\n\n${NC}"
make clean all

printf "\n${PURPLE}##### Have the environment variable NSO_RUNDIR point to the runtime directory\n${NC}"
export NSO_RUNDIR="$EXAMPLE_DIR/nso-lab-rundir"

printf "\n${GREEN}##### Showcase: DNS Configuration Service with Templates\n${NC}"

printf "\n${PURPLE}##### Step 1: Prepare simulated routers\n\n${NC}"
cd "$NSO_RUNDIR"
make showcase-clean-start

printf "\n${PURPLE}##### Step 2: Create a service\n\n${NC}"
cd "$NSO_RUNDIR/packages"
ncs-make-package --build --service-skeleton python dns-config
sed -i.bak '/self\.log\.info(.Service create(service=., service._path, .).)/a\
\ \ \ \ \ \ \ \ template_vars = ncs.template.Variables()\
\ \ \ \ \ \ \ \ template_vars.add("DNS_IP", "192.0.2.1")\
\ \ \ \ \ \ \ \ template = ncs.template.Template(service)\
\ \ \ \ \ \ \ \ template.apply("dns-config-tpl", template_vars)
' dns-config/python/dns_config/main.py
rm -f dns-config/python/dns_config/main.py.bak
printf "\n${RED}$(cat dns-config/python/dns_config/main.py)\n${NC}"

printf "\n${PURPLE}##### Step 3: Create a template\n${NC}"
cd "$NSO_RUNDIR"
ncs --with-package-reload
ncs_cli -n -C -u admin << EOF
devices sync-from
show running-config devices device ex1 config sys dns
show running-config devices device ex1 config sys dns | display xml | save packages/dns-config/templates/dns-config-tpl.xml
EOF
sed -i.bak -e 's/<address>10.2.3.4<\/address>/<address>{$DNS_IP}<\/address>/' \
    -e 's/<name>ex1<\/name>/<name>{\/device}<\/name>/' \
    packages/dns-config/templates/dns-config-tpl.xml
rm -f packages/dns-config/templates/dns-config-tpl.xml.bak
printf "\n\n${RED}$(cat packages/dns-config/templates/dns-config-tpl.xml)\n\n${NC}"

printf "\n${PURPLE}##### Step 4: Test the service\n${NC}"
ncs_cli -n -C -u admin << EOF
packages package dns-config redeploy
config
dns-config dns-for-ex2 device ex2
commit dry-run
commit
EOF

printf "\n\n${GREEN}##### Done\n${NC}"
