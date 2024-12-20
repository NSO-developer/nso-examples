#!/bin/sh

ncs_cli --user admin <<EOF;
request alarms qinq generate_alarm device p0 \
    object /devices/device{p0} \
    alarm-type communications-alarm \
    perceived-severity critical \
    specific-problem specprob \
    alarm-text tramstext \
    impacted-objects { i-obj /devices/device{c0} } \
    impacted-objects { i-obj /devices/device{c1} }
EOF


