# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
NEDS = cisco-ios-netsim-cli-1.0 cisco-iosxr-netsim-cli-1.0
PACKAGES = esc vm-manager l3vpn

# The create-network argument to ncs-netsim
NETWORK = create-network cisco-ios-netsim-cli-1.0 4 ce \
          create-network cisco-iosxr-netsim-cli-1.0 1 pe \
          create-network cisco-iosxr-netsim-cli-1.0 1 p

NETSIM_DIR = netsim

all: build-all $(NETSIM_DIR)

build-all: $(NEDS:%=packages/%)
	for i in $(PACKAGES) $(NEDS); do \
	  $(MAKE) -C packages/$${i}/src all || exit 1; \
	done

packages/%: $(NCS_DIR)/examples.ncs/common/packages/%
	@ln -s $< $@

MKDIR_P = mkdir -p

$(NETSIM_DIR):
	${MKDIR_P} ncs-cdb logs state
	ncs-netsim --dir netsim $(NETWORK) create-network packages/esc 1 esc
	cp initial_data/ios.xml netsim/ce/ce0/cdb
	cp initial_data/ios.xml netsim/ce/ce1/cdb
	cp initial_data/ios.xml netsim/ce/ce2/cdb
	cp initial_data/ios.xml netsim/ce/ce3/cdb
	cp initial_data/iosxr.xml netsim/pe/pe0/cdb
	cp initial_data/iosxr.xml netsim/p/p0/cdb
	cp initial_data/esc.xml netsim/esc/esc0/cdb
	cp initial_data/topology.xml ncs-cdb
	cp initial_data/template.xml ncs-cdb
	cp initial_data/qos.xml ncs-cdb
	cp initial_data/device-groups.xml ncs-cdb
	cp initial_data/virtual_pe.xml ncs-cdb
	cp initial_data/plan_notif.xml ncs-cdb
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml

clean:
	for i in $(PACKAGES); do \
	  $(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	rm -f $(NEDS:%=packages/%)
	rm -rf netsim
	rm -rf *.trace
	rm -rf bin
	rm -rf logs/*
	rm -rf state/*
	rm -f storedstate
	rm -rf ncs-cdb/*.cdb
	rm -rf ncs-cdb/*.xml

stop:
	ncs-netsim stop || true
	ncs --stop || true
	@echo "##### All NSO and netsim instances stopped"

# We need ignore initial validation since we are loading ip address pool and
# id pool data at the beginning, and the java validators are not loaded at
# that time.
# This is no problem in this case since we know data in initial_data is good.
start:
	ncs-netsim start
	ncs --with-package-reload --ignore-initial-validation

reset-setup:
	ncs-setup --reset
	rm -rf netsim
	$(MAKE) $(NETSIM_DIR)

cli:
	ncs_cli -u admin
	pkill -lf confd.conf || true

kill_ncs:
	pkill -lf ncs.conf

sync-from:
	echo request devices device \* sync-from | ncs_cli -u admin

rebuild: kill_ncs stop clean all start sync-from cli

qstop:
	pkill -lf ncs.conf || true
	pkill -lf confd.conf || true

# This rule is to cleanup the vPE devices, no support in ncs-netsim to
# delete individual devices yet
del_netsim:
	for ns in `ncs-netsim list| cut -d' ' -f 1| cut -d= -f 2| grep _vpn_CSR0`; \
	do ncs-netsim stop $$ns || true; \
	done
	sed -e "/^## device .*_vpn_CSR0/,/^#####/d" netsim/.netsiminfo > tmp.txt
	mv tmp.txt netsim/.netsiminfo
	rm -rf netsim/*_vpn_CSR

clean_cdb:
	rm -f ncs-cdb/*.cdb

restart_test: del_netsim stop start

showcase:
	./showcase.sh

showcase-rc:
	python3 showcase_rc.py
