all:
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 3 ex --dir ./netsim
	ncs-netsim --dir ./netsim ncs-xml-init > ./ncs-cdb/devices.xml

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim logs/* state/* ncs-cdb/*

start:
	ncs-netsim start
	ncs

stop:
	-ncs --stop
	-ncs-netsim stop

demo:
	./demo.sh
