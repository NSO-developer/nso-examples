NODES = upper-nso lower-nso-1 lower-nso-2
MNAME = cisco-nso-nc-$(shell ncs --version | sed 's/\([0-9]*\.[0-9]*\).*/\1/')
LDEVS := 3 # Number of devices per lower node
NDEVS = $$(($(LDEVS) * 2))

ifeq ($(NCS_IPC_PATH),)
ENV = NCS_IPC_PORT=$1
else
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH).$1
endif

all: upper-nso lower-nso-1 lower-nso-2 netsim

upper-nso:
	ncs-setup --use-copy --package package-repository/cfs-t3 --no-netsim \
	          --dest $@
	$(MAKE) $@/packages/rfs-t3-ned
	for f in $@/packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	ln -s -f $(NCS_DIR)/packages/lsa/$(MNAME) upper-nso/packages
	$(MAKE) upper-nso/ncs-cdb/devs.xml
	cp nso-etc/$@/ncs.conf $@

.PHONY: upper-nso

upper-nso/packages/rfs-t3-ned:
	ncs-make-package --no-netsim --no-java --no-python --no-template --no-test \
		--vendor Tail-f --lsa-netconf-ned package-repository/rfs-t3/src/yang \
	    --lsa-lower-nso $(MNAME) --dest $@ $(@F)

upper-nso/ncs-cdb/devs.xml: nso-etc/upper-nso/ncs-cdb/devs.xml.in
	cat $< | sed "s/%%MNAME%%/$(MNAME)/g" > $@

lower-nso-%:
	ncs-setup --use-copy --package package-repository/rfs-t3 \
	          --package ../../common/packages/router-nc-1.1 --no-netsim --dest $@
	sed -i.bak 's|container sys {|container sys { leaf trans-delay { type uint32; default 0; }|' $@/packages/router-nc-1.1/src/yang/router.yang
	rm -rf $@/packages/router-nc-1.1/netsim
	cp -r package-repository/router-netsim $@/packages/router-nc-1.1/netsim
	for f in $@/packages/*/src; do \
		$(MAKE) -C $$f clean all || exit 1; \
	done
	cp nso-etc/$@/ncs.conf $@

netsim:
	ncs-netsim create-network lower-nso-1/packages/router-nc-1.1 $(NDEVS) ex \
	           --dir ./netsim
	for (( i=0; i<$(NDEVS); ++i )); do \
		if [ $$i -lt $(LDEVS) ]; then \
			ncs-netsim ncs-xml-init ex$$i > lower-nso-1/ncs-cdb/ex$$i.xml; \
		else \
			ncs-netsim ncs-xml-init ex$$i > lower-nso-2/ncs-cdb/ex$$i.xml; \
		fi; \
	done

start:
	ncs-netsim start
	cd upper-nso;   env $(call ENV,4569) sname=upper-nso   ncs -c ncs.conf
	cd lower-nso-1; env $(call ENV,4570) sname=lower-nso-1 ncs -c ncs.conf
	cd lower-nso-2; env $(call ENV,4571) sname=lower-nso-2 ncs -c ncs.conf
	for (( i=4571; i>=4569; --i )); do \
		env $(call ENV,$$i) ncs_cmd -c 'wait-start 2'; \
		if [ $$i -eq 4569 ]; then \
			env $(call ENV,$$i) ncs_cmd -u admin -c 'maction /devices/fetch-ssh-host-keys';\
		fi; \
		env $(call ENV,$$i) ncs_cmd -u admin -c 'maction /devices/sync-from'; \
	done

stop:
	-ncs-netsim stop
	-env $(call ENV,4569) ncs --stop
	-env $(call ENV,4570) ncs --stop
	-env $(call ENV,4571) ncs --stop
	@echo "##### All NSO and netsim instances stopped"

status:
	@ncs-netsim is-alive
	@env $(call ENV,4569) ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then                              \
	    echo "upper-nso: UP";                         \
	    else echo "upper-nso: ERR";                   \
	fi
	@env $(call ENV,4570) ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then                              \
	    echo "lower-nso-1: UP";                       \
	else                                              \
	    echo "lower-nso-1: ERR";                      \
	fi
	@env $(call ENV,4571) ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then                              \
	    echo "lower-nso-2: UP";                       \
	else                                              \
	    echo "lower-nso-2: ERR";                      \
	fi

cli-upper-nso:
	env $(call ENV,4569) ncs_cli -u admin
cli-lower-nso-1:
	env $(call ENV,4570) ncs_cli -u admin
cli-lower-nso-2:
	env $(call ENV,4571) ncs_cli -u admin

clean:
	rm -rf upper-nso lower-nso-1 lower-nso-2
	rm -rf netsim

showcase:
	./showcase.sh

showcase-rc:
ifdef NONINTERACTIVE
	python3 ./showcase_rc.py -i
else
	python3 ./showcase_rc.py
endif
