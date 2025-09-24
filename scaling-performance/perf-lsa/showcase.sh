#!/usr/bin/env bash

if [ -n "${NCS_IPC_PATH}" ]; then
ENV="NCS_IPC_PATH=${NCS_IPC_PATH}."
else
ENV="NCS_IPC_PORT="
fi

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

USAGE="$0 [-d <ldevs> -t <ntrans> -w <nwork> -r <ndtrans> -q <usecq> -y <ddelay> -h <help>]"

# Default settings for the showcase
NUM_CPU=$(python3 -c "import os; print(os.cpu_count())")
LDEVS=2 # Number of devices simulated per lower node
NDEVS=$(($LDEVS * 2))
RUNID="$($DATE +%s)" # An ID used as dummy data, here a timestamp
NTRANS=$LDEVS # Number of transactions used per lower node. Here, one per device.
NWORK=3 # Simulated work per transaction in the RFS service configure and validation states
NDTRANS=1 # Number of devices the RFS service will configure per service transaction.
USECQ="True" # Use commit queues on the lower devices
DEV_DELAY=1 # Simulated work on devices in seconds
NONINTERACTIVE=${NONINTERACTIVE-}

while getopts ':d:t:w:r:q:y:' opt
do
    case $opt in
        d) LDEVS=${OPTARG};;
        t) NTRANS=${OPTARG};;
        w) NWORK=${OPTARG};;
        r) NDTRANS=${OPTARG};;
        q) USECQ=${OPTARG};;
        y) DEV_DELAY=${OPTARG};;
       \?) echo "ERROR: Invalid option: $USAGE"
           exit 1;;
    esac
done

printf "\n${PURPLE}##### Reset and setup the example\n${NC}"
make stop &> /dev/null
make clean LDEVS=$LDEVS all start

env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cluster device-notifications enabled
cluster remote-node lower-nso-1 address 127.0.0.1 port 2023 authgroup default username admin trace pretty
cluster remote-node lower-nso-2 address 127.0.0.1 port 2024 authgroup default username admin trace pretty
cluster commit-queue enabled
cluster remote-node * ssh fetch-host-keys
devices device * out-of-sync-commit-behaviour accept
commit
EOF

printf "\n\n${PURPLE}##### Configure the device delay, i.e., simulated device work and calibrate the CPU time${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
config
cfs-t3s dev-settings dev-delay $DEV_DELAY
commit
EOF

env ${ENV}4570 ncs_cmd -dd -c "maction /t3s/calibrate-cpu-time"
env ${ENV}4571 ncs_cmd -dd -c "maction /t3s/calibrate-cpu-time"

printf "\n\n${PURPLE}##### Enable the NSO progress trace${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
unhide debug
config
devices device * config progress trace t3-trace-$RUNID enabled verbosity normal destination format csv file t3-$RUNID.csv
top
progress trace t3-trace-$RUNID enabled verbosity normal destination file t3-$RUNID.csv format csv
commit
EOF

CQSYNC=""
if [ $USECQ == "True" ]; then
    printf "\n\n${PURPLE}##### Use commit-queue sync${NC}"
    CQSYNC=" commit-queue sync"
fi

printf "\n\n${PURPLE}##### Run a test with $NTRANS transactions per lower NSO with $NDTRANS transactions per lower NSO device\n${NC}"
printf "${PURPLE}##### run-id $RUNID on a processor with $NUM_CPU cores${NC}"
START=$($DATE +%s)
env ${ENV}4569 ncs_cli -n -u admin -C <<EOF
config
cfs-t3s t3-settings ntrans $NTRANS nwork $NWORK ndtrans $NDTRANS run-id $RUNID
commit$CQSYNC
EOF

printf "\n\n${PURPLE}##### Wait for the lower nodes nano service plan to reach ready status\n${NC}"
for (( n=1; n<=2; n++ ))
do
    for (( i=0; i<$NTRANS; i++ ))
    do
        while : ; do
            arr=($(echo "show devices device lower-nso-$n live-status t3s t3 $i plan component ncs:self self state ncs:ready status" | env ${ENV}4569 ncs_cli -C -u admin))
            res=${arr[1]}
            if [ "$res" == "reached" ]; then
                printf "${GREEN}##### Lower node $n transaction $i configured $NDTRANS devices\n${NC}"
                break
            fi
            printf "${RED}##### Waiting for lower node $n device $i to reach the ncs:ready state...\n${NC}"
            sleep .5
        done
    done
done

END=$($DATE +%s)
TIME=$(($END-$START))

printf "\n${PURPLE}##### Disable the NSO progress traces${NC}"
env ${ENV}4569 ncs_cli -n -u admin -C << EOF
unhide debug
config
progress trace t3-trace-$RUNID disabled
commit
devices device * config progress trace t3-trace-$RUNID disabled
commit
EOF

printf "\n${PURPLE}##### Total wall-clock time: $TIME s\n${NC}"

printf "${PURPLE}##### Show a graph representation of the upper-nso progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py upper-nso/logs/t3-$RUNID.csv
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n\n${NC}"
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "${PURPLE}##### Show a graph representation of the lower-nso-1 progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py lower-nso-1/logs/t3-$RUNID.csv
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "${PURPLE}##### Show a graph representation of the lower-nso-2 progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 -u ../../common/simple_progress_trace_viewer.py lower-nso-2/logs/t3-$RUNID.csv
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi


printf "\n${GREEN}##### Done!\n\n${NC}"
