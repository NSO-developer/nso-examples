all:
	for f in packages/*/src netsim-packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	mkdir -p logs ncs-cdb state
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./netsim-packages/router-nc-1.0 3 ex --dir netsim
	mkdir -p ncs-cdb
	ncs-netsim ncs-xml-init > ncs-cdb/ncs_init.xml

demo:
	@/bin/sh demo.sh

demo-nonstop:
	@NONINTERACTIVE="all" /bin/sh demo.sh

run:
	ncs && ncs-netsim restart

start: run

stop:
	ncs --stop || true ; ncs-netsim stop || true

cli:
	@ncs_cli -n -C -u admin

clean:
	for f in packages/*/src netsim-packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf logs ncs-cdb netsim state
