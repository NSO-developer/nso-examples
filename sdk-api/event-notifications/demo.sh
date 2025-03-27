#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color
NONINTERACTIVE=${NONINTERACTIVE-}

printf "${GREEN}##### Event notification demo\n${NC}"
printf "${PURPLE}##### Start clean\n${NC}"
set +e
make stop clean &> /dev/null
set -e
rm -rf nso-rundir

printf "${PURPLE}##### Create a dummy NSO service example\n${NC}"
make all
printf "
module dummy {
  namespace \"http://com/example/dummy\";
  prefix dummy;
  leaf dummy {
    type string;
  }
}
" > nso-rundir/dummy.yang
ncs-make-package --no-java --no-python --no-test --dest nso-rundir/packages/dummy-nc-1.0 --build --netconf-ned ./nso-rundir dummy-nc-1.0
ncs-make-package --no-test --dest nso-rundir/packages/dummy-service --service-skeleton template dummy-service
printf "module dummy-service {
  namespace \"http://com/example/dummyservice\";
  prefix dummy-service;
  import tailf-ncs {
    prefix ncs;
  }
  list dummy-service {
    key device;
    uses ncs:service-data;
    ncs:servicepoint \"dummy-service\";
    leaf device {
      type leafref {
        path \"/ncs:devices/ncs:device/ncs:name\";
      }
    }
    leaf dummy {
      type string;
    }
  }
}
" > nso-rundir/packages/dummy-service/src/yang/dummy-service.yang
printf "<config-template xmlns=\"http://tail-f.com/ns/config/1.0\"
                 servicepoint=\"dummy-service\">
  <devices xmlns=\"http://tail-f.com/ns/ncs\">
    <device>
      <name>{/device}</name>
      <config>
        <dummy xmlns=\"http://com/example/dummy\">{/dummy}</dummy>
      </config>
    </device>
  </devices>
</config-template>
" > nso-rundir/packages/dummy-service/templates/dummy-service-template.xml
make -C nso-rundir/packages/dummy-service/src all
ncs-netsim --dir nso-rundir/netsim create-network nso-rundir/packages/dummy-nc-1.0 2 d
ncs-setup --netsim-dir nso-rundir/netsim --dest nso-rundir
# Give the d1 simulated device a unique SSH host key
rm -f nso-rundir/netsim/d/d1/ssh/ssh_host_*
cd nso-rundir/netsim/d/d1/ssh
ssh-keygen -m PEM -t ed25519 -N '' -f ssh_host_ed25519_key
cd -

printf "\n${PURPLE}##### Start the NSO daemon and simulated devices\n${NC}"
ncs --cd nso-rundir -c $(pwd)/nso-rundir/ncs.conf
ncs-netsim start --dir nso-rundir/netsim

printf "\n${PURPLE}##### Start listening to all event notifications including the NETCONF stream \n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
python3 -u event_notifications.py --all --audit-sync --audit-network-sync --ha-info-sync --stream NETCONF --non-interactive | tee nso-rundir/logs/event.log &
echo $! > nso-rundir/notif-app.pid

# For replaying stream notifications
dt_string=$(python3 -c "from datetime import datetime, timezone; dt = datetime.now().isoformat(); print(f'{dt}')")

printf "${PURPLE}##### Enable the commit queue notification stream and generate notifications by adding some dummy configuration on d0 through the service and commit queue asynchronously\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
devices device d0 sync-from
config
services commit-queue-notifications subscription dummy-notif service-type /dummy-service:dummy-service
commit
dummy-service d0 dummy "hello world"
commit dry-run
commit commit-queue async tag "hello-world-cq-tag"
EOF

while : ; do
    arr=($(echo "show devices commit-queue queue-item | icount" | ncs_cli -C -u admin))
    res=${arr[1]}
    if [ "$res" == "0" ]; then
        break
    fi
    printf "${RED}##### Waiting for $res commit queue items to complete...\n${NC}"
    sleep .1
done

