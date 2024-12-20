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

printf "\n${GREEN}##### Showcase: Extending the CDB with Packages\n${NC}"

printf "\n${PURPLE}##### Step 1: Create a package\n\n${NC}"
ncs-make-package --service-skeleton python --build --dest packages/my-data-entries my-data-entries

printf "\n${PURPLE}##### Step 2: Add package to NSO\n${NC}"
ncs
ncs_cli -n -Cu admin << EOF
packages reload
show my-data-entries
EOF

printf "\n\n${PURPLE}##### Step 3: Set data\n${NC}"
ncs_cli -n -Cu admin << EOF
config
my-data-entries "entry number 1"
dummy 0.0.0.0
abort
EOF

printf "\n\n${PURPLE}##### Step 4: Inspect the YANG module\n${NC}"
ncs_cli -n -Cu admin << EOF
file show packages/my-data-entries/src/yang/my-data-entries.yang | nomore
show ncs-state loaded-data-models data-model my-data-entries | nomore
EOF

printf "\n\n${GREEN}##### Showcase: Building and Testing a Model\n${NC}"

printf "\n${PURPLE}##### Step 1: Create a model skeleton\n${NC}"
printf "${PURPLE}##### Note: Created the service skeleton package in step 1 of the previous showcase\n${NC}"
cd "$NSO_RUNDIR/packages/my-data-entries"
printf "
module my-test-model {
  namespace \"http://example.tail-f.com/my-test-model\";
  prefix \"t\";
" > src/yang/my-test-model.yang
printf "${RED}$(cat src/yang/my-test-model.yang)\n${NC}"

printf "\n${PURPLE}##### Step 2: Fill out the model\n${NC}"
printf "
  container host {
    leaf host-name {
      type string;
      description \"Hostname for this system\";
    }
    leaf-list domains {
      type string;
      description \"My favourite internet domains\";
    }
    container server-admin {
      description \"Administrator contact for this system\";
      leaf name {
        type string;
      }
    }
    list user-info {
      description \"Information about team members\";
      key \"name\";
      leaf name {
        type string;
      }
      leaf expertise {
        type string;
      }
    }
  }
}
" >> src/yang/my-test-model.yang
printf "${RED}$(cat src/yang/my-test-model.yang)\n${NC}"

printf "\n${PURPLE}##### Step 3: Compile and load the model\n\n${NC}"
make -C src/
cd "$NSO_RUNDIR"
ncs_cli -n -C -u admin << EOF
packages reload
EOF

printf "\n\n${PURPLE}##### Step 4: Test the model\n${NC}"
ncs_cli -n -Cu admin << EOF
config
host host-name my-host
host domains [ tail-f.com tail-f.se ]
host server-admin name Ingrid
host user-info Greta
expertise sustainability
host user-info Gunvald
expertise security
top
show configuration
commit
show full-configuration host | display xml | save cdb-init.xml
EOF

printf "\n\n${RED}cat cdb-init.xml\n${NC}"
cat cdb-init.xml

printf "\n${RED}ncs_load -Fp -p /host\n${NC}"
ncs_load -Fp -p /host

printf "\n${RED}ncs_load -Fc -p /host\n${NC}"
ncs_load -Fc -p /host

ncs_cli -n -Cu admin << EOF
show running-config host
config
no host
commit
show full-configuration host
load merge cdb-init.xml
commit dry-run
commit
show full-configuration host
EOF

printf "\n\n${GREEN}##### Done\n${NC}"
