# Reusable functions for Raft HA

find_leader() {
    nodes=$1
    fail=${2-1}
    for i in $(seq $nodes) ; do
        role=$(node_role $i)
        if [ "$role" = "leader" ]; then echo $i; return 0; fi
    done
    echo none
    return $fail
}

node_cli() {
    node=$1
    shift
    NCS_IPC_PORT=456$node exec_cli "$@"
}

node_cmd() {
    node=$1
    shift
    NCS_IPC_PORT=456$node ncs_cmd "$@"
}

node_leader() {
    node=$1
    shift
    node_cmd "$node" -o -c 'mrtrans; mget "/ha-raft/status/leader"' \
        2>/dev/null || echo none
}

node_role() {
    node=$1
    shift
    node_cmd "$node" -o -c 'mrtrans; mget "/ha-raft/status/role"' \
        2>/dev/null || echo down
}

node_show() {
    node=$1
    shift
    node_cli "$node" "show $@ | nomore"
}

show_ha_roles() {
    nodes=$1
    for i in $(seq $nodes) ; do
        role=$(node_role $i)
        echo "node${i}: ${role}"
    done
}
