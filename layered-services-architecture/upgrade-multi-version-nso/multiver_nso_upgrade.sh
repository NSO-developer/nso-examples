#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

function usage()
{
   printf "${GREEN}Demo upgrading the 28-lsa-multi-version-deployment\n"
   printf "example upper layer NSO instance to a newer NSO version\n\n"
   printf "  -d  Path to old NSO local install. Default: NCS_DIR environment variable\n"
   printf "  -p  Path to new NSO local install. Default: NCS_DIR environment variable\n"
   printf "  -o  Old NSO version. 5.4.1 or newer Default: Output of the ncs --version command\n"
   printf "  -n  New NSO version. 5.4.1 or newer. Default: Output of the ncs --version command\n\n"
   printf "I.e. if only default values are used the upgrade will be to the ${RED}same NSO version${GREEN},\n"
   printf "still performing the same upgrade steps for demo purposes.\n"
   printf "\nExample - upgrade the 5.4.1 28-lsa-multi-version-deployment example upper-nso and lower-nso-1 to 5.7:\n\n"
   printf "  \$ ./multiver_nso_upgrade.sh -o 5.4.1 -d /Users/tailf/nso-5.4.1 -n 5.7 -p /Users/tailf/nso-5.7\n\n${NC}"
}

# Retrieve the calling parameters.
while getopts "o:n:d:p:h" OPTION; do
    case "${OPTION}"
    in
        o)  OLD_VERSION="${OPTARG}";;
        n)  NEW_VERSION="${OPTARG}";;
        d)  OLD_DIR="${OPTARG}";;
        p)  NEW_DIR="${OPTARG}";;
        h)  usage; exit 0;;
        \?) echo "Invalid parameter"; usage; return 1;;
    esac
done

set +u
if [ -z "$OLD_VERSION" ]; then
    OLD_VERSION=$(ncs --version)
fi
if [ -z "$NEW_VERSION" ]; then
    NEW_VERSION=$(ncs --version)
fi
if [ -z "$OLD_DIR" ]; then
    OLD_DIR=${NCS_DIR}
fi
if [ -z "$NEW_DIR" ]; then
    NEW_DIR=${NCS_DIR}
fi

if [ -n "$NCS_IPC_PATH" ]; then
NODE1="NCS_IPC_PATH=${NCS_IPC_PATH}.4569"
NODE2="NCS_IPC_PATH=${NCS_IPC_PATH}.4570"
NODE3="NCS_IPC_PATH=${NCS_IPC_PATH}.4571"
else
# All nodes use the same IP for IPC but different ports
export NCS_IPC_ADDR=127.0.0.1
NODE1=NCS_IPC_PORT=4569
NODE2=NCS_IPC_PORT=4570
NODE3=NCS_IPC_PORT=4571
fi
set -u

if ! [ -d $NEW_DIR/packages/lsa/cisco-nso-nc-${OLD_VERSION::3} ]; then
    printf "\n${PURPLE}No $NEW_DIR/packages/lsa/cisco-nso-nc-${OLD_VERSION::3} for NSO $NEW_VERSION\n${NC}"
    printf "${RED}Upgrade not supported\n${NC}"
    exit 1
fi

function version_lt() { test "$(printf '%s\n' "$@" | sort -rV | head -n 1)" != "$1"; }
function version_ge() { test "$(printf '%s\n' "$@" | sort -rV | head -n 1)" == "$1"; }

NSO541=5.4.1
NSO55=5.5
NSO56=5.6

EXAMPLE_DIR=$(pwd)
LSA_EXAMPLE_DIR=lsa-multi-version-deployment

if version_lt $OLD_VERSION "6.4"; then
    ORIGINAL_EXAMPLE_DIR=$OLD_DIR/examples.ncs/getting-started/developing-with-ncs/28-lsa-multi-version-deployment
else
    ORIGINAL_EXAMPLE_DIR=$OLD_DIR/examples.ncs/layered-services-architecture/$LSA_EXAMPLE_DIR
fi

if version_lt $OLD_VERSION "5.4.1" || ! [ -d $ORIGINAL_EXAMPLE_DIR ]; then
    printf "\n${RED}No $ORIGINAL_EXAMPLE_DIR for NSO $OLD_VERSION\n${NC}"
    exit 1
fi

printf "\n${PURPLE}##### Stop any running NSO or netsim instances\n${NC}"
set +u
if [ -f "$NEW_DIR/ncsrc" ]; then
    source $NEW_DIR/ncsrc
fi
set -u
set +e
if [ -d $LSA_EXAMPLE_DIR ] ; then
    cd $LSA_EXAMPLE_DIR
    make stop
    env $NODE1 ncs --stop
    env $NODE2 ncs --stop
    env $NODE3 ncs --stop
    cd $EXAMPLE_DIR
fi
set -e

printf "\n${PURPLE}##### Get a copy of the $OLD_VERSION $ORIGINAL_EXAMPLE_DIR\n${NC}"
rm -rf lsa-multi-version-deployment packages
cp -r $ORIGINAL_EXAMPLE_DIR .

