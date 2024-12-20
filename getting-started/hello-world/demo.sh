#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "${GREEN}##### Hello world demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
set -e
rm -rf nso-rundir

printf "${PURPLE}##### Create an 'nso-rundir' directory and run 'ncs-setup' to create the directories and files needed\n${NC}"
mkdir nso-rundir
ls -1a nso-rundir
ncs-setup --dest nso-rundir
ls -1a nso-rundir

printf "${PURPLE}##### Start the NSO daemon\n${NC}"
ncs --cd nso-rundir -c $(pwd)/nso-rundir/ncs.conf

printf "${PURPLE}##### Verify the NSO version status using the 'ncs --status | grep vsn:' command\n${NC}"
ncs --status | grep "vsn:"

printf "${PURPLE}##### Verify the NSO version status from the NSO CLI using the 'ncs_cli' command\n${NC}"
echo "show ncs-state version | nomore" | ncs_cli -n -u admin -C

if hash ssh 2> /dev/null && hash ssh-keygen 2> /dev/null; then
    printf "\n${PURPLE}##### Create SSH client keys\n${NC}"
    ssh-keygen -N "" -t ed25519 -m pem -f nso-rundir/id_ed25519
    mkdir -p nso-rundir/admin/.ssh
    cp nso-rundir/id_ed25519.pub nso-rundir/admin/.ssh/authorized_keys

    printf "${PURPLE}##### Create a known_hosts key file for the SSH client\n${NC}"
    HOST_KEY=$(cat ${NCS_DIR}/etc/ncs/ssh/ssh_host_ed25519_key.pub | cut -d ' ' -f1-2)
    echo "[localhost]:2024 $HOST_KEY" >> nso-rundir/known_hosts
    cat nso-rundir/known_hosts
    ssh-keygen -Hf nso-rundir/known_hosts

    printf "${PURPLE}##### Configure NSO with where the NSO SSH server 'ssh_keydir' with the authorized_keys for the admin user is located\n${NC}"
    ncs_cli -n -u admin -C << EOF
config
aaa authentication users user admin ssh_keydir $(pwd)/nso-rundir/admin/.ssh
commit
EOF

    printf "\n${PURPLE}##### Connect to the NSO CLI through the NSO SSH server using the SSH command and verify the NSO version status\n${NC}"
    ssh -i nso-rundir/id_ed25519 -o UserKnownHostsFile=$(pwd)/nso-rundir/known_hosts -l admin -p 2024 localhost "show ncs-state version | nomore"
else
    printf "${RED}##### No 'ssh' and/or 'ssh-keygen' command, skip connecting to the NSO SSH server\n${NC}"
fi

printf "${PURPLE}##### Print the 'logs/ncs.log' log file\n${NC}"
cat nso-rundir/logs/ncs.log

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop the NSO daemon by using the 'ncs --stop' option:\n${NC}"
    ncs --stop
    printf "\n${PURPLE}##### Use the '--reset' option with the 'ncs-setup' script\n${NC}"
    ncs-setup --reset --dest nso-rundir

    printf "\n${PURPLE}##### Reset the example to its original files\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    rm -rf nso-rundir
fi

printf "\n${GREEN}##### Done!\n${NC}"
