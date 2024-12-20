# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
PACKAGES = basic-firewall

# Directory of example packages
PACKAGE_STORE=$(NCS_DIR)/packages/neds

# The create-network argument to ncs-netsim
NETWORK = \
	create-network $(PACKAGE_STORE)/cisco-ios-cli-3.0     1 c \
	create-network $(PACKAGE_STORE)/juniper-junos-nc-3.0  1 j

all:  packages netsim ncs
.PHONY: all

packages:
	for i in $(PACKAGES); do \
	    $(MAKE) -C packages/$${i}/src all || exit 1; \
	done
.PHONY: packages

netsim:
	ncs-netsim --dir netsim $(NETWORK)

ncs:
	ncs-setup --netsim-dir netsim --dest .
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml
.PHONY: ncs

clean:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	rm -f README.ncs README.netsim ncs.conf
	rm -rf netsim state ncs-cdb *.trace
	rm -f packages/cisco-ios-cli-3.0
	rm -f packages/juniper-junos-nc-3.0
	rm -rf bin logs/*
.PHONY: clean

demo:
	./demo.sh
