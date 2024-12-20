#!/bin/sh

devicename=""
managedobject=""
alarmtype=""
specificproblem=""
perceivedseverity=""
alarmtext=""
impactedobject=""

usage() {
    p=`basename $0`
    pf="                 "
    echo "$p --device <device-name>"
    echo "$pf --object <managed-object>"
    echo "$pf --type <alarm type>"
    echo "$pf --specific-problem <specific problem>"
    echo "$pf --severity <perceived severity>"
    echo "$pf --text <alarm text>"

    echo ""
    echo "Examples:
             $p --device c0 \\
                --object /devices/device{c0}/config/ios:interface/FastEthernet{1/0}/switchport \\
                --type spanning-tree \\
                --specific-problem \"spanning-tree\" \\
                --severity critical \\
                --text \"The alarm text\""
    exit 1
}


while [ $# -gt 0 ]; do
    arg="$1"
    shift
    case "$arg" in
        --device)
          devicename=$1
          shift;;
        --object)
          managedobject=$1
          shift;;
        --type)
          alarmtype=$1
          shift;;
        --specific-problem)
          specificproblem=$1
          shift;;
        --severity)
          perceivedseverity=$1
          shift;;
        --text)
          alarmtext=$1
          shift;;
        *)
            usage
        esac
done



if [ -z "$devicename" ]; then
    usage;
fi

if [ -z "$managedobject" ]; then
    usage;
fi

if [ -z "$alarmtype" ]; then
    usage;
fi

if [ -z "$specificproblem" ]; then
    usage;
fi

if [ -z "$perceivedseverity" ]; then
    usage;
fi

if [ -z "$alarmtext" ]; then
    usage;
fi

if [ 0 == 1 ]; then
    echo "device=$devicename"
    echo "object=$managedobject"
    echo "type=$alarmtype"
    echo "specific-problem=$specificproblem"
    echo "severity=$perceivedseverity"
    echo "text=$alarmtext"
fi

ncs_cli --user admin <<EOF;
  config

  request alarms qinq generate_alarm device $devicename \
      object $managedobject \
      alarm-type $alarmtype \
      perceived-severity $perceivedseverity \
      specific-problem $specificproblem \
      alarm-text "$alarmtext"

EOF



