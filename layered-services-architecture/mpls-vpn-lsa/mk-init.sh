#!/bin/sh

set -e

if [ ! -d netsim ]; then

    ncs-netsim --dir netsim  \
        create-network device-nodes/packages/cisco-ios-netsim-cli-1.0      9 ce \
        create-network device-nodes/packages/cisco-iosxr-netsim-cli-1.0    2 pe \
        create-network device-nodes/packages/juniper-junos-netsim-nc-1.0   1 pe \
        create-network device-nodes/packages/alu-sr-netsim-cli-1.0         1 pe \
        create-network device-nodes/packages/cisco-iosxr-netsim-cli-1.0    4 p


    cp initial_data/ios.xml netsim/ce/ce0/cdb
    cp initial_data/ios.xml netsim/ce/ce1/cdb
    cp initial_data/ios.xml netsim/ce/ce2/cdb
    cp initial_data/ios.xml netsim/ce/ce3/cdb
    cp initial_data/ios.xml netsim/ce/ce4/cdb
    cp initial_data/ios.xml netsim/ce/ce5/cdb
    cp initial_data/ios.xml netsim/ce/ce6/cdb
    cp initial_data/ios.xml netsim/ce/ce7/cdb
    cp initial_data/ios.xml netsim/ce/ce8/cdb
    cp initial_data/iosxr.xml netsim/pe/pe0/cdb
    cp initial_data/iosxr.xml netsim/pe/pe1/cdb
    cp initial_data/alu-sr.xml netsim/pe/pe3/cdb
    cp initial_data/iosxr.xml netsim/p/p0/cdb
    cp initial_data/iosxr.xml netsim/p/p1/cdb
    cp initial_data/iosxr.xml netsim/p/p2/cdb
    cp initial_data/iosxr.xml netsim/p/p3/cdb
fi


for i in `echo 1 2 3`; do
    dir=device-nodes/nso-${i}/
    mkdir -p ${dir}/ncs-cdb;
    mkdir -p ${dir}/logs
    mkdir -p ${dir}/state
    cp initial_data/template.xml ${dir}/ncs-cdb
done

dir=service-node
mkdir -p ${dir}/ncs-cdb;
mkdir -p ${dir}/logs
mkdir -p ${dir}/state


for i in `echo 1 2 3`; do
    echo '<config xmlns="http://tail-f.com/ns/config/1.0">' > \
         device-nodes/nso-${i}/ncs-cdb/netsim_devices_init.xml
done

for i in `echo 0 1 2 3 4 5 6 7 8`; do
    ncs-netsim ncs-xml-init ce${i} >> \
        device-nodes/nso-1/ncs-cdb/netsim_devices_init.xml
done

for i in `echo 0 1 2 3`; do
    ncs-netsim ncs-xml-init p${i} >> \
        device-nodes/nso-2/ncs-cdb/netsim_devices_init.xml
done

for i in `echo 0 1 2 3`; do
    ncs-netsim ncs-xml-init pe${i} >> \
        device-nodes/nso-3/ncs-cdb/netsim_devices_init.xml
done

for i in `echo 1 2 3`; do
    echo '</config>' >> \
         device-nodes/nso-${i}/ncs-cdb/netsim_devices_init.xml
done