printf "\n${PURPLE}##### Get the dummy service config using NETCONF and RESTCONF\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
netconf-console --get-config -x /dummy-service
python3 -c 'import requests; session = requests.Session(); session.auth = ("admin", "admin"); r = session.get("http://localhost:8080/restconf/data/dummy-service:dummy-service?content=config", headers={"Content-Type": "application/yang-data+json"}); print(r.text)'

printf "\n${PURPLE}##### Enable netconf-call-home and high-availablity in ncs.conf and reload the config\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_conf_tool -e "true" ncs-config netconf-call-home enabled < nso-rundir/ncs.conf > nso-rundir/ncs.conf.tmp && mv -f nso-rundir/ncs.conf.tmp nso-rundir/ncs.conf
ncs_conf_tool -a '  <ha>
    <enabled>true</enabled>
  </ha>' ncs-config < nso-rundir/ncs.conf > nso-rundir/ncs.conf.tmp && mv -f nso-rundir/ncs.conf.tmp nso-rundir/ncs.conf
ncs --reload

grep -q 'reopen_logs: completed' <(tail -n 1000 -f nso-rundir/logs/event.log)

printf "\n${PURPLE}##### Stop listening to all event notifications\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
kill $(cat nso-rundir/notif-app.pid)

printf "\n${PURPLE}##### Enable call home for the d1 device\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
devices device d1 state admin-state call-home
devices device d1 local-user admin
devices device d1 ssh host-key-verification reject-mismatch
devices device d1 ssh host-key ssh-ed25519 key-data "$(cat nso-rundir/netsim/d/d1/ssh/ssh_host_ed25519_key.pub)"
commit
EOF

printf "\n${PURPLE}##### Start listening to call-home, commit-simple, ha-info, stream ncs-events (replay from $dt_string), and heartbeat event notifications only and redirect to a log file\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
python3 -u event_notifications.py --call-home --commit-simple --ha-info --heartbeat --stream ncs-events --start-time $dt_string --non-interactive | tee nso-rundir/logs/call-home-event.log &
echo $! > nso-rundir/notif-app.pid

printf "${PURPLE}##### Wait for the first heartbeat before continuing\n${NC}"
grep -q 'tick heartbeat' <(tail -f nso-rundir/logs/call-home-event.log)

printf "${PURPLE}##### Have the d1 device call home\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
CONFD_IPC_PORT=5011 confd_cmd -dd -c "netconf_ssh_call_home 127.0.0.1 4334"

printf "\n${PURPLE}##### Wait for the call-home device connected event notification\n${NC}"
grep -q 'call_home: type=device connected' <(tail -f nso-rundir/logs/call-home-event.log)

printf "${PURPLE}##### Add some configuration to the d1 device\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
devices device d1 sync-from
config
dummy-service d1 dummy "calling home"
commit dry-run
commit commit-queue async tag "calling-home-cq-tag"
EOF

printf "\n\n${PURPLE}##### Wait for the commit-simple event notification\n${NC}"
grep -q 'commit_simple' <(tail -f nso-rundir/logs/call-home-event.log)

printf "${PURPLE}##### Enable HA and change role to generate ha-info notifications\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
ncs_cli -n -u admin -C << EOF
config
high-availability token super-secret
high-availability ha-node n1 address 127.0.0.1 nominal-role primary
commit
end
high-availability enable
high-availability be-none
high-availability be-primary
EOF

printf "\n\n${PURPLE}##### Get the received events in nso-rundir/logs/call-home-event.log\n${NC}"
if [ -z "$NONINTERACTIVE" ]; then
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    read -n 1 -s -r
fi
grep -q 'ha_info: this node is now primary' <(tail -f nso-rundir/logs/call-home-event.log)
cat nso-rundir/logs/call-home-event.log

if [ -z "$NONINTERACTIVE" ]; then
    printf "${GREEN}##### Cleanup\n${NC}"
    printf "${RED}##### Press any key to continue or ctrl-c to exit\n${NC}"
    tail -n 1 -f nso-rundir/logs/call-home-event.log &
    echo $! > nso-rundir/tailf.pid
    read -n 1 -s -r
    make stop
    make clean
fi

printf "\n${GREEN}##### Done!\n${NC}"

