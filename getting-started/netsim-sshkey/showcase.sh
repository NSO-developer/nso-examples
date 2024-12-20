#!/usr/bin/env bash

# NSO nano service example.
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

printf "\n${PURPLE}###### Reset and setup the example\n${NC}"

make stop &> /dev/null
make clean all start

printf "\n${PURPLE}##### Generate keys, distribute the public key and configure NSO for public key authentication with $1 network elements\n${NC}"

NES=""
i=0
while [ $i -lt $1 ]; do
    NES+="pubkey-dist key-auth ex$i admin remote-name admin authgroup-name ex$i-admin passphrase \"GThunberg18!\";"
    i=$(($i+1))
done

ncs_cli -n -u admin -C << EOF
devices sync-from
config
$NES
commit dry-run
commit
EOF

echo ""

NES=()
i=0
while [ $i -lt $1 ]; do
    NES+=("ex$i")
    i=$(($i+1))
done

for NE in "${NES[@]}"
do
    while : ; do
      arr=($(echo "show pubkey-dist key-auth $NE admin plan component self self state ready status" | ncs_cli -C -u admin))
      res=${arr[1]}
      if [ "$res" == "reached" ]; then
          printf "${GREEN}##### $NE deployed\n${NC}"
          break
      fi
      printf "${RED}##### Waiting for $NE to reach the ncs:ready state...\n${NC}"
      sleep 1
    done
done

printf "\n${PURPLE}###### Show the plan status\n${NC}"
ncs_cli -n -u admin -C << EOF
show pubkey-dist key-auth plan component | tab | nomore
EOF

printf "\n${PURPLE}###### Show the configuration added to NSO and network elements\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices authgroups group umap admin | nomore
show running-config devices device authgroup | nomore
show running-config devices device config aaa authentication users user admin authkey | nomore
EOF

printf "\n${PURPLE}###### The generated private and public keys\n${NC}"
ls -la *ed25519*

printf "\n${PURPLE}###### Delete the nano service (ctrl-c to abort) to go back from public key to password based network element authentication\n${NC}"
i=5
while [ $i -gt 0 ]; do
    printf "${PURPLE}$i\n${NC}"
    sleep 1
    i=$(($i-1))
done

ncs_cli -n -u admin -C << EOF
config
no pubkey-dist
commit dry-run
commit
EOF

echo ""
res="-1"
while [ "$res" != "0" ]; do
    arr=($(echo "show zombies service | icount" | ncs_cli -C -u admin))
    res=${arr[1]}
    printf "${RED}##### Waiting for $res nano service instances to be deleted...\n${NC}"
    sleep 1
done

printf "\n${PURPLE}###### Show the restored configuration for password authentication\n${NC}"
ncs_cli -n -u admin -C << EOF
show running-config devices authgroups group umap admin | nomore
show running-config devices device authgroup | nomore
show running-config devices device config aaa authentication users user admin authkey | nomore
EOF

printf "\n\n${GREEN}##### Done!\n${NC}"
