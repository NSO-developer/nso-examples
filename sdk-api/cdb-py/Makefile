all:
	$(MAKE) -C packages/router-nc-1.1/src all
	$(MAKE) -C packages/cdb/src all
	$(MAKE) netsim
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1/ 3 ex --dir ./netsim

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim logs state/* ncs-cdb/*.cdb logs ncs.conf
	rm -rf  ncs-cdb/netsim_devices_init.xml ./netsim

stop:
	-ncs --stop
	ncs-netsim stop

start:
	mkdir -p logs
	ncs-netsim start
	ncs

update_py:
	cp packages/cdb/python/plaincdbsub/plaincdbsub.py state/packages-in-use/1/cdb/python/plaincdbsub/
	make -C state/packages-in-use/1/cdb/python

demo:
	./demo.sh
