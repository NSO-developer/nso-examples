.PHONY: all netsim start stop clean sync-from bootstrap
.PHONY: demo demo-rc demo-nc demo-jrpc

RUN_DIR = nso-run

all:
	$(MAKE) -C ../../common/packages/router-nc-1.1/src all
	for f in package-repository/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	[ -d $(RUN_DIR) ] || ncs-setup --dest $(RUN_DIR)
	cp -RPf ./package-repository/* ./$(RUN_DIR)/packages/
	cp -RPf ../../common/packages/router-nc-1.1 ./$(RUN_DIR)/packages/
	$(MAKE) netsim

netsim:
	ncs-netsim --dir $(RUN_DIR)/netsim create-network \
		./$(RUN_DIR)/packages/router-nc-1.1 2 ex
	ncs-netsim --dir $(RUN_DIR)/netsim ncs-xml-init > \
		$(RUN_DIR)/ncs-cdb/devices.xml

start:
	ncs-netsim --dir $(RUN_DIR)/netsim start
	ncs --cd ./$(RUN_DIR)

stop:
	-ncs --cd $(RUN_DIR) --stop
	-[ ! -d $(RUN_DIR)/netsim ] || ncs-netsim --dir $(RUN_DIR)/netsim stop

clean:
	for f in package-repository/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf $(RUN_DIR)

sync-from:
	printf '%s\n' 'devices sync-from' | ncs_cli -n -u admin -C

bootstrap:
	printf '%s\n' \
		'config' \
		'enterprise-dns enterprise-dns-instances branch-office device ex0 dns-server 192.0.2.53 search-domain bootstrap.example' \
		'commit' \
		| ncs_cli -n -u admin -C

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh

demo-rc:
	python3 demo_rc.py

demo-rc-nonstop:
	NONINTERACTIVE=1 python3 demo_rc.py

demo-nc:
	python3 demo_nc.py

demo-nc-nonstop:
	NONINTERACTIVE=1 python3 demo_nc.py

demo-jrpc:
	python3 demo_jrpc.py

demo-jrpc-nonstop:
	NONINTERACTIVE=1 python3 demo_jrpc.py
