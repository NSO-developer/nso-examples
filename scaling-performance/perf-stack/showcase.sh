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

USAGE="$0 [-d <ndevs> -t <ntrans> -w <nwork> -r <ndtrans> -q <usecq> -y <ddelay> -h <help>]"

# Default settings for the showcase
NUM_CPU=$(python3 -c "import os; print(os.cpu_count())")
NDEVS=4 # Number of devices simulated using netsim
RUNID="$($DATE +%s)" # An ID used as dummy data, here a timestamp
NTRANS=$NDEVS # Number of transactions used. Here, one per device.
NWORK=3 # Simulated work per transaction in the RFS service configure and validation states
NDTRANS=1 # Number of devices the RFS service will configure per service transaction.
USECQ="True" # Use commit queues
DEV_DELAY=1 # Simulated work on devices in seconds
NONINTERACTIVE=${NONINTERACTIVE-}

while getopts ':d:t:w:r:q:y:' opt
do
    case $opt in
        d) NDEVS=${OPTARG};;
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
make clean NDEVS=$NDEVS all start

if [ $USECQ == "True" ]; then
    printf "\n${PURPLE}##### Configure the default commit-queue settings${NC}"
    ncs_cli -n -u admin -C << EOF
config
devices global-settings commit-queue enabled-by-default true
devices global-settings commit-queue sync
commit
EOF
fi

printf "\n\n${PURPLE}##### Configure the device delay, i.e., simulated device work and calibrate the CPU time${NC}"
ncs_cli -n -u admin -C << EOF
config
cfs-t3s dev-settings dev-delay $DEV_DELAY
commit
EOF

ncs_cmd -dd -c "maction /t3:t3s/calibrate-cpu-time"

printf "\n\n${PURPLE}##### Enable the NSO progress trace${NC}"
ncs_cli -n -u admin -C << EOF
unhide debug
config
progress trace t3-trace-$RUNID
enabled destination file t3-$RUNID.csv format csv
verbosity normal
commit
EOF

printf "\n\n${PURPLE}##### Run a test with $NTRANS transactions to $NDEVS devices with $NDTRANS transactions per device\n${NC}"
printf "${PURPLE}##### simulate $NWORK s work and using run-id $RUNID on a processor with $NUM_CPU cores${NC}"
START=$($DATE +%s)
ncs_cli -n -u admin -C <<EOF
config
cfs-t3s t3-settings ntrans $NTRANS nwork $NWORK ndtrans $NDTRANS run-id $RUNID
commit
EOF

printf "\n\n${PURPLE}##### Wait for the nano service plan to reach ready status\n${NC}"
for (( i=0; i<$NTRANS; i++ ))
do
    while : ; do
        arr=($(echo "show t3s t3 $i plan component self self state ready status" | ncs_cli -C -u admin))
        res=${arr[1]}
        if [ "$res" == "reached" ]; then
            printf "${GREEN}##### Transaction $i configured $NDTRANS devices\n${NC}"
            break
        fi
        printf "${RED}##### Waiting for device $i to reach the ncs:ready state...\n${NC}"
        sleep .5
    done
done

if [ $USECQ == "True" ]; then
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

printf "\n${PURPLE}##### Disable the NSO progress trace${NC}"
ncs_cli -n -u admin -C << EOF
unhide debug
config
progress trace t3-trace-$RUNID disabled
commit
EOF

printf "\n\n${PURPLE}##### T3 RFS service log from nso-rundir/logs/ncs-python-vm-t3.log:\n${NC}"
cat nso-rundir/logs/ncs-python-vm-t3.log

printf "\n${PURPLE}##### Total wall-clock time: $TIME s\n${NC}"
printf "${PURPLE}##### Progress trace written to nso-rundir/logs/t3-$RUNID.csv\n\n${NC}"

printf "${PURPLE}##### Show a graph representation of the progress trace\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    python3 ../../common/simple_progress_trace_viewer.py nso-rundir/logs/t3-$RUNID.csv
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n${NC}"
else
    printf "${RED}##### Skip - non-interactive\n${NC}"
fi

printf "\n${GREEN}##### Done!\n\n${NC}"
