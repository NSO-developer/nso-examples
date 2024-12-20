#!/usr/bin/env sh
ant -f ./packages/cdb/src/java/build.xml stats -Dop=CREATE -Dkey=$1 >/dev/null