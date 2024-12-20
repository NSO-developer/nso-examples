#!/bin/sh

## Tiny script to create a new managed device over
## The NETCONF interface

ncsip="--host=127.0.0.1"
ncsport="--port=2022"
ncsuser="--user=admin"
ncspass="--password=admin"
devip=127.0.0.1
devport=
devname=
authgroup="default"
file=
NC=${NCS_DIR}/bin/netconf-console

while [ $# -gt 0 ]; do
    arg="$1"
    shift
    case $arg in
        --ncsip)
            ncsip="--host=$1"; shift;;
        --ncsport)
            ncsport="--port=$1"; shift;;
        --ncsuser)
           ncsuser="--user=$1"; shift;;
        --ncspass)
           ncspass="--password=$1"; shift;;
        --devip)
            devip=$1; shift;;
        --devport)
            devport=$1; shift;;
        --devname)
            devname=$1; shift;;
        --authgroup)
            authgroup=$1; shift;;
        --dev-data-file)
            file=$1; shift;;
    esac
done

if [ -z "$devport" -o -z "$devname" -o -z "${file}" ]; then
    echo "Need at least devport, devname and file";
    exit 1;
fi

filedata=`cat $file`


${NC} ${ncsuser} ${ncspass} ${ncshost} ${ncsport} - << HERE
<?xml version="1.0" encoding="UTF-8"?>
<hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
  <capabilities>
    <capability>urn:ietf:params:netconf:base:1.0</capability>
  </capabilities>
</hello>
]]>]]>
<?xml version="1.0" encoding="UTF-8"?>
<rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0"  message-id="1">
  <edit-config xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0">
    <target>
      <running/>
    </target>
    <test-option>test-then-set</test-option>
    <error-option>rollback-on-error</error-option>
    <config>
      <devices xmlns="http://tail-f.com/ns/ncs">
        <device>
          <name>${devname}</name>
          <address>${devip}</address>
          <port>${devport}</port>
          <authgroup>${authgroup}</authgroup>
          <device-type>
            <netconf/>
          </device-type>
          <state>
            <admin-state>unlocked</admin-state>
          </state>
       </device>
     </devices>
    </config>
  </edit-config>
</rpc>
]]>]]>
<?xml version="1.0" encoding="UTF-8"?>
<rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0"  message-id="1">
  <edit-config xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0">
    <target>
      <running/>
    </target>
    <test-option>test-then-set</test-option>
    <error-option>rollback-on-error</error-option>
    <config>
      <devices xmlns="http://tail-f.com/ns/ncs">
        <device>
          <name>${devname}</name>
          <config>${filedata}</config>
       </device>
     </devices>
    </config>
  </edit-config>
</rpc>
]]>]]>
<?xml version="1.0" encoding="UTF-8"?>
<rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="2">
  <close-session/>
</rpc>

HERE
