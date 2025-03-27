all:
	for f in packages/*/src; do \
			$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1/ 3 ex --dir ./netsim
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim state/* ncs-cdb/*.cdb logs/*
	rm -rf ncs-cdb/netsim_devices_init.xml

stop:
	-ncs --stop
	-ncs-netsim stop

start:
	ncs-netsim start
	ncs

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh
