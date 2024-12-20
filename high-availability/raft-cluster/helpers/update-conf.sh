#!/bin/sh
set -e

CONFIG_FILE=$1
CONFIG=$(cat "$1")
shift

for f in "$@" ; do
    HEAD=$(head -n1 "$f")
    CMD=$(echo "$HEAD" | cut -d':' -f2)
    TAGPATH=$(echo "$HEAD" | cut -d':' -f3 | cut -d' ' -f1 | sed -e 's|/| |g')
    DATA=$(tail -n +2 "$f" | sed -e "s/~{ID}/$NODE_ID/g")

    TAGS_IN_DATA=''
    case $CMD in
        add)
            SWITCH='-a'
            ;;
        delete)
            SWITCH='-d'
            TAGS_IN_DATA='yes'
            ;;
        replace)
            SWITCH='-R'
            ;;
        set)
            SWITCH='-e'
            ;;
    esac

    if [ -n "$TAGS_IN_DATA" ] ; then
        for tag in $DATA ; do
            CONFIG=$(echo "$CONFIG" | ncs_conf_tool $SWITCH $TAGPATH $tag)
        done
    else
        CONFIG=$(echo "$CONFIG" | ncs_conf_tool $SWITCH "$DATA" $TAGPATH)
    fi
done

echo "$CONFIG" > "$CONFIG_FILE"
