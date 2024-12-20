all:
	$(MAKE) -C packages
	$(MAKE) netsim
	$(MAKE) ncs-run

ncs-run:
	ncs-setup --dest ncs-run --netsim-dir ./netsim \
		--package cisco-ios-cli-3.0 \
		--package packages/iface-v3-discovery \
		--use-copy

netsim:
	ncs-netsim create-network cisco-ios-cli-3.0 3 c

start:
	ncs-netsim start --async
	(cd ncs-run; ncs)
	ncs_cmd -u admin -c 'maction /devices/sync-from'
	ncs_load -lmu admin init.xml

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
	python3 import.py
	ncs_cmd -u admin -c 'maction /iface{instance1}/re-deploy'
	ncs_cmd -u admin -c 'maction /iface{instance2}/re-deploy'

clean:
	rm -rf ncs-run netsim
