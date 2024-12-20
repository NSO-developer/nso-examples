
all:	env-check
	$(MAKE) packs
	$(MAKE) dev_all
	echo "Build complete"

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

PACKAGE_DIRS = common xml-rpc

REMAKE:	stop clean all start status

quick-remake:
	if [ -d ./packages/xml-rpc/src/java/build ]; then \
		$(MAKE) stop reset start status; \
	else \
		$(MAKE) REMAKE; \
	fi

reset:
	rm -f rollback* *.log ncserr* 2>/dev/null || true
	rm -f *.trace ncs-cdb/*.cdb 2>/dev/null || true
	find  devices -name '*.ser' | xargs rm  2>/dev/null || true
	find  devices -name 'log4j.log' | xargs rm  2>/dev/null || true

status:
	@$(NCS) --status  2>&1 >/dev/null && echo "NCS OK"



dev_all:
	cd devices; $(MAKE) all


start:	env-check
	$(MAKE) dev_start
	$(MAKE) ncs_start

dev_start:
	cd devices; $(MAKE) start-devices

ncs_start:
	$(NCS) $(NCS_FLAGS)

packs:
	for i in $(PACKAGE_DIRS); do \
		$(MAKE) -C ./packages/$${i}/src all || exit 1; \
	done


stop:	env-check
	$(NCS) --stop || true
	$(MAKE) stop-x1
	$(MAKE) stop-x2
	$(MAKE) stop-x3
	@echo "##### All NSO and netsim instances stopped"

clean:	env-check
	$(MAKE) -k ncs_clean
	cd devices && $(MAKE) clean || true
	$(MAKE) -k clean_packs
	find  devices -name '*.ser' | xargs rm -f
	find  devices -name 'log4j.log' | xargs rm -f



ncs_clean:	iclean
	-rm -rf *.trace


cli:	env-check
	$(NCS_DIR)/bin/ncs_cli -u admin

clean_packs:
	for i in $(PACKAGE_DIRS); do \
		$(MAKE) -C ./packages/$${i}/src clean || exit 1; \
	done

start-x1:
	cd devices; $(MAKE) start-x1

## Hardcoded port for device x1
stop-x1:
	cd devices; $(MAKE) stop-x1


start-x2:
	cd devices; $(MAKE) start-x2

## Hardcoded port for device x2
stop-x2:
	cd devices; $(MAKE) stop-x2


start-x3:
	cd devices; $(MAKE) start-x3
## Hardcoded port for device x3
stop-x3:
	cd devices; $(MAKE) stop-x3

demo:
	./demo.sh
