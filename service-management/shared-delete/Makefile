all:
	$(MAKE) -C packages
	$(MAKE) netsim
	$(MAKE) ncs-run

ncs-run:
	ncs-setup --dest ncs-run --netsim-dir ./netsim \
		--package cisco-ios-cli-3.0 \
		--package packages/bgp-routing \
		--package packages/bgp-clean \
		--use-copy

netsim:
	ncs-netsim create-network cisco-ios-cli-3.0 3 c

start:
	ncs-netsim start --async
	(cd ncs-run; ncs)
	ncs_cmd -u admin -c 'maction /devices/sync-from'
	ncs_load -lmu admin -F c init.cfg

stop:
	ncs --stop || true
	[ ! -d ./netsim ] || ncs-netsim stop --async

cli:
	@ncs_cli -Cu admin

demo:
	$(MAKE) stop clean all start
	@echo; echo; cat README.md; echo
	@$(MAKE) cli

test:
	$(MAKE) stop clean all start

clean:
	rm -rf ncs-run netsim
