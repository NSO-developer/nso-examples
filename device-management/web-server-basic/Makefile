# The order of packages is significant as there are dependencies bewteen
# the packages. Typically generated namespaces are used by other packages.
PACKAGES = lb webserver

# The create-network argument to ncs-netsim
NETWORK = create-network packages/lb 1 lb \
          create-network packages/webserver 3 www

all:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim --dir netsim $(NETWORK)
	ncs-netsim --dir netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml

start:
	ncs-netsim start
	ncs

stop:
	-ncs --stop
	-ncs-netsim stop
	@echo "##### All NSO and netsim instances stopped"

clean:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src clean || exit 1; \
	done
	rm -rf netsim logs/* state/* ncs-cdb/*.cdb
	rm -f ncs-cdb/netsim_devices_init.xml
	rm -rf state/*

demo:
	./demo.sh
