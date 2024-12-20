#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### SSH keys demo\n${NC}"
printf "${GREEN}##### Start clean${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
rm -rf netsim README.ncs README.netsim host logs ncs-cdb ncs.conf packages
rm -rf scripts state

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Create a package called 'host' from the YANG file\n${NC}"
ncs-make-package --netconf-ned . host

printf "\n${PURPLE}##### Build the YANG file in the package\n${NC}"
make -C host/src

printf "\n${PURPLE}##### Create a simulated network with one device\n${NC}"
ncs-netsim create-network host 1 h

printf "\n${PURPLE}##### Add one instance of the cisco-ios device to the simulated network\n${NC}"
ncs-netsim add-to-network $NCS_DIR/packages/neds/cisco-ios-cli-3.0 1 c

printf "\n${PURPLE}##### Create an NSO setup to use the simulated network\n${NC}"
ncs-setup --netsim-dir ./netsim --dest .

printf "\n${PURPLE}##### Start everything\n${NC}"
ncs-netsim start
ncs

printf "\n${PURPLE}##### Show the host keys used by NSO from the NSO CLI\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices device * ssh | nomore
EOF

printf "\n\n${PURPLE}##### To simulate using real devices, first disconnect from the devices and delete the keys\n${NC}"
ncs_cli -n -u admin -C << EOF
devices disconnect
config
no devices device * ssh host-key
commit
devices connect
EOF

printf "\n\n${PURPLE}##### Configure an ED25519 key for device c0\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device c0 ssh host-key ssh-ed25519 key-data "---- BEGIN SSH2 PUBLIC KEY ----\n\
Comment: \"ED25519, converted by per@mars.tail-f.com from OpenSSH\"\n\
AAAAC3NzaC1lZDI1NTE5AAAAIFM3gvv8XwVIvdEQ2iGdHPQ1O7dTjXW1fwbl0pLv4off\n\
---- END SSH2 PUBLIC KEY ----\n"
commit
devices device c0 connect
EOF

printf "\n\n${PURPLE}##### Fetch the keys for all device\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device * ssh fetch-host-keys
EOF

printf "\n\n${PURPLE}##### Verify that successful connections can be made\n${NC}"
ncs_cli -n -u admin -C << EOF
devices connect
EOF

printf "\n\n${PURPLE}##### Show the fingerprints of already configured keys\n${NC}"
ncs_cli -n -u admin -C << EOF
devices device * ssh host-key * show-fingerprint
EOF

printf "\n\n${GREEN}##### Modifying the Host Key Verification Level\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Disable host key verification globally - ${RED}Development only. Not good for security\n${NC}"
ncs_cli -n -u admin -C << EOF
config
ssh host-key-verification none
commit
EOF

printf "\n\n${PURPLE}##### Configure device h0 with the "reject-mismatch" setting - ${RED}Development only. Not good for security\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices device h0 ssh host-key-verification reject-mismatch
commit
EOF

printf "\n\n${PURPLE}##### Change back to "reject-unknown" globally\n${NC}"
ncs_cli -n -u admin -C << EOF
config
no devices device h0 ssh host-key-verification
ssh host-key-verification reject-unknown
commit
EOF

printf "\n\n${GREEN}##### Publickey Authentication for Devices\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi

printf "\n${PURPLE}##### Connect to the CLI of the h0 device and configure its ssh_keydir\n${NC}"
ncs-netsim cli-c h0 << EOF
config
aaa authentication users user admin ssh_keydir home/admin/.ssh
commit
EOF

printf "\n${PURPLE}##### Create and populate the .aah directory\n${NC}"
mkdir -p netsim/h/h0/home/admin/.ssh
cp id_ed25519.pub netsim/h/h0/home/admin/.ssh/authorized_keys

printf "\n${PURPLE}##### Configure NSO to use the corresponding private key\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices authgroups group default umap admin public-key
commit
EOF

printf "\n\n${PURPLE}##### Verify that the default settings use the intended private key file\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices authgroups group default umap admin public-key | details
EOF

printf "\n\n${PURPLE}##### Configure a private key in CDB\n${NC}"
ncs_cli -n -u admin -C << EOF
file show id_ed25519
config
ssh private-key admin key-data "-----BEGIN OPENSSH PRIVATE KEY-----\n\
b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABCc5s4icM\n\
8h0SVxG52a46EpAAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIFM3gvv8XwVIvdEQ\n\
2iGdHPQ1O7dTjXW1fwbl0pLv4offAAAAoDbnwxKqWaPuq0/M9MIbA9YHVQzc4GxPBs/gUF\n\
5AEG9zc+H24E0fQkqXTcJKpLHFpe5NsYOJBQAyN4wn9ojmXaXXOZ4dbwoHh/Iup9anm4pw\n\
JT1BJnMJcg/svr6vrCf1Rk4KOk9ac/exuHqqhkshl0aVoz7OqoxALWDcfChDBgogIhgnYX\n\
0q3x8Q7t6t0oAk76uenFKOW4XZ2puI+6QyPRQ=\n\
-----END OPENSSH PRIVATE KEY-----\n"
ssh private-key admin passphrase secret
show full-configuration ssh private-key | nomore
commit
EOF

printf "\n\n${PURPLE}##### Select using the "admin" key for the "admin" user\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices authgroups group default umap admin public-key private-key name admin
commit
EOF

printf "\n\n${PURPLE}##### Verify authenticating with device h0 using the private key\n${NC}"
ncs_cli -n -u admin -C << EOF
devices disconnect
device device h0 connect
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO, netsim, and clean all created files\n${NC}"
    ncs-netsim stop
    ncs --stop
    rm -rf netsim README.ncs README.netsim host logs ncs-cdb ncs.conf packages
    rm -rf scripts state
fi

printf "\n${GREEN}##### Done!\n${NC}"
