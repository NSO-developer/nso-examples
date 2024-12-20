all:
	$(MAKE) -C packages/router-nc-1.0/src all
	$(MAKE) -C packages/cdb/src all
	$(MAKE) netsim
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml

netsim:
	ncs-netsim create-network ./packages/router-nc-1.0 3 ex --dir ./netsim

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim logs/* state/* ncs-cdb/*.cdb

start: stop
	ncs-netsim start
	ncs

stop:
	-ncs --stop
	-ncs-netsim -a stop
	@echo "##### All NSO and netsim instances stopped"

demo:
	./demo.sh
