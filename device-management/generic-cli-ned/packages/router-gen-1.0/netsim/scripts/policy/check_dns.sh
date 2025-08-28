#!/bin/sh

usage_and_exit() {
    cat << EOF
Usage: $0 -h
       $0 --policy

  -h                    display this help and exit
  --policy              display policy configuration and exit

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
  keypath: /sys/dns/server/address
  dependency: /sys/dns/server
  priority: 4
  call: each
end
EOF
            exit 0
            ;;
    esac
    shift
done


#echo Running $* XXX >> /tmp/x
dns=`maapi --keys /sys/dns/server`
#echo "dns = $dns" >> /tmp/x
for ip in `echo ${dns}`; do
    case ${ip} in
        255.*)
            echo "${ip} is not a good value for dns server"
            exit 2;;
        *)
            true;;
    esac
done