if version_lt $OLD_VERSION "6.4"; then
    cp -r $ORIGINAL_EXAMPLE_DIR/../packages .
else
    mkdir -p ./packages
    cp -r $ORIGINAL_EXAMPLE_DIR/../../common/packages/router-nc-1.1 ./packages/
fi

set +u
if [ -f "$OLD_DIR/ncsrc" ]; then
    source $OLD_DIR/ncsrc
fi
set -u

if version_lt $OLD_VERSION "6.4"; then
    make -C $EXAMPLE_DIR/packages/router/src clean
else
    make -C $EXAMPLE_DIR/packages/router-nc-1.1/src clean
fi


printf "\n${PURPLE}##### Build the $LSA_EXAMPLE_DIR example from version $OLD_VERSION\n${NC}"
cd $EXAMPLE_DIR/$LSA_EXAMPLE_DIR
if version_ge $OLD_VERSION "6.4"; then
    sed -i.bak -e 's|../../common|../../../common|' $EXAMPLE_DIR/$LSA_EXAMPLE_DIR/Makefile
fi
make clean all

printf "\n${PURPLE}##### Start the NSO $OLD_VERSION simulated network and the three NSO nodes\n${NC}"
set +e
make stop &> /dev/null
set -e
make start-all

printf "\n${PURPLE}##### Run the NSO $OLD_VERSION example and commit some CFS configuration\n${NC}"
env $NODE1 ncs_cli -u admin -C << EOF
config
cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77
commit dry-run
commit
end
show packages
show running-config ncs:devices device config rfs-vlan:services | display xml | display service-meta-data
EOF

printf "\n${PURPLE}##### Exit from the CLI, stop the upper-nso and lower-nso-1 NSO instances and switch from $OLD_VERSION to the newer NSO $NEW_VERSION version\n${NC}"
env $NODE1 ncs --stop
env $NODE2 ncs --stop

printf "\n\n${PURPLE}##### Backup both NSO and the netsim devices before upgrading\n##### Since we are using a local NSO install, we backup the runtime directory for potential disaster recovery.\n${NC}"
cd $EXAMPLE_DIR
make backup
cd $EXAMPLE_DIR/$LSA_EXAMPLE_DIR

printf "\n${PURPLE}##### Execute the ncsrc commands for NSO $NEW_VERSION\n${NC}"
set +u
if [ -f "$NEW_DIR/ncsrc" ]; then
    source $NEW_DIR/ncsrc
fi
set -u

