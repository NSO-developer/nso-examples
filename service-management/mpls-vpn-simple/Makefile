# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
PACKAGES = l3vpn

# Directory of example packages
PACKAGE_STORE=$(NCS_DIR)/packages/neds

# The create-network argument to ncs-netsim
NETWORK = \
	create-network $(PACKAGE_STORE)/cisco-ios-cli-3.0    6 ce \
	create-network $(PACKAGE_STORE)/cisco-iosxr-cli-3.0  3 pe

all: packages netsim ncs
.PHONY: all

packages:
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
	cp initial_data/iosxr.xml netsim/pe/pe0/cdb
	cp initial_data/iosxr.xml netsim/pe/pe1/cdb
	cp initial_data/iosxr.xml netsim/pe/pe2/cdb

ncs:
	ncs-setup --netsim-dir netsim --dest .
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml
.PHONY: ncs

clean:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	rm -rf netsim logs state ncs-cdb *.trace
	rm -f packages/cisco-ios-cli-3.0
	rm -f packages/cisco-iosxr-cli-3.0
	rm -f README.ncs README.netsim ncs.conf
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
	ncs_cli -C -u admin
.PHONY: cli

demo:
	./demo.sh
