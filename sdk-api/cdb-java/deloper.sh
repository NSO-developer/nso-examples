#!/usr/bin/env sh
ant -f packages/cdb/src/java/build.xml stats -Dop=DELETE -Dkey=$1 >/dev/null
