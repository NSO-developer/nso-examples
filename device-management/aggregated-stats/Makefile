all:
	$(MAKE) -C packages/router-nc-1.1/src all
	$(MAKE) -C packages/stats/src all
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 3 ex --dir ./netsim

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim logs/* state/* ncs-cdb/*.cdb

demo:
	./demo.sh
