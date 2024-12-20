#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Scripting demo\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
ncs --stop &> /dev/null
ncs-netsim stop &> /dev/null
set -e
make clean

printf "\n${GREEN}##### Running the Example\n${NC}"
printf "${PURPLE}##### Start NSO\n${NC}"
ncs

printf "\n${PURPLE}##### Try out the new CLI command 'echo'\n${NC}"
ncs_cli -n -u admin -C << EOF
my script echo hello world
EOF

printf "\n\n${PURPLE}##### Try to change the trace-dir leaf to a value the scripts/policy/check_dir.sh policy script does not allow\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices global-settings trace-dir ./mybad
validate
EOF

printf "\n\n${PURPLE}##### Restart NSO in order to set the TEST_POST_COMMIT_SHOW_DIFF_FILE variable\n${NC}"
ncs --stop
export TEST_POST_COMMIT_SHOW_DIFF_FILE=my_trace_file.txt
ncs

printf "\n${PURPLE}##### Do a config change to trigger the scripts/post-commit/show_diff.sh post commit script\n${NC}"
ncs_cli -n -u admin -C << EOF
config
devices global-settings read-timeout 30
commit
EOF

printf "\n\n${PURPLE}##### Take a look at the side effect\n${NC}"
cat my_trace_file.txt

printf "\n${PURPLE}##### Reload the scripts to get information about them\n${NC}"
ncs_cli -n -u admin -C << EOF
script reload all | nomore
script reload all debug | nomore
EOF

printf "\n\n${GREEN}##### Cleanup\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO\n${NC}"
    ncs --stop
    printf "\n${GREEN}##### Reset the example to its original files\n${NC}"
    make clean
fi
rm -v $TEST_POST_COMMIT_SHOW_DIFF_FILE

printf "\n${GREEN}##### Done!\n${NC}"
