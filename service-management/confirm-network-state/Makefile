all: netsim ncs-run

.PHONY: packages
packages:
	for p in packages/*/src; do $(MAKE) -C $$p || exit 1; done

ncs-run: packages
	ncs-setup --dest ncs-run --netsim-dir ./netsim \
		--package cisco-ios-netsim-cli-1.0 \
		--package packages/router-nc-1.0 \
		--package packages/iface-v3 \
		--use-copy
	ncs_conf_tool -a \
		'<out-of-band-policy-log-level>trace</out-of-band-policy-log-level>' \
		ncs-config logs 1<>ncs-run/ncs.conf <ncs-run/ncs.conf

netsim: packages
	ncs-netsim create-network cisco-ios-netsim-cli-1.0 3 c
	ncs-netsim add-to-network packages/router-nc-1.0 3 r

start:
	ncs-netsim start --async
	(cd ncs-run; ncs)
	ncs_cmd -u admin -c 'maction /devices/sync-from'
	ncs_load -lmu admin -F c devices.cfg
	ncs_load -lmu admin -F c init.cfg
	unset NCS_IPC_PATH; NCS_IPC_PORT=5011 ncs_load -lm -F c oob-c1.cfg
	unset NCS_IPC_PATH; NCS_IPC_PORT=5015 ncs_load -lm -F c oob-r2.cfg

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
	ncs_cmd -u admin -c 'maction /devices/device{c1}/compare-config'
	ncs_cmd -u admin -c 'maction /devices/device{c1}/sync-from'
	$(MAKE) stop clean

clean:
	rm -rf ncs-run netsim
