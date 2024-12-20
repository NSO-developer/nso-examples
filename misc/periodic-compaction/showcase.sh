#!/usr/bin/env bash

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

printf "\n${GREEN}##### Showcase: Configuring periodic compaction\n${NC}"

printf "\n${PURPLE}##### Step 1: Start NSO\n${NC}"
ncs

printf "\n${RED}##### Load compaction task package\n${NC}"
# load compaction task package
ncs_cli << EOF
request package reload
EOF

printf "\n${PURPLE}##### Step 2: set a scheduler task to trigger in the near future\n${NC}"

printf "\n${RED}##### Commit 10 transactions\n${NC}"
for d in $(seq 10); do ncs_cmd -c "mcreate /user{user$d}"; done

# For the purpose of the showcase a set time is preferable to a recurring
# schedule. To schedule the compaction to run e.g. on Sundays at midnight:
# set scheduler task test action-name compact action-node /compaction-task schedule "0 0 * * 0"
printf "\n${RED}##### set compaction task to trigger in 5 seconds\n${NC}"
OSTYPE=$(uname -s)
if [[ "$OSTYPE" == "Darwin"* ]]; then
   date=$(date -v +5S +"%FT%H:%M:%S")
else
   date=$(date -d "5 seconds" +"%FT%H:%M:%S")
fi
echo "will trigger compaction task on $date"
ncs_cli << EOF
configure
set scheduler task test action-name compact action-node /compaction-task time $date
commit
exit
EOF

printf "\n${PURPLE}##### Step 3: await compaction notification\n${NC}"
cd $EXAMPLE_DIR
make listener

printf "\n${GREEN}##### Done\n${NC}"
