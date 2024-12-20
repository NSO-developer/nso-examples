NODES =  upper-nso lower-nso-1 lower-nso-2 lower-nso-3

MNAME = cisco-nso-nc-$(shell ncs --version | sed 's/\([0-9]*\.[0-9]*\).*/\1/')

ifeq ($(NCS_IPC_PATH),)
ENV = NCS_IPC_PORT=
else
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH).
endif

all: upper-nso/ncs-cdb/devs.xml
	rm -rf upper-nso/packages/rfs-ned
	rm -rf upper-nso/packages/device-action-ned
	rm -f upper-nso/packages/cisco-nso-nc-*
	for node in $(NODES); do \
	  mkdir -p $$node/state $$node/logs; \
	done
	cp -r ../../common/packages/router-nc-1.1 lower-nso-1/packages/
	cp -r ../../common/packages/router-nc-1.1 lower-nso-2/packages/
	cp -r ../../common/packages/router-nc-1.1 lower-nso-3/packages/
	for f in */packages/*/src; do \
	  $(MAKE) -C $$f all || exit 1; \
	done
	ln -s -f $(NCS_DIR)/packages/lsa/$(MNAME) upper-nso/packages
	$(MAKE) netsim
	$(MAKE) nc-rfs-ned nc-device-action-ned

upper-nso/ncs-cdb/devs.xml: upper-nso/ncs-cdb/devs.xml.in
	cat $< | sed "s/%%MNAME%%/$(MNAME)/g" > $@

netsim:
	ncs-netsim create-network lower-nso-1/packages/router-nc-1.1 6 ex --dir ./netsim

nc-rfs-ned:
	cd upper-nso/packages;                                           \
	ncs-make-package --no-netsim                                     \
	  --lsa-netconf-ned ../../lower-nso-1/packages/rfs-vlan/src/yang \
	  --lsa-lower-nso $(MNAME) --build rfs-ned

nc-device-action-ned:
	cd upper-nso/packages;                                                 \
	ncs-make-package --no-netsim                                           \
	  --lsa-netconf-ned ../../lower-nso-1/packages/device-actions/src/yang \
	  --lsa-lower-nso $(MNAME)                                             \
	  --build device-action-ned

start:
	ncs-netsim start
	cd upper-nso;   env $(ENV)4569 sname=upper-nso ncs -c ncs.conf
	cd lower-nso-1; env $(ENV)4570 sname=lower-nso-1 ncs -c ncs.conf
	cd lower-nso-2; env $(ENV)4571 sname=lower-nso-2 ncs -c ncs.conf
	cd lower-nso-3; env $(ENV)4572 sname=lower-nso-3 ncs -c ncs.conf
	./init.sh

stop:
	ncs-netsim stop
	env $(ENV)4569 ncs --stop
	env $(ENV)4570 ncs --stop
	env $(ENV)4571 ncs --stop
	env $(ENV)4572 ncs --stop

status:
	@ncs-netsim is-alive
	@env $(ENV)4569 ncs --status > /dev/null 2>&1; \
        if [ $$? = 0 ]; then echo "upper-nso: UP"; \
        else echo "upper-nso: ERR";fi
	@env $(ENV)4570 ncs --status > /dev/null 2>&1; \
        if [ $$? = 0 ]; then echo "lower-nso-1: UP"; \
        else echo "lower-nso-1: ERR";fi
	@env $(ENV)4571 ncs --status > /dev/null 2>&1; \
        if [ $$? = 0 ]; then echo "lower-nso-2: UP"; \
        else echo "lower-nso-2: ERR";fi
	@env $(ENV)4572 ncs --status > /dev/null 2>&1; \
        if [ $$? = 0 ]; then echo "lower-nso-3: UP"; \
        else echo "lower-nso-3: ERR";fi

cli-upper-nso:
	env $(ENV)4569 ncs_cli -u admin
cli-lower-nso-1:
	env $(ENV)4570 ncs_cli -u admin
cli-lower-nso-2:
	env $(ENV)4571 ncs_cli -u admin
cli-lower-nso-3:
	env $(ENV)4572 ncs_cli -u admin

cdb-clean:
	for node in $(NODES); do \
	  rm -rf $$node/state/* $$node/logs/* $$node/ncs-cdb/*.cdb; \
	done

clean:
	for node in $(NODES); do \
	  rm -rf $$node/state $$node/logs $$node/ncs-cdb/*.cdb; \
	done
	rm -rf upper-nso/packages/rfs-ned
	rm -rf upper-nso/packages/device-action-ned
	rm -f upper-nso/packages/cisco-nso-nc-*
	rm -rf lower-nso-*/packages/router-nc-1.1
	for f in */packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf db_store/*
	rm -rf ./netsim
	rm -f upper-nso/ncs-cdb/devs.xml

demo:
	./demo.sh
