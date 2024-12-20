#!/usr/bin/env bash

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable has not been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
CYAN="\033[0;36m"
NC='\033[0m' # No Color


# Helper that waits until given text shows up in a file
tailgrep() {
        arg1=$1 ; shift
        arg2=$1 ; shift
        # Sometimes `(tail ... &) | grep` alone would produce the
        # error "write error: Resource temporarily unavailable" on
        # next output, so avoid printing errors and properly clean up
        (
                set -e
                pid=$(
                        ( (
                                tail -f $arg1 "$arg2" 2>/dev/null &
                                echo $! >&2
                        ) | grep "$@" 2>/dev/null ) 3>&2 2>&1 1>&3
                )
                [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
        ) 2>&1
}


printf "\n\n${GREEN}##### Showcase: Trigger conflicts that NSO handle through the service automatic retry mechanism\n${NC}"

printf "\n\n${PURPLE}##### Show devices and their initial DNS server config\n${NC}"
ncs_cli -n -C -u admin << EOF
show devices list | nomore
devices sync-from
show running-config devices device config sys dns | nomore
show running-config devices device config sys ntp | nomore
EOF

printf "\n\n${PURPLE}##### Acquire the semaphore using an action\n${NC}"
ncs_cli -n -C -u admin << EOF
servers acquire-sem
EOF

printf "\n\n${PURPLE}##### Create a DNS server service instance\n${NC}"
echo "config; dns-configs dns-config my-cfg device [ ex0 ex1 ex2 ] server server1; commit" | ncs_cli -C -u admin &
echo "DNS server service instance is being created"

printf "${CYAN}"
tailgrep -n10 logs/ncs-java-vm.log -m1 "DNS service - try acquiring the semaphore"
printf "${NC}"

printf "\n${PURPLE}##### Update the dual purpose DNS NTP server list while the service instance is still being created to cause a conflict\n${NC}"
ncs_cli -n -C -u admin << EOF
config
servers server server1 ip 21.21.21.21
commit
EOF

printf "\n\n${PURPLE}##### Create an NTP server service instance. Due to the YANG ncs:conflicts-with extension statement it will wait for the DNS service to finish, but will run before the DNS service retry\n${NC}"
echo "config; ntp-configs ntp-config my-cfg device [ ex0 ex1 ex2 ] server server1; commit" | ncs_cli -C -u admin &
echo "NTP service instance is being created"

printf "\n${PURPLE}##### Sleep 5 seconds to highlight how the NTP server service will wait for the DNS service to finish due to the YANG extension ncs:conflicts-with statement\n${NC}"
sleep 5

printf "\n${PURPLE}##### Release the semaphore to allow the DNS service to finish followed by the NTP service\n${NC}"
ncs_cli -n -C -u admin << EOF
servers release-sem
EOF

printf "\n${PURPLE}##### Once the NTP service finishes, the DNS service will retry, followed by an NTP service retry. See the NTP service Java code for why it too retries\n${NC}"

printf "\n\n${PURPLE}##### Print the contents of the devel.log that describes the conflicts retries\n${NC}"
printf "${CYAN}"
tailgrep -n+1 logs/devel.log -m6 "conflict on\|retrying\|conflict found"
printf "${NC}"

printf "\n\n${PURPLE}##### Print the contents of the Java 'server' service log for finished create callbacks\n${NC}"
printf "${CYAN}"
tailgrep -n10 logs/ncs-java-vm.log -m4 "Service create done"
printf "${NC}"

printf "\n\n${PURPLE}##### Show that the DNS and NTP server addresses on devices has changed since the NSO service mapping takes care of conflicts\n${NC}"

ncs_cli -n -C -u admin << EOF
show running-config dns-configs
show running-config ntp-configs
show running-config servers
show running-config devices device config sys dns | nomore
show running-config devices device config sys ntp | nomore
exit
EOF

printf "\n\n${GREEN}##### Showcase done: Service automatic retry\n${NC}"

printf "\n\n${GREEN}##### Showcase: Trigger a conflict that NSO handle through the CLI automatic rebase & retry mechanism when the resulting configuration is the same despite the conflict\n${NC}"

printf "\n\n${PURPLE}##### Configure some description text\n${NC}"
ncs_cli -n -C -u admin << EOF
config
servers server server1 description "some text" extended-description "some more text"
commit
EOF

printf "\n\n${PURPLE}##### Delete the IP address from server1 to trigger the description leafs when expression and wait 5 seconds before committing\n${NC}"
echo "config; no servers server server1 ip; sleep 5; commit" | ncs_cli -C -u admin &

printf "\n\n${PURPLE}##### Wait for the CLI sleep command to be logged in the audit log\n${NC}"
printf "${CYAN}"
tailgrep -n1 logs/audit.log -m1 "sleep 5"
printf "\n${NC}"

printf "\n${PURPLE}##### Use MAAPI to delete the description leaf to trigger the extended-description when expression before CLI command commit to cause a conflict\n${NC}"
ncs_cmd -c 'mdel "/servers/server{server1}/description"'

printf "\n\n${PURPLE}##### Wait for the CLI commit command to be logged in the audit log\n${NC}"
printf "${CYAN}"
tailgrep -n1 logs/audit.log -m1 "commit"
printf "\n${NC}"

printf "\n  ${PURPLE}##### Print the contents of the devel.log that describes the conflict rebase and retry\n${NC}"
printf "${CYAN}"
tailgrep -n+1 logs/devel.log -a -A1 -B1 -m1 "/servers/server\[name='server1'\]/description"
tailgrep -n+1 logs/devel.log -B5 -m1 "retrying transaction after rebase"
printf "${NC}"

printf "\n\n${GREEN}##### Showcase done: CLI automatic rebase & retry\n${NC}"

printf "\n\n${GREEN}##### Showcase: Trigger a conflict that NSO handle through the JSON-RPC automatic rebase & retry mechanism when the resulting configuration is the same despite the conflict\n${NC}"

printf "\n\n${PURPLE}##### Configure some description text and restore server ip\n${NC}"
ncs_cli -n -C -u admin << EOF
config
servers server server3 description "some text" extended-description "some more text"
commit
EOF

printf "\n\n${PURPLE}##### Delete the IP address from server3 to trigger the description leafs when expression and wait 5 seconds before committing\n${NC}"
./jsonrpc.py &

printf "\n\n${PURPLE}##### Wait for the webui changeset validation to be logged in the devel log\n${NC}"
printf "${CYAN}"
tailgrep -n1 logs/devel.log -m1 "run validation over the changeset: ok"
printf "\n${NC}"

printf "\n${PURPLE}##### Use MAAPI to delete the description leaf to trigger the extended-description when expression before CLI command commit to cause a conflict\n${NC}"
ncs_cmd -c 'mdel "/servers/server{server3}/description"'

printf "\n\n${PURPLE}##### Wait for the webui session terminated to be logged in the audit log\n${NC}"
printf "${CYAN}"
tailgrep -n1 logs/audit.log -m1 "terminated session"
printf "\n${NC}"

printf "\n  ${PURPLE}##### Print the contents of the devel.log that describes the conflict rebase and retry\n${NC}"
printf "${CYAN}"
cat logs/devel.log | grep -a -A1 -B1 "conflict on: /servers/server\[name='server3'\]" | grep -A1 -B1 "/description"
LINES=$(cat logs/devel.log | grep -m1 -n "webui" | cut -d : -f 1)
tailgrep -n+$LINES logs/devel.log -B5 -m1 "retrying transaction after rebase"
printf "${NC}"

printf "\n\n${GREEN}##### Showcase done: JSON-RPC automatic rebase & retry\n${NC}"

printf "\n\n${GREEN}##### Showcase: Trigger conflicts handled using the NSO Java API for the retry mechanism\n${NC}"

printf "\n\n${PURPLE}##### Show the devices initial NTP server config and reset the server list\n${NC}"
ncs_cli -n -C -u admin << EOF
show running-config devices device config sys ntp | nomore
config
servers server server1 ip 11.11.11.11
servers server server2 ip 22.22.22.22
commit
EOF

printf "\n\n${PURPLE}##### Acquire the semaphore using an action\n${NC}"
ncs_cli -n -C -u admin << EOF
servers acquire-sem
EOF

printf "\n\n${PURPLE}##### Create an NTP server instance using an action ${RED}*without* retry${PURPLE} - will fail\n${NC}"
echo "servers update-ntp device ex0 server server1" | ncs_cli -C -u admin &
echo "NTP server instance is being created"

printf "${CYAN}"
tailgrep -n10 logs/ncs-java-vm.log -m1 "Update NTP without retry action - try acquiring the semaphore"
printf "${NC}"

printf "\n${PURPLE}##### Update the dual purpose DNS NTP server list while the 'update-ntp' action is waiting for the semaphore to cause a conflict\n${NC}"
ncs_cli -n -C -u admin << EOF
config
servers server server1 ip 31.31.31.31
commit
EOF

printf "\n\n${PURPLE}##### Release the semaphore to allow the 'update-ntp' action Java program to finish and NSO to detect the conflict and abort the transaction\n${NC}"
ncs_cli -n -C -u admin << EOF
servers release-sem
EOF

printf "\n${CYAN}"
tailgrep -n10 logs/ncs-java-vm.log -m1 "Cannot apply trans"
cat logs/devel.log | grep -a -A3 "conflict on"
printf "${NC}"

printf "\n\n${PURPLE}##### Show that the devices NTP server config did not change due to the server list conflict\n${NC}"
ncs_cli -n -C -u admin << EOF
show running-config devices device config sys ntp | nomore
show running-config servers | nomore
EOF

printf "\n\n${PURPLE}##### Again acquire the semaphore using an action\n${NC}"
ncs_cli -n -C -u admin << EOF
servers acquire-sem
EOF

printf "\n\n${PURPLE}##### Create an NTP server instance using an action ${RED}*with* retry${PURPLE} - will succeed after a retry\n${NC}"
echo "servers update-ntp-retry device ex0 server server2" | ncs_cli -C -u admin &
echo "NTP server instance is being created"

printf "${CYAN}"
tailgrep -n10 logs/ncs-java-vm.log -m1 "Update NTP with retry action - try acquiring the semaphore"
printf "${NC}"

printf "\n${PURPLE}##### Update the dual purpose DNS NTP server list while while the 'update-ntp-retry' action is waiting for the semaphore to cause a conflict\n${NC}"
ncs_cli -n -C -u admin << EOF
config
servers server server2 ip 32.32.32.32
commit
EOF

printf "\n\n${PURPLE}##### Release the semaphore to allow the 'update-ntp-retry' action Java program to finish. NSO will detect the conflict and call the retry function\n${NC}"
ncs_cli -n -C -u admin << EOF
servers release-sem
EOF

printf "\n${CYAN}"
tailgrep -n10 logs/ncs-java-vm.log -m2 "Update NTP with retry action - done" | tail -1
cat logs/devel.log | grep -a -A3 "conflict on" | tail -4
printf "${NC}"

printf "\n\n${PURPLE}##### Show that the devices NTP server config changed after the retry\n${NC}"
ncs_cli -n -C -u admin << EOF
show running-config devices device config sys ntp | nomore
show running-config servers | nomore
EOF

printf "\n\n${GREEN}##### Showcase done: Java API retry\n${NC}"
