usage:
	@echo "See README file for more instructions"
	@echo "make all           Build all example files"
	@echo "make clean         Remove all built and intermediary files"
	@echo "make start         Start NCS and devices"
	@echo "make stop          Stop NCS and devices"
	@echo "make REMAKE        Full rebuild and start of NCS and devices"
	@echo "make quick-remake  Reset NCS and devices to their init config"
	@echo "make cli           Start the NCS Command Line Interface"
	@echo "make status        Check status of NCS and devices"

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

REMAKE:	stop clean all start status

PACKAGE_DIRS = snmp-notif-recv

quick-remake:
	if [ -d ./packages/snmp-notif-recv/src/java/build]; then \
		$(MAKE) stop reset start status; \
	else \
		$(MAKE) REMAKE; \
	fi

reset:
	rm -f rollback* *.log ncserr* 2>/dev/null || true
	rm -f ncs-cdb/*.cdb 2>/dev/null || true


all:	env-check
	$(MAKE) ncs_all
	echo "Build complete"

ncs_all:
	$(MAKE) packs
	true;

start:	env-check
	$(MAKE) ncs_start

ncs_start:
	$(NCS) $(NCS_FLAGS)


stop:	env-check
	$(MAKE) ncs_stop
ncs_stop:
	$(NCS) --stop || true
	@echo "##### NSO stopped"

clean:	env-check
	$(MAKE) ncs_clean

ncs_clean:	iclean clean_packs
	-rm -rf *.trace

status:	env-check
	@$(MAKE) ncs_status

ncs_status:
	@$(NCS) --status  2>&1 >/dev/null && echo "NCS OK"

cli:	env-check
	$(NCS_DIR)/bin/ncs_cli --user=admin

ssh-cli: env-check
	ssh -l admin localhost -p 2024


packs:
	for i in $(PACKAGE_DIRS); do \
                (cd ./packages/$${i}/src; $(MAKE) all) || exit 1; \
        done

clean_packs:
	for i in $(PACKAGE_DIRS); do \
                (cd ./packages/$${i}/src; $(MAKE) clean) || exit 1; \
        done

demo:
	./demo.sh
