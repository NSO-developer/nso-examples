all: ncs-run/.ready

ncs-run/.ready: netsim/.ready
	ncs-setup --dest ncs-run --netsim-dir ./netsim \
		--package router-nc-1.0 \
		--package ../packages/iface-v6 \
		--use-copy
	@for p in ncs-run/packages/*/src; do (set -x; $(MAKE) -C $$p) || exit 1; done
	@touch $@

netsim/.ready:
	ncs-netsim create-network router-nc-1.0 3 r
	@touch $@

reset: all
	( cd ncs-run && ncs-setup --reset )
	[ ! -e ./netsim ] || ncs-netsim reset --async

start:
	ncs-netsim start --async
	( cd ncs-run && ncs )

stop:
	ncs --stop || true
	[ ! -e ./netsim ] || ncs-netsim stop --async

configure:
	ncs_cmd -u admin -c 'maction /devices/sync-from'
	ncs_load -lmu admin -F c init.cfg

link-connect:
	ncs-netsim cmd r0 -o -c 'mset /sys/interfaces/interface{eth0}/status/link up'

link-disconnect:
	ncs-netsim cmd r0 -o -c 'mset /sys/interfaces/interface{eth0}/status/link down'

subscribe-on-change: subscribe-on-change.xml
	(cat $<; echo) | netconf-console -i -v 1.1 \
		--port $$(ncs-netsim get-port r0 netconf)

test:
	$(MAKE) stop
	$(MAKE) reset
	$(MAKE) start
	$(MAKE) configure
	$(MAKE)	link-connect ; sleep 1
	[ "$$(ncs_cmd -u admin -o -c 'get /iface{instance1}/status')" = "up" ]
	$(MAKE)	link-disconnect ; sleep 1
	[ "$$(ncs_cmd -u admin -o -c 'get /iface{instance1}/status')" = "down" ]
	$(MAKE) stop

demo:
	$(MAKE) stop
	$(MAKE) reset
	$(MAKE) start
	$(MAKE) configure
	@echo; echo; cat README.md; echo
	$(MAKE) cli

cli:
	@ncs_cli -Cu admin

clean:
	rm -rf ncs-run netsim
