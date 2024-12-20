#!/bin/sh
RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n\n${GREEN}##### Showcase: Service mapping with 100 zone-pairs per 3 transactions to 3 devices = 300 zone-pairs\n${NC}"
make stop clean NDEVS=3 parallel start; python3 measure.py --ntrans 1 --nzones 100 --cqparam sync
CSVFILE=$(ls logs/*.csv)
if [ -z "$NONINTERACTIVE" ]; then
    python3 ../../common/simple_progress_trace_viewer.py $CSVFILE
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n${NC}"
fi

printf "\n\n${GREEN}##### Showcase: Service mapping with 100 zone-pairs per device in a single transaction to 3 devices = 300 zone-pairs\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
make stop clean NDEVS=3 parallel start; python3 measure.py --ntrans 0 --nzones 100 --cqparam bypass
CSVFILE=$(ls logs/*.csv)
if [ -z "$NONINTERACTIVE" ]; then
    python3 ../../common/simple_progress_trace_viewer.py $CSVFILE
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n${NC}"
fi

printf "\n\n${GREEN}##### Showcase: Service mapping simulating pre-6.0 behavior with 100 zone-pairs per 3 transactions to 3 devices = 300 zone-pairs\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
make stop clean NDEVS=3 serial start; python3 measure.py --ntrans 1 --nzones 100 --cqparam async
CSVFILE=$(ls logs/*.csv)
if [ -z "$NONINTERACTIVE" ]; then
    python3 ../../common/simple_progress_trace_viewer.py $CSVFILE
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n${NC}"
fi

printf "\n\n${GREEN}##### Showcase done\n${NC}"