printf "\n${RED}##### Important step: ${PURPLE}Rebuild the lower-nso-1 packages with the new NSO $NEW_VERSION\n${NC}"
cd "$EXAMPLE_DIR/$LSA_EXAMPLE_DIR/lower-nso-1"
for f in packages/*/src; do
  make -C $f clean all
done

printf "${RED}##### Important step: ${PURPLE}Make the necessary changes to the lower-nso-1 ncs.conf to upgrade from NSO $OLD_VERSION to $NEW_VERSION\n${NC}"
# NSO 5.5 added a trace-id, enabled by default, which is not recognized by the
# earlier versions of NSO. The show-log-directory parameter was removed.
if version_lt $OLD_VERSION $NSO55 && version_ge $NEW_VERSION $NSO55; then
    sed -i.bak -e 's%<show-log-directory>./logs</show-log-directory>%%'\
               ncs.conf
    rm ncs.conf.bak
fi
# NSO 5.6 removed the large-scale parameters
if version_lt $OLD_VERSION $NSO56 && version_ge $NEW_VERSION $NSO56; then
    sed -i.bak '/<large-scale>/I,+7 d' ncs.conf
    rm ncs.conf.bak
fi

printf "${RED}##### Important step: ${PURPLE}Restart the lower-nso-1 NSO instance and upgrade to ${NEW_VERSION::3}\n${NC}"
ncs --cdb-compact ncs-cdb
env $NODE2 sname=lower-nso-1 ncs -c ncs.conf --with-package-reload

if version_ge $NEW_VERSION $NSO56; then
    printf "${PURPLE}##### Before NSO 5.6, on the ConfD/netsim instances, ssh-rsa was the only algorithm that had a host key installed and therefore\n"
    printf "      we need to add ssh-rsa to the list of supported algorithms southbound for the 5.6+ lower-nso-1 NSO instance\n${NC}"
    env $NODE2 ncs_cli -u admin -C << EOF
config
insert ncs:devices global-settings ssh-algorithms public-key ssh-rsa
commit
EOF
fi

printf "\n${RED}##### Important step: ${PURPLE}Rebuild the cfs-vlan package with the new NSO $NEW_VERSION\n${NC}"
cd "$EXAMPLE_DIR/$LSA_EXAMPLE_DIR/upper-nso/packages"
make clean all -C cfs-vlan/src

printf "\n${RED}##### Important step: ${PURPLE}Replace the old NSO $OLD_VERSION tailf/cisco-nso-nc-${OLD_VERSION::3} package with\n${NC}"
printf "${PURPLE}                      the NSO $NEW_VERSION NETCONF NED package for ${OLD_VERSION::3} and add the $NEW_VERSION to support both if they differ\n${NC}"
rm -rf *-nso-nc-${OLD_VERSION::3}
ln -sf ${NCS_DIR}/packages/lsa/cisco-nso-nc-${OLD_VERSION::3} .
ln -sf ${NCS_DIR}/packages/lsa/cisco-nso-nc-${NEW_VERSION::3} .

OLD_RFS_NED=rfs-vlan-nc-${OLD_VERSION::3}
NEW_RFS_NED=rfs-vlan-nc-${NEW_VERSION::3}
printf "${RED}##### Important step: ${PURPLE}Copy the $OLD_RFS_NED to $NEW_RFS_NED and update the package-meta-data.xml + Makefile for the $NEW_RFS_NED package and rebuild the RFS NEDs\n${NC}"
if ! [ -d $NEW_RFS_NED ]; then
  cp -r $OLD_RFS_NED $NEW_RFS_NED
fi

sed -i.bak -e "s%${OLD_VERSION::3}%${NEW_VERSION::3}%g" $NEW_RFS_NED/src/Makefile
rm $NEW_RFS_NED/src/Makefile.bak
sed -i.bak -e "s%${OLD_VERSION::3}%${NEW_VERSION::3}%g" $NEW_RFS_NED/src/package-meta-data.xml.in
rm $NEW_RFS_NED/src/package-meta-data.xml.in.bak

make clean all -C $NEW_RFS_NED/src
make clean all -C $OLD_RFS_NED/src

printf "${RED}##### Important step: ${PURPLE}Make the necessary changes to ncs.conf to upgrade from NSO $OLD_VERSION to $NEW_VERSION\n${NC}"
cd "$EXAMPLE_DIR/$LSA_EXAMPLE_DIR/upper-nso"
# NSO 5.5 added a trace-id, enabled by default, which is not recognized by the
# earlier versions of NSO. The show-log-directory parameter was removed.
if version_lt $OLD_VERSION $NSO55 && version_ge $NEW_VERSION $NSO55; then
    sed -i.bak -e 's%<show-log-directory>./logs</show-log-directory>%%'\
               ncs.conf
    rm ncs.conf.bak
fi
# NSO 5.6 removed the large-scale parameters
if version_lt $OLD_VERSION $NSO56 && version_ge $NEW_VERSION $NSO56; then
    sed -i.bak '/<large-scale>/I,+7 d' ncs.conf
    rm ncs.conf.bak
fi

printf "${RED}##### Important step: ${PURPLE}Restart the upper NSO instance and upgrade to ${NEW_VERSION::3}\n${NC}"
env $NODE1 ncs --cdb-compact ncs-cdb
env $NODE1 sname=upper-nso ncs -c ncs.conf --with-package-reload

printf "${RED}##### Important step: ${PURPLE}Migrate the lower-nso-1 $OLD_VERSION NED ID to $NEW_VERSION\n${NC}"
env $NODE1 ncs_cli -u admin -C << EOF
config
ncs:devices device lower-nso-1 ssh fetch-host-keys
ncs:devices device lower-nso-1 migrate new-ned-id cisco-nso-nc-${NEW_VERSION::3} dry-run verbose
ncs:devices device lower-nso-1 migrate new-ned-id cisco-nso-nc-${NEW_VERSION::3} verbose
commit
EOF

env $NODE1 ncs_cli -u admin -C << EOF
config
ncs:devices device lower-nso-* out-of-sync-commit-behaviour accept
commit
EOF

printf "\n${PURPLE}##### Check if there are changes using re-deploy\n${NC}"
env $NODE1 ncs_cli -u admin -C << EOF
config
cfs-vlan v1 re-deploy dry-run
EOF

printf "\n${PURPLE}##### No changes should be indicated.\n${NC}"
printf "\n${PURPLE}##### Also, check the refcounters and back pointers\n${NC}"

env $NODE1 ncs_cli -u admin -C << EOF
show running-config ncs:devices device config rfs-vlan:services | display xml | display service-meta-data
EOF

printf "\n${PURPLE}##### At this stage we have a CFS node built for version $NEW_VERSION and 1 RFS node for $OLD_VERSION and the other for $NEW_VERSION.\n${NC}"
printf "${PURPLE}##### The cfs-vlan and $OLD_RFS_NED packages should still have the same ned-id.\n${NC}"
printf "${PURPLE}##### Thus, except for the lower-nso-1 node to the $NEW_RFS_NED package, no migration was needed and no models have been discarded.\n${NC}"
env $NODE1 ncs_cli -u admin -C << EOF
show packages
EOF
printf "\n${GREEN}##### DONE!\n${NC}"
