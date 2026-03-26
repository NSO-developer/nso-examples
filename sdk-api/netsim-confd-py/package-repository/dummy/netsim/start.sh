#!/usr/bin/env bash
env sname=${NAME} ${CONFD} -c confd.conf ${CONFD_FLAGS} --addloadpath ${CONFD_DIR}/etc/confd
ret=$?

if [ $ret = 0 ]; then
  python3 dummy_app.py ${CONFD_IPC_PORT} &
fi

exit $ret
