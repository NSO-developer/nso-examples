# The order of packages is significant as there are dependencies between
# the packages. Typically generated namespaces are used by other packages.
PACKAGES =  webserver lb manual-ha

# The create-network argument to ncs-netsim
NETWORK = create-network packages/lb 1 lb \
          create-network packages/webserver 3 www

all:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src all || exit 1; \
	done
	$(MAKE) netsim
	mkdir -p n1/ncs-cdb n1/logs
	mkdir -p n2/ncs-cdb n2/logs
	ln -sf ../packages n1/
	ln -sf ../packages n2/
	cp  ncs-cdb/*_init.xml n1/ncs-cdb
	cp  ncs-cdb/*_init.xml n2/ncs-cdb

netsim:
	ncs-netsim --dir netsim $(NETWORK)
	ncs-netsim --dir netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml

src-clean:
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src clean || exit 1; \
	done

state-clean:
	rm -rf netsim
	for n in n1 n2; do \
	    rm -rf $$n/logs $$n/packages $$n/state/* $$n/ncs-cdb; \
	done
	rm -f ncs-cdb/netsim_devices_init.xml

clean:	src-clean state-clean

demo:
	./demo.sh
