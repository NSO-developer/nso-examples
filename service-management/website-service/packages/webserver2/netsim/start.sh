

# We may optionally have start.sh file in the netsim directory
# It will be transformed and sourced with a set of environment
# variables.
# If we need to start additional C programs we can do that here
# Also if we need to load additional XML/CLI files etc we
# can do that here.
# This script will have access to the following environment variables
#
# CONFD_IPC_PORT    - which port is this ConfD instance listening to for IPC
# NETCONF_SSH_PORT  - which port is this ConfD instance listening to for netconf
# NETCONF_TCP_PORT
# CLI_SSH_PORT      - which port is this ConfD instance listening to for CLI/ssh
# SNMP_PORT         - which port is this ConfD instance listening to for snmp
# NAME              - what is the name of this ConfD instance
# CONFD             - path to the confd executable
# CONFD_DIR         - path to the ConfD installation
# PACKAGE_NETSIM_DIR - path to the netsim directory in the package which
#                      was used to produce this netsim network

env sname=${NAME} ${CONFD} -c confd.conf --start-phase0 \
          --addloadpath ${CONFD_DIR}/etc/confd  ${CONFD_FLAGS}
ret=$?

if [ $ret = 0 ]; then
    ${CONFD} --start-phase1

    ./interface_stats -c 127.0.0.1/${CONFD_IPC_PORT}  \
          > interface_stats.${CONFD_IPC_PORT}.log 2>&1

    # support setting the interval using environment, default is
    # sending a notification every 2 seconds.
    if [ "${WWW_NOTIF_INTERVAL}x" = "x" ]; then
        WWW_NOTIF_INTERVAL="2"
    fi
    
    ./notifier_builtin_replay_store -c 127.0.0.1/${CONFD_IPC_PORT} \
          -s ${WWW_NOTIF_INTERVAL} -S > notif.${CONFD_IPC_PORT}.log 2>&1

    ${CONFD} --start-phase2
fi

exit $ret

