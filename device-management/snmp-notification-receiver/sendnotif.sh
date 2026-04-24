#!/bin/sh
set -eu

if ! command -v snmptrap >/dev/null 2>&1; then
    echo "snmptrap command not found" >&2
    exit 1
fi

export SNMP_PERSISTENT_FILE="${SNMP_PERSISTENT_FILE:-/dev/null}"

date -u +'Before send: %FT%T.%N'
snmptrap -v3 -u ncs -l authPriv -a SHA -A authpass -x AES -X privpass \
    "$1:$2" 100 1.3.6.1.6.3.1.1.5.3 \
    1.3.6.1.2.1.2.2.1.1.1 i "$3" \
    1.3.6.1.2.1.2.2.1.7.1 i "2" \
    1.3.6.1.2.1.2.2.1.8.1 i "2"
R=$?
date -u +'After send: %FT%T.%N'

exit $R
