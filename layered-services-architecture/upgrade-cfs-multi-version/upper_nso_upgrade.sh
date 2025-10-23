#!/usr/bin/env bash
set -eu # Abort the script if a command returns with a non-zero exit code or if
        # a variable name is dereferenced when the variable hasn't been set

RED='\033[0;31m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

function usage()
{
   printf "${GREEN}Demo upgrading the"
   printf " 22-layered-service-architecture/lsa-single-version-deployment\n"
   printf "example upper layer NSO instance to a newer NSO version\n\n"
   printf "  -d  Path to old NSO local install. Default: NCS_DIR environment variable\n"
   printf "  -p  Path to new NSO local install. Default: NCS_DIR environment variable\n"
   printf "  -o  Old NSO version. 5.x or newer Default: Output of the ncs --version command\n"
   printf "  -n  New NSO version. 5.4.1 or newer. Default: Output of the ncs --version command\n\n"
   printf "I.e. if only default values are used the upgrade will be to the ${RED}same NSO version${GREEN},\n"
   printf "still performing the same upgrade steps for demo purposes.\n"
   printf "\nTo, for example, upgrade the 5.4.5 22-layered-service-architecture example upper layer NSO to 5.7:\n\n"
   printf "  \$ ./upper_nso_upgrade.sh -o 5.4.5 -d /Users/tailf/nso-5.4.5 -n 5.7 -p /Users/tailf/nso-5.7\n\n${NC}"
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

NSO5=5
NSO53=5.3
NSO541=5.4.1
NSO55=5.5
NSO56=5.6

if version_lt $OLD_VERSION $NSO5; then
    printf "${RED}Not possible to upgrade from $OLD_VERSION to a multi-version NED. First upgrade $OLD_VERSION to a 5.x single version NED\n${NC}"
    exit 1
fi

EXAMPLE_DIR=$(pwd)
LSA_EXAMPLE_DIR=lsa-single-version-deployment

# The LSA example changed name from "22-layered-service-architecture" to
# "22-lsa-single-version-deployment" in NSO 5.4.1 and to
# "lsa-single-version-deployment" in NSO 6.4
if version_lt $OLD_VERSION "5.4.1"; then
    ORIGINAL_EXAMPLE_DIR=$OLD_DIR/examples.ncs/getting-started/developing-with-ncs/22-layered-service-architecture
elif version_lt $OLD_VERSION "6.4"; then
    ORIGINAL_EXAMPLE_DIR=$OLD_DIR/examples.ncs/getting-started/developing-with-ncs/22-lsa-single-version-deployment
else
    ORIGINAL_EXAMPLE_DIR=$OLD_DIR/examples.ncs/layered-services-architecture/$LSA_EXAMPLE_DIR
fi

printf "\n${PURPLE}##### Stop any running NSO or netsim instances\n${NC}"
set +u
if [ -f "$NEW_DIR/ncsrc" ]; then
    source $NEW_DIR/ncsrc
fi
set -u
set +e
if [ -d $EXAMPLE_DIR/lsa-single-version-deployment ] ; then
    cd lsa-single-version-deployment
    ncs-netsim stop
    cd $EXAMPLE_DIR
fi
env $NODE1 ncs --stop
env $NODE2 ncs --stop
env $NODE3 ncs --stop
set -e

printf "\n${PURPLE}##### Get a copy of the $OLD_VERSION $ORIGINAL_EXAMPLE_DIR\n${NC}"
rm -rf lsa-single-version-deployment packages
cp -r $ORIGINAL_EXAMPLE_DIR .

if version_lt $OLD_VERSION "6.4"; then
    cp -r $ORIGINAL_EXAMPLE_DIR/../packages ./lsa-single-version-deployment
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
if version_ge $OLD_VERSION "6.4"; then
    sed -i.bak -e 's|../../common|../../../common|' $EXAMPLE_DIR/$LSA_EXAMPLE_DIR/Makefile
fi
cd $EXAMPLE_DIR/$LSA_EXAMPLE_DIR
make clean all

printf "\n${PURPLE}##### Start the NSO $OLD_VERSION simulated network and the three NSO nodes\n${NC}"
set +e
make stop &> /dev/null
set -e
make start

printf "\n${PURPLE}##### Run the NSO $OLD_VERSION example and commit some example CFS configuration.\n${NC}"
if version_lt $OLD_VERSION $NSO541; then
    RFS_VLAN=rfs-vlan:vlan
else
    RFS_VLAN=rfs-vlan:services
fi
env $NODE1 ncs_cli -u admin -C << EOF
config
cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77
commit dry-run
commit
end
show packages
show running-config ncs:devices device config $RFS_VLAN | display xml | display service-meta-data
EOF

printf "\n${PURPLE}##### Always take a backup here if necessary. Stop the upper layer NSO instances and switch from $OLD_VERSION to the newer NSO $NEW_VERSION version\n${NC}"
env $NODE1 ncs --stop

printf "\n\n${PURPLE}##### Backup both NSO and the netsim devices before upgrading\n##### Since we are using a local NSO install, we backup the runtime directory for potential disaster recovery.\n${NC}"
cd $EXAMPLE_DIR
make backup
cd "$EXAMPLE_DIR/$LSA_EXAMPLE_DIR/upper-nso/packages"

printf "\n${PURPLE}##### Execute the ncsrc commands for NSO $NEW_VERSION\n${NC}"
set +u
if [ -f "$NEW_DIR/ncsrc" ]; then
    source $NEW_DIR/ncsrc
fi
set -u
make clean all -C cfs-vlan/src

printf "\n${RED}##### Important step: ${PURPLE}Replace the old NSO $OLD_VERSION tailf/cisco-nso-nc-${OLD_VERSION::3} package with \n"
printf "                      the NSO $NEW_VERSION NETCONF NED package for ${OLD_VERSION::3}\n${NC}"
rm -rf *-nso-nc-${OLD_VERSION::3}
ln -sf ${NCS_DIR}/packages/lsa/cisco-nso-nc-${OLD_VERSION::3} .

# The LSA example rfs package changed name in NSO 5.4.1
if version_lt $OLD_VERSION $NSO541; then
    RFS_NED=rfs-ned
else
    RFS_NED=rfs-vlan-ned
fi

MULTIVER_RFS_NED=rfs-vlan-nc-${OLD_VERSION::3}
printf "${RED}##### Multi-version step: ${PURPLE}Copy the $RFS_NED to $MULTIVER_RFS_NED \n${NC}"
cp -r $RFS_NED $MULTIVER_RFS_NED

printf "${RED}##### Multi-version step: ${PURPLE}Have the RFS NED makefile --ncs-ned-id flag use the multi-version NED ID\n${NC}"
# Replace the existing lsa-netconf NED ID with the multiversion variant
sed -i.bak "s/--ncs-ned-id tailf-ncs-ned:lsa-netconf/--ncs-ned-id cisco-nso-nc-${OLD_VERSION::3}:cisco-nso-nc-${OLD_VERSION::3}/" $MULTIVER_RFS_NED/src/Makefile
rm $MULTIVER_RFS_NED/src/Makefile.bak
sed -i.bak "s%NCSCPATH   =%NCSCPATH   = --yangpath \.\./\.\./cisco-nso-nc-${OLD_VERSION::3}/src --ncs-depend-package \.\./\.\./cisco-nso-nc-${OLD_VERSION::3} #%" $MULTIVER_RFS_NED/src/Makefile
rm $MULTIVER_RFS_NED/src/Makefile.bak
sed -i.bak "s%NCSCPATH   =%NCSCPATH   = --yangpath \.\./\.\./cisco-nso-nc-${OLD_VERSION::3}/src/yang #%" $RFS_NED/src/Makefile
rm $RFS_NED/src/Makefile.bak

printf "${RED}##### Multi-version step: ${PURPLE}Update the package-meta-data.xml for the $MULTIVER_RFS_NED package\n${NC}"
sed -i.bak -e "s%$RFS_NED%$MULTIVER_RFS_NED%g" $MULTIVER_RFS_NED/src/package-meta-data.xml.in
rm $MULTIVER_RFS_NED/src/package-meta-data.xml.in.bak
sed -i.bak -e "s%<ned-id xmlns:id=\"http://tail-f.com/ns/ncs-ned\">id:lsa-netconf</ned-id>%<ned-id xmlns:id=\"http://tail-f.com/ns/ned-id/cisco-nso-nc-${OLD_VERSION::3}\">id:cisco-nso-nc-${OLD_VERSION::3}</ned-id>%g" $MULTIVER_RFS_NED/src/package-meta-data.xml.in
rm $MULTIVER_RFS_NED/src/package-meta-data.xml.in.bak

printf "${RED}##### Multi-version step: ${PURPLE}Rebuild the single and multi-version RFS packages\n${NC}"
make clean all -C $MULTIVER_RFS_NED/src
make clean all -C $RFS_NED/src

printf "${RED}##### Important step: ${PURPLE}Make the necessary changes to ncs.conf to upgrade from NSO $OLD_VERSION to $NEW_VERSION\n${NC}"
cd "$EXAMPLE_DIR/$LSA_EXAMPLE_DIR/upper-nso"
# NSO 5.3 added a mandatory encrypted-strings/AES256CFB128 key parameter
if version_lt $OLD_VERSION $NSO53; then # New version assumed to be > 5.4.1
    sed -i.bak 's%</encrypted-strings>%<AES256CFB128><key>0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef</key></AES256CFB128></encrypted-strings>%'\
                ncs.conf
    rm ncs.conf.bak
fi
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

printf "${PURPLE}##### Restart the upper NSO instance and upgrade to ${NEW_VERSION::3}\n${NC}"
env $NODE1 ncs --cdb-compact ncs-cdb
env $NODE1 sname=upper-nso ncs -c ncs.conf --with-package-reload

printf "${RED}##### Multi-version step: ${PURPLE}Migrate the upper NSO single version NED ID to a multi-version NED ID\n${NC}"
env $NODE1 ncs_cli -u admin -C << EOF
config
ncs:devices device lower-nso-* ssh fetch-host-keys
ncs:devices device lower-nso-1 migrate new-ned-id cisco-nso-nc-${OLD_VERSION::3} dry-run report { all }
ncs:devices device lower-nso-1 migrate new-ned-id cisco-nso-nc-${OLD_VERSION::3} report { all }
ncs:devices device lower-nso-2 migrate new-ned-id cisco-nso-nc-${OLD_VERSION::3} dry-run report { all }
ncs:devices device lower-nso-2 migrate new-ned-id cisco-nso-nc-${OLD_VERSION::3} report { all }
ncs:devices device lower-nso-* out-of-sync-commit-behaviour accept
commit
EOF

printf "${RED}##### Multi-version step: ${PURPLE}Delete the single version $RFS_NED package and reload packages\n${NC}"
printf "${RED}##### Warning: ${PURPLE}If the migrate to the multi-version NED ID earlier failed, we will loose data if we delete the single version NED\n${NC}"
rm -rf $EXAMPLE_DIR/$LSA_EXAMPLE_DIR/upper-nso/packages/$RFS_NED
env $NODE1 ncs_cli -u admin -C << EOF
packages reload force
EOF

printf "\n${PURPLE}##### Check if there are changes using re-deploy\n${NC}"
env $NODE1 ncs_cli -u admin -C << EOF
config
cfs-vlan v1 re-deploy dry-run
EOF

printf "\n${PURPLE}##### No changes should be indicated.\n${NC}"
printf "\n${PURPLE}##### Also, check the refcounters and back pointers\n${NC}"

env $NODE1 ncs_cli -u admin -C << EOF
show running-config ncs:devices device config $RFS_VLAN | display xml | display service-meta-data
EOF

printf "\n${PURPLE}##### At this stage we have a CFS node built for version $NEW_VERSION and 2 RFS nodes built for version $OLD_VERSION.\n${NC}"
printf "${PURPLE}##### The cfs-vlan and $RFS_NED packages should still have the same ned-id.\n${NC}"
printf "${PURPLE}##### Thus no migration was needed and no models have been discarded.\n${NC}"
env $NODE1 ncs_cli -u admin -C << EOF
show packages
EOF

printf "\n${GREEN}##### DONE!\n${NC}"
