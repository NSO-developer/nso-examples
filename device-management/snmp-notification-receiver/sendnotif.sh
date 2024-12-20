#!/bin/sh

opts="-classpath ."
for i in ${NCS_DIR}/java/jar/*.jar; do
    opts="${opts}:$i"
done

date -u +'Before send: %FT%T.%N'
java ${opts} \
   org.snmp4j.tools.console.SnmpRequest -p TRAP -v 2c \
   $1/$2 "1.3.6.1.2.1.1.3.0={t}0" \
   "1.3.6.1.6.3.1.1.4.1.0={o}1.3.6.1.6.3.1.1.5.3" \
   "1.3.6.1.2.1.2.2.1={s}$3" $4
R=$?
date -u +'After send: %FT%T.%N'

exit $R

