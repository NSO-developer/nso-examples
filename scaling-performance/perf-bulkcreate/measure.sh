#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

if hash gdate 2> /dev/null; then
    DATE=gdate
else
    DATE=date
fi

USAGE="$0 [-d <num_devs> -r <num_routes> -t <test> -q <use_cq> -n <no_networking> -h <help>]"

# Default settings for the showcase
NUM_CPU=$(python3 -c "import os; print(os.cpu_count())")
NUM_DEVS=1 # Number of devices simulated using netsim
RUN_ID="$($DATE +%s)" # For progress trace CSV file name
NUM_ROUTES=10 # Total number of rules
TEST="py_setvals_xml"
USE_CQ=false # Use commit queues
NO_NETWORKING=false
NONINTERACTIVE=${NONINTERACTIVE-}

while getopts ':d:r:t:q:n:' opt
do
    case $opt in
        d) NUM_DEVS=${OPTARG};;
        r) NUM_ROUTES=${OPTARG};;
        t) TEST=${OPTARG};;
        q) USE_CQ=${OPTARG};;
        n) NO_NETWORKING=${OPTARG};;
       \?) echo "$USAGE"
           exit 1;;
    esac
done

printf "\n${PURPLE}##### Reset and setup the example\n${NC}"
make stop clean TEST=$TEST NDEVS=$NUM_DEVS all start #&> /dev/null

MN=""
if [ -z "$NONINTERACTIVE" ]; then
    MN="-n"
fi

if [ $NO_NETWORKING == true ]; then
    NO_NETWORKING=" no-networking"
else
    NO_NETWORKING=
fi

if [ $USE_CQ == true ]; then
    printf "\n${PURPLE}##### Configure the default commit-queue settings${NC}"
    ncs_cli $MN -u admin -C << EOF
config
devices global-settings commit-queue enabled-by-default true
devices global-settings commit-queue sync
commit
EOF
fi

printf "\n${PURPLE}##### Enable the NSO progress trace\n${NC}"
ncs_cli $MN -u admin -C << EOF
unhide debug
config
services global-settings service-callback-timeout 36000
progress trace progress-trace-$RUN_ID
enabled destination file progress-$RUN_ID.csv format csv
verbosity very-verbose
commit
EOF

printf "\n${PURPLE}##### With NSO $(ncs --version) commit $NUM_ROUTES rules$NO_NETWORKING\n${NC}"
if [ $TEST == "j_setvals_xml" ]; then
    printf "${PURPLE}##### Using Java MAAPI sharedSetValues(String XML) for service create mapping\n${NC}"
elif [ $TEST == "py_setvals_xml" ]; then
    printf "${PURPLE}##### Using Python MAAPI XML shared_set_values() for service create mapping\n${NC}"
elif [ $TEST == "py_create" ]; then
    printf "${PURPLE}##### Using Python MAAPI create() for service create mapping\n${NC}"
elif [ $TEST == "py_mload_xml" ]; then
    printf "${PURPLE}##### Using Python MAAPI load_config_cmds() for service create mapping\n${NC}"
elif [ $TEST == "java_mload_xml" ]; then
    printf "${PURPLE}##### Using Java MAAPI loadConfigCmds() for service create mapping\n${NC}"
elif [ $TEST == "py_template" ]; then
    printf "${PURPLE}##### Using Python MAAPI and a configuration template for service create mapping\n${NC}"
else
    printf "${RED}##### Unknown method for service create mapping\n${NC}"
    exit 1
fi

START=$($DATE +%s)
for ((n=0;n<$NUM_DEVS;n++)); do
    echo "config;rfs-routes route asa$n num-routes $NUM_ROUTES;commit$NO_NETWORKING" | ncs_cli -u admin -C & CMD_PIDS+=($!)
done
wait "${CMD_PIDS[@]}"

if [ $USE_CQ == true ]; then
    while : ; do
        arr=($(echo "show devices commit-queue queue-item | icount" | ncs_cli -C -u admin))
        res=${arr[1]}
        if [ "$res" == "0" ]; then
            break
        fi
        printf "${RED}##### Waiting for $res commit queue items to complete...\n${NC}"
        sleep .1
    done
    arr=($(echo "show devices commit-queue completed queue-item failed | icount" | ncs_cli -C -u admin))
    res=${arr[1]}
    if [ ! "$res" == "0" ]; then
        printf "${RED}##### $res commit queue items failed!\n${NC}"
    else
        printf "${GREEN}##### All commit queue items completed\n${NC}"
    fi
fi

END=$($DATE +%s)
TIME=$(($END-$START))

printf "\n${PURPLE}##### Disable the NSO progress trace\n${NC}"
ncs_cli $MN -u admin -C << EOF
unhide debug
config
progress trace progress-trace-$RUN_ID disabled
commit
EOF

printf "\n${PURPLE}##### Total wall-clock time: ${GREEN}$TIME s\n${NC}"
printf "\n${PURPLE}##### Progress trace written to nso-rundir/logs/progress-$RUN_ID.csv\n\n${NC}"

if [ -z "$NONINTERACTIVE" ]; then
    printf "${PURPLE}##### Show a graph representation of the progress trace\n${NC}"
    while : ; do
        NUM=$(cat nso-rundir/logs/progress-$RUN_ID.csv | grep "switch to new running" | grep stop | wc -l)
        NUM2=$(( $NUM_DEVS + 1 ))
        if [ $NUM == $NUM2 ] ; then
            break
        fi
        sleep 1
    done
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r </dev/tty
    python3 ../../common/simple_progress_trace_viewer.py nso-rundir/logs/progress-$RUN_ID.csv
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r </dev/tty
fi
printf "\n${GREEN}##### Done!\n\n${NC}"
