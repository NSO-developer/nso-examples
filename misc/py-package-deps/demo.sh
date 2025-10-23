#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "\n${GREEN}##### Python cowlog action demo handling the cowsay Python package dependency\n${NC}"
printf "${PURPLE}##### Reset\n${NC}"
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the cowlog action demo installing the cowsay Python package dependency in the cowlog package python directory (recommended)\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Build the package, add Python dependencies, and start NSO\n${NC}"
make all cowlog-deps start

printf "\n${PURPLE}##### Run the 'cowlog' action\n${NC}"
ncs_cli -n -u admin -C << EOF
cowlog-action cowlog
EOF

while ! [ -s logs/ncs-python-vm.log ]; do
    sleep 1 # Give some time for the log to be written
done

printf "\n\n${PURPLE}##### Check the log output in logs/ncs-python-vm.log\n${NC}"
cat logs/ncs-python-vm.log

printf "\n${PURPLE}##### View the logs/ncs-python-vm-cowlog.log output\n${NC}"
cat logs/ncs-python-vm-cowlog.log

printf "${GREEN}##### Reset\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
set +e
make stop
set -e
make clean

printf "\n${GREEN}##### Running the cowlog action demo installing the cowsay Python package dependency using a Python virtual environment (alternative)\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
printf "${PURPLE}##### Build the package, create the Python virtual environment, add Python dependencies, and start NSO\n${NC}"
make all pyvenv start

printf "\n${PURPLE}##### Run the 'cowlog' action\n${NC}"
ncs_cli -n -u admin -C << EOF
cowlog-action cowlog
EOF

while ! [ -s logs/ncs-python-vm.log ]; do
    sleep 1 # Give some time for the log to be written
done

printf "\n\n${PURPLE}##### Check the log output in logs/ncs-python-vm.log to confirm that the virtual environment was activated for the NSO Python VM instance of the package:\n${NC}"
cat logs/ncs-python-vm.log

printf "\n${PURPLE}##### View the logs/ncs-python-vm-cowlog.log output\n${NC}"
cat logs/ncs-python-vm-cowlog.log

if [ -z "$NONINTERACTIVE" ]; then
    printf "${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
    printf "\n${PURPLE}##### Stop NSO and clean all created files\n${NC}"
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
