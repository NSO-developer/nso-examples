# Common utility functions

echo_bold() {
    printf "\033[1m"
    echo "$@"
    printf "\033[0m"
}

echo_green() {
    printf "\033[0;32m"
    echo "$@"
    printf "\033[0m"
}

echo_purple() {
    printf "\033[0;35m"
    echo "$@"
    printf "\033[0m"
}

echo_red() {
    printf "\033[0;31m"
    echo "$@"
    printf "\033[0m"
}


exec_cli() {
    echo "$@" | ncs_cli -nsCu admin
    echo ""
}

# np = no paging
exec_cli_np() {
    echo "$@ | nomore" | ncs_cli -nsCu admin
    echo ""
}

exec_shell() {
    echo_bold "$@"
    "$@"
}


NONINTERACTIVE=${NONINTERACTIVE-}
STEP_NO=0
next_step() {
    if [ "$NONINTERACTIVE" = "$STEP_NO" ]; then
        NONINTERACTIVE=""
    fi
    STEP_NO=$((STEP_NO + 1))
    echo ""
    echo_purple "##### Step ${STEP_NO}: $@"
    if [ -z "$NONINTERACTIVE" ]; then
        printf "[return to proceed]"
        read X
    fi
}

print_section() {
    cat "$1" | sed -n "/<$2/,/<\\/$2/p"
}

show_done() {
    echo ""
    echo ""
    echo_green "##### Done!"
    echo ""
    echo "In case you want to further explore the example, feel free to do so."
    echo "When done, please run 'make stop' to release used resources."
    echo ""
}

show_title() {
    echo ""
    echo_green "##### $@"
    echo "Note: To run this example without pausing, use: make demo-nonstop"
}


wait_for() {
    expected=$1
    shift
    tried=0
    while true; do
        tried=$((tried + 1))
        found=$("$@")
        if [ "x$found" = "x$expected" ]; then return 0; fi
        if [ "$tried" = "${TRYSEC-15}" ]; then return 1; fi
        echo "Waiting for ${expected}"
        sleep 1
    done
}

wait_while() {
    expected=$1
    shift
    tried=0
    while true; do
        tried=$((tried + 1))
        found=$("$@")
        if ! [ "x$found" = "x$expected" ]; then return 0; fi
        if [ "$tried" = "${TRYSEC-15}" ]; then return 1; fi
        echo "Waiting..."
        sleep 1
    done
}
