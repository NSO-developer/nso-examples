all:
	$(MAKE) -C ../packages
	$(MAKE) netsim
	$(MAKE) ncs-run

ncs-run:
	ncs-setup --dest ncs-run --netsim-dir ./netsim \
		--package cisco-ios-cli-3.0 \
		--package ../packages/router-nc-1.0 \
		--package ../packages/dns-v4 \
		--use-copy

netsim:
	ncs-netsim create-network cisco-ios-cli-3.0 3 c
	ncs-netsim add-to-network ../packages/router-nc-1.0 3 r

start:
	ncs-netsim start --async
	(cd ncs-run; ncs)
	ncs_cmd -u admin -c 'maction /devices/sync-from'

stop:
	ncs --stop || true
	[ ! -d ./netsim ] || ncs-netsim stop --async

cli:
	@ncs_cli -Cu admin

demo:
	$(MAKE) stop clean all start
	@echo; echo; cat README; echo
	@$(MAKE) cli

test:
	$(MAKE) stop clean all start
	ncs_load -lmu admin -F c example.cfg

clean:
	rm -rf ncs-run netsim
