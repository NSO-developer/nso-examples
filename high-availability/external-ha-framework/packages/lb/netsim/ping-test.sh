#!/bin/sh

echo $* > /tmp/ping-test.out
echo " 0 $0" >>  /tmp/ping-test.out

echo "rcon __BEGIN result ping-test-ok ip 2.3.4.5 ival 66 rcon __END"
 
