#!/usr/bin/env bash
LIST_ENTRIES=( "3000" )
TESTS=( "py_setvals_xml" "j_setvals_xml" "py_create" )
NONINTERACTIVE=${NONINTERACTIVE-}

for INSTANCES in "${LIST_ENTRIES[@]}" ; do
    for TEST in "${TESTS[@]}" ; do
        if [ -n "$NONINTERACTIVE" ]; then
            NONINTERACTIVE=1 ./measure.sh -r $INSTANCES -d 2 -t $TEST -n true
        else
            ./measure.sh -r $INSTANCES -d 2 -t $TEST -n true
        fi
    done
done
