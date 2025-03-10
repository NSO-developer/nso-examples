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

# Move ConfD to start-phase 0
env sname=${NAME} ${CONFD} --start-phase0 -c confd.conf --addloadpath ${CONFD_DIR}/etc/confd
# Load the initial configuration data
CONFD_IPC_PORT=${CONFD_IPC_PORT} ${CONFD_DIR}/bin/confd_load -dd -i -o -m -l chassis_init.xml.in
# Start the system-controller module
python3 system_controller.py ${CONFD_IPC_PORT} &
# Wait for the system-controller to move ConfD to start-phase 1
${CONFD_DIR}/bin/confd_cmd -dd -p ${CONFD_IPC_PORT} -c 'wait-start 1'
# Load the initial state data
${CONFD_DIR}/bin/confd_load -dd -O -m -l chassis_init.xml.in
# Register an example card and have the card open up the nortbound interfaces by moving ConfD to start-phase 2
python3 card.py -p ${CONFD_IPC_PORT} -c "dummy" -a "82" -n "2391" -m "tail-f" -r 1 -u 4 -l 1 -o 4 -i &
# Wait for the card to move ConfD to start-phase 2
${CONFD_DIR}/bin/confd_cmd -dd -p ${CONFD_IPC_PORT} -c 'wait-start 2'

ret=$?

if [ ! $first_time = 0 ]; then
   true;
   ## If there is anything we want to do after the
   ## first initial start, this is the place. An example could be
   ## to load CDB operational data from xml files
fi

exit $ret

