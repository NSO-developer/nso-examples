#!/bin/sh

## Tiny script to create a new web site by instantiating
## a resource facing service, using CLI scripting


lbprofile="gold"
port="80"

CLI=${NCS_DIR}/bin/ncs_cli

while [ $# -gt 0 ]; do
    arg="$1"
    shift
    case $arg in
        --lbprofile)
            lbprofile="$1"; shift;; 
        --ip)
            ip="$1"; shift;;
        --port)
           port="$1"; shift;;      
        --servicename)
           servicename="$1"; shift;;      
    esac
done

if [ -z "$servicename" -o -z "$ip" ]; then
    echo "Need at least servicename and ip";
    exit 1;
fi

$CLI << HERE
configure
set services web-site ${servicename} ip ${ip} port ${port} url /var/www/${servicename} lb-profile ${lbprofile}
commit
exit
exit
HERE | grep "Commit complete"


