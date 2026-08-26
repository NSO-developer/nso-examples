#!/bin/sh
. ./env.sh

test -f cdb/O.cdb
first_time=$?

env sname="${NAME}" "${CONFD}" -c confd.conf ${CONFD_FLAGS} \
    --addloadpath "${CONFD_DIR}/etc/confd"
ret=$?

if [ "$ret" -eq 0 ] && [ "$first_time" -ne 0 ]; then
    python=${PYTHON:-python3}
    dhcp_ztp_client=../../../device-config/dhcp-ztp-client.py
    dhcp_leases=../../../device-config/dhcp-leases.json
    if ! command -v "$python" >/dev/null 2>&1 || \
       [ ! -f "$dhcp_ztp_client" ] || [ ! -f "$dhcp_leases" ]; then
        echo "Missing DHCP/ZTP simulator dependency for $NAME" >&2
        "${CONFD}" --stop >/dev/null 2>&1 || true
        exit 1
    fi

    "${CONFD}" --wait-started 20
    ret=$?
    if [ "$ret" -eq 0 ]; then
        "$python" -u "$dhcp_ztp_client" \
            --device "$NAME" \
            --leases "$dhcp_leases" \
            --download-dir logs \
            --retry-timeout 300 \
            > logs/ztp.log 2>&1
        ret=$?
    fi
    if [ "$ret" -ne 0 ]; then
        "${CONFD}" --stop >/dev/null 2>&1 || true
    fi
fi

exit "$ret"
