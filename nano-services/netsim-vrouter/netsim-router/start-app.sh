#!/usr/bin/env bash
source ./env.sh
export JARFILE=${CONFD_DIR}/java/jar/conf-api.jar
export LOG4JAPI=${CONFD_DIR}/java/jar/log4j-api.jar
export LOG4JCORE=${CONFD_DIR}/java/jar/log4j-core.jar
export LOG4JWRAP=${CONFD_DIR}/java/jar/log4j.jar
export JUNIXSOCKC=${CONFD_DIR}/java/jar/junixsocket-common.jar
export JUNIXSOCKNC=${CONFD_DIR}/java/jar/junixsocket-native-common.jar
export CLASSPATH=${JARFILE}:${LOG4JAPI}:${LOG4JCORE}:${LOG4JWRAP}:${JUNIXSOCKC}:${JUNIXSOCKNC}
java -classpath ${CLASSPATH}:${JLIB}:. IfLink ${CONFD_IPC_PORT} &
ecode=1; while [ $ecode -ne 0 ]; do sleep .5; ${CONFD_DIR}/bin/confd_cmd -p ${CONFD_IPC_PORT} -o -c "mget /tfcm:confd-state/tfcm:internal/tfcm:cdb/tfcm:client{1}/tfcm:name" > /dev/null; ecode=$?; done;
