# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
PACKAGES = \
	pica8-nc-1.0 \
	qinq qinq-alarm \
	qinq-template qinq-java-template

# Directory of example packages
PACKAGE_STORE=$(NCS_DIR)/packages/neds

# The create-network argument to ncs-netsim
NETWORK = \
	create-network $(PACKAGE_STORE)/cisco-ios-cli-3.0   3 c \
	create-network packages/pica8-nc-1.0                4 p

NETSIM_DIR = netsim

all: packages netsim ncs
.PHONY: all

packages:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src all || exit 1; \
	done
.PHONY: packages

netsim:
	ncs-netsim --dir netsim $(NETWORK)
	cp initial_data/ios_init.xml netsim/c/c0/cdb
	cp initial_data/ios_init.xml netsim/c/c1/cdb
	cp initial_data/ios_init.xml netsim/c/c2/cdb
	cp initial_data/p0_init.xml initial_data/pica8_init.xml netsim/p/p0/cdb
	cp initial_data/p1_init.xml initial_data/pica8_init.xml netsim/p/p1/cdb
	cp initial_data/p2_init.xml initial_data/pica8_init.xml netsim/p/p2/cdb
	cp initial_data/p3_init.xml initial_data/pica8_init.xml netsim/p/p3/cdb

ncs:
	ncs-setup --netsim-dir netsim --dest .
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml
	cp initial_data/device-description-init.xml ncs-cdb
.PHONY: ncs

clean:
	for i in $(PACKAGES); do \
	    $(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	$(MAKE) -C alarms clean
	rm -rf netsim logs state ncs-cdb *.trace
	rm -f packages/cisco-ios-cli-3.0
	rm -rf bin
	rm -f README.ncs README.netsim
.PHONY: clean

start:
	ncs-netsim --async start
	cp initial_data/device-description-init.xml ncs-cdb
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
