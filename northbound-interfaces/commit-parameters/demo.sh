#!/usr/bin/env bash
set -eu

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m'
NONINTERACTIVE=${NONINTERACTIVE-}
SERVICE_NAME="branch-office"

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

stop_example() {
    set +e
    make stop > /dev/null 2>&1
    set -e
}

clean_example() {
    make clean
}

cleanup_environment() {
    stop_example
    clean_example
}

stop_example
clean_example

printf "\n${GREEN}##### CLI commit parameters demo\n${NC}"

printf "${PURPLE}##### Start clean\n${NC}"
cleanup_environment

printf "\n${PURPLE}##### Build and start the enterprise-dns example\n${NC}"
make all start

printf "\n${PURPLE}##### Sync configuration from the simulated routers\n${NC}"
make sync-from

printf "\n\n${PURPLE}##### Bootstrap a base enterprise DNS service\n${NC}"
make bootstrap

printf "\n\n${GREEN}##### Step 1: Inspect CLI commit parameter completions\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
paginate false
config
enterprise-dns enterprise-dns-instances ${SERVICE_NAME} search-domain cli-preview.example
commit ?
EOF

printf "\n\n${GREEN}##### Step 2: Use CLI commit parameters for dry-run and commit\n${NC}"
pause
ncs_cli -n -u admin -C << EOF
paginate false
config
enterprise-dns enterprise-dns-instances ${SERVICE_NAME} search-domain cli-demo.example
commit dry-run outformat cli-c
commit comment "Committed through CLI commit parameters" label "cli-demo"
EOF

printf "\n\n${PURPLE}##### Verify the CLI-applied value and inspect commit history\n${NC}"
ncs_cli -n -u admin -C << 'EOF'
paginate false
show running-config enterprise-dns enterprise-dns-instances branch-office
show running-config devices device ex0 config sys dns
show configuration commit list
EOF

if [ -z "$NONINTERACTIVE" ]; then
    printf "\n${GREEN}##### Cleanup\n${NC}"
    cleanup_environment
    pause
fi

printf "\n${GREEN}##### Done!\n${NC}"
