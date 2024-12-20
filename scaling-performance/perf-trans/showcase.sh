#!/bin/sh
RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n\n${GREEN}##### Showcase: Python package based variant\n${NC}"
make NDEVS=4 python; python3 measure.py --ntrans 4 --nwork 3 --ndtrans 1 --cqparam sync --ddelay 1
CSVFILE=$(ls logs/*.csv)
if [ -z "$NONINTERACTIVE" ]; then
    python3 ../../common/simple_progress_trace_viewer.py $CSVFILE
    printf "${PURPLE}##### Note: The last transaction disables the progress trace\n${NC}"
fi

printf "\n\n${GREEN}##### Showcase: Python package based variant simulating pre-6.0 behavior\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
make NDEVS=4 python-serial; python3 measure.py --ntrans 4 --nwork 3 --ndtrans 1 --cqparam sync --ddelay 1
CSVFILE=$(ls logs/*.csv)
if [ -z "$NONINTERACTIVE" ]; then
    python3 ../../common/simple_progress_trace_viewer.py $CSVFILE
fi

printf "\n\n${GREEN}##### Showcase: Java package based variant\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
make NDEVS=4 java; python3 measure.py --ntrans 4 --nwork 3 --ndtrans 1 --cqparam sync --ddelay 1
CSVFILE=$(ls logs/*.csv)
if [ -z "$NONINTERACTIVE" ]; then
    python3 ../../common/simple_progress_trace_viewer.py $CSVFILE
fi

printf "\n\n${GREEN}##### Showcase: Java package based variant simulating pre-6.0 behavior\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
make NDEVS=4 java-serial; python3 measure.py --ntrans 4 --nwork 3 --ndtrans 1 --cqparam sync --ddelay 1
CSVFILE=$(ls logs/*.csv)
if [ -z "$NONINTERACTIVE" ]; then
    python3 ../../common/simple_progress_trace_viewer.py $CSVFILE
fi

printf "\n\n${GREEN}##### Showcase done\n${NC}"
