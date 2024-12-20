
# We may optionally have start.sh file in the netsim directory
# It will be transformed and sourced with a set of environment
# variables.
# If we need to start additional C programs we can do that here
# Also if we need to load additional XML/CLI files etc we
# can do that here.
# This script will have access to the following environment variables

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


test -f  cdb/O.cdb
first_time=$?

env sname=${NAME} ${CONFD} -c confd.conf ${CONFD_FLAGS} \
                  --addloadpath ${CONFD_DIR}/etc/confd
ret=$?

# So for example here we can load operational data
# Test, and only do this the first time

if [ $ret = 0 ]; then
  python3 slowpoke.py ${CONFD_IPC_PORT} &
fi

exit $ret
