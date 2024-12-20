#!/bin/sh

set -e

if [ $# -gt 0 ]; then
    case "$1" in
        --post-commit)
            cat << EOF
begin post-commit
end
EOF
            exit 0
            ;;
        *)
            echo
            echo "Usage: $0 [--post-commit]"
            echo
            echo "  --post-commit Mandatory for post-commit scripts"
            exit 1
            ;;
    esac
else
    file="$TEST_POST_COMMIT_SHOW_DIFF_FILE"
    if [ "x${file}" != "x" ]; then
        # echo "Redirect stdout to logfile"
        exec > "${file}" 2>&1

        echo
        date
        echo $0
        echo "CONFD_MAAPI_USID=$CONFD_MAAPI_USID"
        echo "CONFD_MAAPI_THANDLE=$CONFD_MAAPI_THANDLE"
        echo
        echo "--- transaction diff ---"
        ncs-maapi --keypath-diff /
    else
        var=TEST_POST_COMMIT_SHOW_DIFF_FILE
        echo "Set the environment variable $var to redirect the output to file"
        exit 2
    fi
fi
