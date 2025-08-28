# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
PACKAGES = connectivity

# Directory of example packages
PACKAGE_STORE=$(NCS_DIR)/examples.ncs/common/packages

# The create-network argument to ncs-netsim
NETWORK = \
	create-network $(PACKAGE_STORE)/cisco-ios-netsim-cli-1.0   6 catalyst \
	create-network $(PACKAGE_STORE)/cisco-iosxr-netsim-cli-1.0 4 asr      \
	create-network $(PACKAGE_STORE)/cisco-nx-netsim-cli-1.0    4 nexus    \
	create-network $(PACKAGE_STORE)/dell-ftos-netsim-cli-1.0   2 dell

all: packages netsim ncs
.PHONY: all

packages:
	$(MAKE) -C $(PACKAGE_STORE)/cisco-ios-netsim-cli-1.0/src all
	$(MAKE) -C $(PACKAGE_STORE)/cisco-iosxr-netsim-cli-1.0/src all
	$(MAKE) -C $(PACKAGE_STORE)/cisco-nx-netsim-cli-1.0/src all
	$(MAKE) -C $(PACKAGE_STORE)/dell-ftos-netsim-cli-1.0/src all
	for i in $(PACKAGES); do                         \
	    $(MAKE) -C packages/$${i}/src all || exit 1; \
	done
.PHONY: packages

netsim:
	ncs-netsim --dir $@ $(NETWORK)
	cp initial_data/ios.xml netsim/catalyst/catalyst0/cdb
	cp initial_data/ios.xml netsim/catalyst/catalyst1/cdb
	cp initial_data/ios.xml netsim/catalyst/catalyst2/cdb
	cp initial_data/ios.xml netsim/catalyst/catalyst3/cdb
	cp initial_data/ios.xml netsim/catalyst/catalyst4/cdb
	cp initial_data/ios.xml netsim/catalyst/catalyst5/cdb
	cp initial_data/nexus.xml netsim/nexus/nexus0/cdb
	cp initial_data/nexus.xml netsim/nexus/nexus1/cdb
	cp initial_data/nexus.xml netsim/nexus/nexus2/cdb
	cp initial_data/nexus.xml netsim/nexus/nexus3/cdb
	cp initial_data/iosxr.xml netsim/asr/asr0/cdb
	cp initial_data/iosxr.xml netsim/asr/asr1/cdb
	cp initial_data/iosxr.xml netsim/asr/asr2/cdb
	cp initial_data/iosxr.xml netsim/asr/asr3/cdb
	cp initial_data/asr0.xml netsim/asr/asr0/cdb
	cp initial_data/asr1.xml netsim/asr/asr1/cdb
	cp initial_data/asr2.xml netsim/asr/asr2/cdb
	cp initial_data/asr3.xml netsim/asr/asr3/cdb
	cp initial_data/nexus0.xml netsim/nexus/nexus0/cdb
	cp initial_data/nexus1.xml netsim/nexus/nexus1/cdb
	cp initial_data/nexus2.xml netsim/nexus/nexus2/cdb
	cp initial_data/nexus3.xml netsim/nexus/nexus3/cdb
	cp initial_data/f10.xml netsim/dell/dell0/cdb
	cp initial_data/f10.xml netsim/dell/dell1/cdb

ncs:
	ncs-setup --netsim-dir netsim --dest .
	cp initial_data/topology.xml ncs-cdb
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml
.PHONY: ncs

clean:
	for i in $(PACKAGES); do \
	    $(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	$(MAKE) -C alarms clean
	rm -f README.ncs README.netsim
	rm -rf netsim logs state ncs-cdb *.trace
	rm -f packages/cisco-ios-netsim-cli-1.0
	rm -f packages/cisco-nx-netsim-cli-1.0
	rm -f packages/cisco-iosxr-netsim-cli-1.0
	rm -f packages/dell-ftos-netsim-cli-1.0
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
