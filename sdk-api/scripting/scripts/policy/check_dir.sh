#!/bin/sh

usage_and_exit() {
    cat << EOF
Usage: $0 -h
       $0 --policy
       $0 --keypath <keypath> [--value <value>]

  -h                    display this help and exit
  --policy              display policy configuration and exit
  --keypath <keypath>   path to node
  --value <value>       value of leaf

Return codes:

  0 - ok
  1 - warning message is printed on stdout
  2 - error message   is printed on stdout
EOF
    exit 1
}

while [ $# -gt 0 ]; do
    case "$1" in
        -h)
            usage_and_exit
            ;;
        --policy)
            # Configuration of dynamic validation point
            #
            # keypath    - Path to node in data model
            # dependency - path to node in data model
            # priority   - priority level (integer)
            # call       - call mode (once|each)
            cat << EOF
begin policy
  keypath: /devices/global-settings/trace-dir
  dependency: /devices/global-settings
  priority: 2
  call: each
end
EOF
            exit 0
            ;;
        --keypath)
            if [ $# -lt 2 ]; then
                echo "<ERROR> --keypath <keypath> - path omitted"
                usage_and_exit
            else
                keypath=$2
                shift
            fi
            ;;
        --value)
            if [ $# -lt 2 ]; then
                echo "<ERROR> --value <value> - leaf value omitted"
                usage_and_exit
            else
                value=$2
                shift
            fi
            ;;
        *)
            usage_and_exit
            ;;
    esac
    shift
done

if [ -z "${keypath}" ]; then
    echo "<ERROR> --keypath <keypath> is mandatory"
    usage_and_exit
fi

if [ -z "${value}" ]; then
    echo "<ERROR> --value <value> is mandatory"
    usage_and_exit
fi

orig="./logs"
dir=${value}
# dir=`ncs-maapi --get /devices/global-settings/trace-dir`
if [ "${dir}" != "${orig}" ] ; then
    echo "/devices/global-settings/trace-dir: must retain its original value (${orig})"
    exit 2
fi
