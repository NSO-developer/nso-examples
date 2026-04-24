RUN_DIR = nso-run

all:
	$(MAKE) -C ../../common/packages/router-nc-1.1/src all
	for f in package-repository/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	[ -d $(RUN_DIR) ] || ncs-setup --dest $(RUN_DIR)
	cp -RPf ./package-repository/* ./$(RUN_DIR)/packages/
	cp -RPf ../../common/packages/router-nc-1.1 ./$(RUN_DIR)/packages/
	cp -f ./ncs-cdb/ncs_init.xml ./$(RUN_DIR)/ncs-cdb/ncs_init.xml
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./$(RUN_DIR)/packages/router-nc-1.1 \
		1 ex --dir ./$(RUN_DIR)/netsim

start:
	ncs --cd ./$(RUN_DIR)
	ncs-netsim start --dir ./$(RUN_DIR)/netsim
	echo "devices sync-from" | ncs_cli -n -C -u admin

stop:
	-[ ! -d $(RUN_DIR) ] || ncs --cd $(RUN_DIR) --stop
	-[ ! -d $(RUN_DIR)/netsim ] || ncs-netsim --dir $(RUN_DIR)/netsim stop

clean:
	for f in package-repository/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf $(RUN_DIR)

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh
