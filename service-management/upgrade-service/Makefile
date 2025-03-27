all:
	cp -r ../../common/packages/router-nc-1.1 ./packages/
	$(MAKE) -C packages/router-nc-1.1/src all
	$(MAKE) -C package-store/vlan/src all
	$(MAKE) -C package-store/vlan_v2/src all
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 3 ex --dir ./netsim

vlan:
	$(MAKE) -C packages/vlan/src all

tunnel:
	$(MAKE) -C packages/tunnel/src all

clean:
	for f in package-store/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./packages/*;
	rm -rf ./netsim logs/* state/* ncs-cdb/*.cdb ncs-cdb-bak

demo:
	./demo.sh
