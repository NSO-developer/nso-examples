# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
PACKAGES = l3vpn

# Directory of example packages
PACKAGE_STORE=$(NCS_DIR)/examples.ncs/common/packages

# The create-network argument to ncs-netsim
NETWORK = \
	create-network $(PACKAGE_STORE)/cisco-ios-netsim-cli-1.0     9 ce \
	create-network $(PACKAGE_STORE)/cisco-iosxr-netsim-cli-1.0   2 pe \
	create-network $(PACKAGE_STORE)/juniper-junos-netsim-nc-1.0  1 pe \
	create-network $(PACKAGE_STORE)/cisco-iosxr-netsim-cli-1.0   1 pe \
	create-network $(PACKAGE_STORE)/cisco-iosxr-netsim-cli-1.0   4 p

all: packages netsim ncs
.PHONY: all

packages:
	$(MAKE) -C $(PACKAGE_STORE)/cisco-ios-netsim-cli-1.0/src all
	$(MAKE) -C $(PACKAGE_STORE)/cisco-iosxr-netsim-cli-1.0/src all
	$(MAKE) -C $(PACKAGE_STORE)/alu-sr-netsim-cli-1.0/src all
	$(MAKE) -C $(PACKAGE_STORE)/juniper-junos-netsim-nc-1.0/src all
	for i in $(PACKAGES); do \
	    $(MAKE) -C packages/$${i}/src all || exit 1; \
	done
.PHONY: packages

netsim:
	ncs-netsim --dir netsim $(NETWORK)
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
	cp initial_data/iosxr.xml netsim/pe/pe3/cdb
	cp initial_data/iosxr.xml netsim/p/p0/cdb
	cp initial_data/iosxr.xml netsim/p/p1/cdb
	cp initial_data/iosxr.xml netsim/p/p2/cdb
	cp initial_data/iosxr.xml netsim/p/p3/cdb

ncs:
	ncs-setup --netsim-dir netsim --dest .
	sed -i.bkp -e 's!<cli>!<cli><default-table-behavior>dynamic</default-table-behavior>!g' ncs.conf
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml
	cp initial_data/topology.xml ncs-cdb
	cp initial_data/qos.xml ncs-cdb
	cp initial_data/compliance.xml ncs-cdb
	cp initial_data/template.xml ncs-cdb
	cp initial_data/python.xml ncs-cdb
	cp initial_data/device-groups.xml ncs-cdb
.PHONY: ncs

clean:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	rm -f README.ncs README.netsim
	rm -rf netsim logs state ncs-cdb *.trace ncs.conf*
	rm -f packages/cisco-ios-netsim-cli-1.0
	rm -f packages/cisco-iosxr-netsim-cli-1.0
	rm -f packages/juniper-junos-netsim-nc-1.0
	rm -rf bin
	rm -f clicmd.tmp waiter.sh
.PHONY: clean

start:
	ncs-netsim --async start
	ncs
.PHONY: start

starti4:
	ncs-netsim start
	ncs -i -d 4 '[python]'
.PHONY: starti4

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
