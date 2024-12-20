all:
	for f in packages/*/src; do \
	  $(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 3 ex --dir ./netsim

clean:
	for f in packages/*/src; do \
	  $(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim logs/* state/* ncs-cdb/*.cdb

start:
	ncs-netsim start
	ncs

stop:
	ncs-netsim stop
	ncs --stop

status:
	ncs-netsim is-alive
	@ncs --status > /dev/null 2>&1; \
        if [ $$? = 0 ]; then echo "ncs: UP"; \
        else echo "ncs: ERR";fi

cli-ncs:
	ncs_cli -u admin

demo:
	./demo.sh
