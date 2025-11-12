all:
	$(MAKE) -C $(NCS_DIR)/examples.ncs/common/packages/cisco-ios-netsim-cli-1.0/src all
	$(MAKE) -C packages
	$(MAKE) netsim
	$(MAKE) ncs-run

ncs-run:
	ncs-setup --dest ncs-run --netsim-dir ./netsim \
		--package cisco-ios-netsim-cli-1.0 \
		--package packages/iface-postmod-py \
		--use-copy

netsim:
	ncs-netsim create-network cisco-ios-netsim-cli-1.0 3 c

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
	ncs_cmd -u admin -c 'mdel /iface{instance1}'
	ncs_cmd -u admin -c 'mget /devices/device{c1}/config/interface/GigabitEthernet{0/1}/switchport/access/vlan'

clean:
	rm -rf ncs-run netsim
