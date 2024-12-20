all: on-demand

demo:
	@$(MAKE) stop clean
	@$(MAKE) in-memory
	@$(MAKE) start
	@echo '###'
	@echo '### Starting migration to on-demand-v1 mode'
	@echo '###'
	@echo ''
	@sleep 4
	@$(MAKE) stop
	@echo ''
	@$(MAKE) on-demand
	@$(MAKE) start

in-memory: ncs-run/.ready cdb.conf
	@cat cdb.conf | ncs_conf_tool -e in-memory-v1 cdb persistence format \
		> ncs-run/ncs.conf.d/cdb.conf
	@echo '###'
	@echo '### Using CDB in-memory persistence:'
	@echo '###'
	@cat ncs-run/ncs.conf.d/cdb.conf
	@echo ''

on-demand: ncs-run/.ready cdb.conf
	@cat cdb.conf | ncs_conf_tool -e on-demand-v1 cdb persistence format \
		> ncs-run/ncs.conf.d/cdb.conf
	@echo '###'
	@echo '### Using CDB on-demand persistence:'
	@echo '###'
	@cat ncs-run/ncs.conf.d/cdb.conf
	@echo ''

ncs-run/.ready:
	ncs-setup --dest ncs-run
	@ncs-setup --dest ncs-run --reset
	@mv ncs-run/ncs.conf ncs-run/ncs.conf~
	@# Remove cdb section from ncs.conf
	@cat ncs-run/ncs.conf~ \
		| ncs_conf_tool -d ncs-config cdb \
		> ncs-run/ncs.conf
	@mkdir -p ncs-run/ncs.conf.d
	@cp cdb.conf ncs-run/ncs.conf.d/
	@echo ''
	@touch $@

start:
	cd ncs-run && ncs
	@printf '\nadmin@ncs# show '
	@echo 'show ncs-state internal cdb datastore running' \
		| ncs_cli -sCu admin
	@echo ''

stop:
	ncs --stop || true

test: demo

cli:
	ncs_cli -Cu admin

clean:
	rm -rf ncs-run