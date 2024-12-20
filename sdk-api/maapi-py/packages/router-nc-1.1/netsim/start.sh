#!/bin/sh

# The following variables will be set before this script
# is invoked.

# CONFD_IPC_PORT     - The port this ConfD instance is listening to for IPC
# NETCONF_SSH_PORT   - The port this ConfD instance is listening to for NETCONF
# NETCONF_TCP_PORT
# CLI_SSH_PORT       - The port this ConfD instance is listening to for CLI/ssh
# SNMP_PORT          - The port this ConfD instance is listening to for SNMP
# NAME               - The name of this ConfD instance
# COUNTER            - The number of this ConfD instance
# CONFD              - Path to the confd executable
# CONFD_DIR          - Path to the ConfD installation
# PACKAGE_NETSIM_DIR - Path to the netsim directory in the package which
#                      was used to produce this netsim network

## If you need to start additional things, like C code etc in the
## netsim environment, this is the place to add that

test -f  cdb/O.cdb
first_time=$?

env sname=${NAME} ${CONFD} -c confd.conf ${CONFD_FLAGS} \
    --addloadpath ${CONFD_DIR}/etc/confd
ret=$?

# For example, here we can load operational data
# Test, and only do this the first time
if [ $ret = 0 -a ! $first_time = 0 ]; then
    ${CONFD_DIR}/bin/confd_load -l -m -O ${PACKAGE_NETSIM_DIR}/oper/${NAME}.xml
fi
exit $ret

