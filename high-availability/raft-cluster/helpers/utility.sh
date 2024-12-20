# Common utility functions

echo_color() {
    color=$1
    shift
    # https://no-color.org
    if [ -n "${NO_COLOR:-}" ]; then
        echo "$@"
        return 0
    fi
    case "$color" in
        bold) n=1 ;;
        green) n=32 ;;
        purple) n=35 ;;
        red) n=31 ;;
        *) echo "invalid color: $color" >&2; exit 1 ;;
    esac
    printf '\033[%dm' "$n"
    echo "$@"
    printf '\033[0m'
}

echo_bold() { echo_color bold "$@"; }
echo_green() { echo_color green "$@"; }
echo_purple() { echo_color purple "$@"; }
echo_red() { echo_color red "$@"; }

exec_cli() {
    echo NCS_IPC_PORT=$NCS_IPC_PORT ncs_cli -nsCu admin -g admin
    echo "$@"
    echo "$@" | ncs_cli -nsCu admin -g admin
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
        [ "x$X" != xq ] || exit
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
