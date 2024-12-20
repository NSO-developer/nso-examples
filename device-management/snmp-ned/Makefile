all:
	$(MAKE) -C packages/ex-snmp-ned/src all
	$(MAKE) netsim
.PHONY: all

netsim:
	ncs-netsim --dir netsim create-network packages/ex-snmp-ned 3 r

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $${f} clean || exit 1; \
	done
	rm -f logs/* ncs-cdb/*.cdb
	rm -rf netsim state/*
.PHONY: clean

### Shortcuts

start:
	ncs-netsim start
	ncs
.PHONY: start

stop:
	-ncs --stop
	-ncs-netsim stop
	@echo "##### All NSO and netsim instances stopped"
.PHONY: stop

REMAKE:	stop clean all start

.PHONY: REMAKE

# This make target is faster than REMAKE, it will not call clean
quick-remake:
	if [ -d ./netsim ]; then \
		$(MAKE) stop reset all start; \
	else \
		$(MAKE) REMAKE; \
	fi
.PHONY: quick-remake

reset:
	ncs-netsim reset
	rm -rf logs/* state/* ncs-cdb/*.cdb
.PHONY: reset

demo:
	./demo.sh
