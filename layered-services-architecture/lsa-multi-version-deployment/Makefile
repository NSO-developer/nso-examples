NODES =  upper-nso lower-nso-1 lower-nso-2

NCSVER= $(shell ncs --version | sed 's/\([0-9]*\.[0-9]*\).*/\1/')
MNAME = cisco-nso-nc-$(NCSVER)

ifeq ($(NCS_IPC_PATH),)
ENV = NCS_IPC_PORT=
else
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH).
endif

all:    upper-nso-all    lower-nso-1 lower-nso-2 netsim

manual: upper-nso-manual lower-nso-1 lower-nso-2 netsim

upper-nso-all: upper-nso
.PHONY: upper-nso-all

upper-nso-manual: package-store/cfs-vlan
	rm -rf upper-nso
	ncs-setup --no-netsim --dest upper-nso
	cp nso-etc/upper-nso/ncs.conf upper-nso
.PHONY: upper-nso-manual

upper-nso: package-store/cfs-vlan
	ncs-setup --no-netsim --dest $@
	cp nso-etc/$@/ncs.conf $@
	ln -sf $(NCS_DIR)/packages/lsa/$(MNAME) $@/packages
	ln -sf ../../package-store/cfs-vlan     $@/packages
	$(MAKE) $@/packages/rfs-vlan-nc-$(NCSVER)

package-store/cfs-vlan:
	$(MAKE) -C $@/src
.PHONY: package-store/cfs-vlan

upper-nso/packages/rfs-vlan-nc-%:
	ncs-make-package --no-netsim --no-java --no-python           \
	    --lsa-netconf-ned package-store/rfs-vlan/src/yang        \
	    --lsa-lower-nso cisco-nso-nc-$*                          \
	    --package-version $* --dest $@ --build $(@F)

lower-nso-%: package-store/rfs-vlan
	ncs-setup --no-netsim --dest $@
	cp nso-etc/$@/ncs-cdb/devs.xml      $@/ncs-cdb
	cp nso-etc/$@/ncs.conf              $@
	ln -sf ../../package-store/rfs-vlan $@/packages
	cp -r ../../common/packages/router-nc-1.1 $@/packages/
	$(MAKE) -C $@/packages/router-nc-1.1/src

package-store/rfs-vlan:
	$(MAKE) -C $@/src
.PHONY: package-store/rfs-vlan

netsim:
	ncs-netsim create-network lower-nso-1/packages/router-nc-1.1 6 ex --dir ./netsim

start:
	$(MAKE) start-all

start-%:
	ncs-netsim --async start
	cd upper-nso;   $(ENV)4569 sname=upper-nso   ncs -c ncs.conf
	cd lower-nso-1; $(ENV)4570 sname=lower-nso-1 ncs -c ncs.conf
	cd lower-nso-2; $(ENV)4571 sname=lower-nso-2 ncs -c ncs.conf
	env $(ENV)4569 ncs --wait-started 20
	env $(ENV)4570 ncs --wait-started 20
	env $(ENV)4571 ncs --wait-started 20
	MNAME=$(MNAME) ./init-$*.sh

stop:
	ncs-netsim --async stop
	$(ENV)4569 ncs --stop
	$(ENV)4570 ncs --stop
	$(ENV)4571 ncs --stop
	@echo "##### All NSO and netsim instances stopped"

start-upper-nso-i:
	cd upper-nso;   $(ENV)4569 sname=upper-nso ncs -i -c ncs.conf

reload-upper-nso:
	$(ENV)4569 ncs --reload

stop-upper-nso:
	$(ENV)4569 ncs --stop

status:
	@ncs-netsim is-alive
	@$(ENV)4569 ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then                              \
	    echo "upper-nso: UP";                         \
	    else echo "upper-nso: ERR";                   \
	fi
	@$(ENV)4570 ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then                              \
	    echo "lower-nso-1: UP";                       \
	else                                              \
	    echo "lower-nso-1: ERR";                      \
	fi
	@$(ENV)4571 ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then                              \
	    echo "lower-nso-2: UP";                       \
	else                                              \
	    echo "lower-nso-2: ERR";                      \
	fi

cli-upper-nso:
	$(ENV)4569 ncs_cli -u admin
cli-lower-nso-1:
	$(ENV)4570 ncs_cli -u admin
cli-lower-nso-2:
	$(ENV)4571 ncs_cli -u admin

clean:
	make -C package-store/cfs-vlan/src clean
	make -C package-store/rfs-vlan/src clean
	rm -rf upper-nso lower-nso-1 lower-nso-2
	rm -rf netsim

demo:
	./demo.sh
