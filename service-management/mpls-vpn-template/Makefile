# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
PACKAGES = l3vpn

# Directory of example packages
PACKAGE_STORE=$(NCS_DIR)/packages/neds

# The create-network argument to ncs-netsim
NETWORK = \
	create-network $(PACKAGE_STORE)/cisco-ios-cli-3.8     9 ce \
	create-network $(PACKAGE_STORE)/cisco-iosxr-cli-3.5   2 pe \
	create-network $(PACKAGE_STORE)/juniper-junos-nc-3.0  1 pe \
	create-network $(PACKAGE_STORE)/alu-sr-cli-3.4        1 pe \
	create-network $(PACKAGE_STORE)/cisco-iosxr-cli-3.5   4 p

NETSIM_DIR = netsim

all: packages netsim ncs
.PHONY: all

packages:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src all || exit 1; \
	done
.PHONY: packages

netsim:
	ncs-netsim --dir $@ $(NETWORK)
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

ncs:
	ncs-setup --netsim-dir netsim --dest .
	sed -i.bkp -e 's!<cli>!<cli><default-table-behavior>dynamic</default-table-behavior>!' ncs.conf
	cp initial_data/topology.xml ncs-cdb
	cp initial_data/qos.xml ncs-cdb
	cp initial_data/compliance.xml ncs-cdb
	cp initial_data/template.xml ncs-cdb
	cp initial_data/device-groups.xml ncs-cdb
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml
.PHONY: ncs

clean:
	for i in $(PACKAGES); do                           \
	    $(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	rm -f README.ncs README.netsim
	rm -rf netsim logs state ncs-cdb *.trace
	rm -f packages/alu-sr-cli-3.4
	rm -f packages/cisco-ios-cli-3.8
	rm -f packages/cisco-iosxr-cli-3.5
	rm -f packages/juniper-junos-nc-3.0
	rm -rf bin
.PHONY: clean

start:
	ncs-netsim --async start
	ncs
.PHONY: start

stop:
	-ncs-netsim --async stop
	-ncs --stop
.PHONY: stop

reset:
	ncs-setup --reset
.PHONY: reset

cli:
	ncs_cli -u admin
.PHONY: cli

demo:
	./demo.sh
