all:
	$(MAKE) -C packages/router-nc-1.1/src all
	$(MAKE) -C packages/myserv/src all
	$(MAKE) netsim
	ncs-setup --netsim-dir ./netsim --dest .
	cp logging.xml ncs-cdb

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 3 ex --dir ./netsim

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim logs/* state/* ncs-cdb/* scripts
	rm -f ncs-cdb/logging.xml  README.n* ncs.conf

start:
	ncs-netsim start
	ncs

stop:
	-ncs-netsim stop
	-ncs --stop

demo:
	./demo.sh
