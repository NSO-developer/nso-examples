#!/bin/sh

set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}
RUN_DIR=nso-run

pause() {
    prompt="${1-}"
    if [ -z "$prompt" ]; then
        prompt="${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    fi
    if [ -z "$NONINTERACTIVE" ]; then
        printf "%b" "$prompt"
        read -n 1 -s -r
    fi
}


printf "\n\n${PURPLE}##### Start NSO and simulated routers\n\n${NC}"
make stop clean all start

printf "\n\n${PURPLE}##### Display devices and NSO packages\n${NC}"
ncs_cli -n -C -u admin << EOF
show devices list | nomore
show packages | nomore
EOF

printf "\n\n${GREEN}##### Showcase 1: Display commit params in Python\n${NC}"
printf "${PURPLE}##### Step 1: Showcase commit param detection in Python\n${NC}"
pause
ncs_cli -n -C -u admin << EOF
config
commit-params-py commit-params-py-instances demo device ex0 if GigabitEthernet0 if-speed hundred
commit dry-run
commit
EOF

printf "${RED}\n\nDisplay service Python log\n${NC}"
cat "${RUN_DIR}/logs/ncs-python-vm-commit-params-py.log" | \
    grep "Service create" -m 1 -A 5

printf "\n${PURPLE}##### Step 2: Showcase applying commit params in Python action\n${NC}"
pause
ncs_cli -n -C -u admin << EOF
commit-params-py commit-params-py-instances demo showcase-py
EOF

printf "${RED}\n\nDisplay ncs-python-vm log\n${NC}"
cat "${RUN_DIR}/logs/ncs-python-vm-commit-params-py.log" | \
    grep "Apply commit parameter" -A 23

printf "\n\n${GREEN}##### Showcase 2: Display commit params in Java\n${NC}"
printf "${PURPLE}##### Step 1: Showcase commit param detection in Java\n${NC}"
pause
ncs_cli -n -C -u admin << EOF
config
commit-params-java commit-params-java-instances demo device ex0 if GigabitEthernet2 if-speed hundred
commit dry-run
commit
EOF

printf "${RED}\n\nDisplay service Java log\n${NC}"
cat "${RUN_DIR}/logs/ncs-java-vm.log" | \
    grep "Commit parameters:" -m 1 -A 1

printf "\n${PURPLE}##### Step 2: Showcase applying commit params in Java action\n${NC}"
pause
ncs_cli -n -C -u admin << EOF
commit-params-java commit-params-java-instances demo showcase-java
EOF

printf "${RED}\n\nDisplay ncs-java-vm log\n${NC}"
cat "${RUN_DIR}/logs/ncs-java-vm.log" | \
    grep "Apply commit param" -A 22

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n${GREEN}##### Cleanup\n${NC}"
    pause
    make stop clean
fi

printf "\n${GREEN}##### Done!\n${NC}"
