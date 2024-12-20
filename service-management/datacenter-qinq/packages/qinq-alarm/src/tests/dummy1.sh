#!/bin/sh

ncs_cli --user admin <<EOF;
request alarms qinq generate_alarm device p0 object obj1 \
    alarm-type communications-alarm \
    perceived-severity critical \
    specific-problem specprob \
    alarm-text tramstext \
    impacted-objects { i-obj obj1 } \
    impacted-objects { i-obj obj2 }
EOF


