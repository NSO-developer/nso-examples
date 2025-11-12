SN_PACKAGES = l3vpn-srv l3vpn-ned
DN_PACKAGES = l3vpn

# Directory of example packages
PACKAGE_STORE=$(NCS_DIR)/examples.ncs/common/packages

NEDS = \
	cisco-ios-netsim-cli-1.0    \
	cisco-iosxr-netsim-cli-1.0  \
	juniper-junos-netsim-nc-1.0 \
	alu-sr-netsim-cli-1.0

MNAME = cisco-nso-nc-$(shell ncs --version | sed 's/\([0-9]*\.[0-9]*\).*/\1/')

all: neds app-all

neds:
	for i in $(NEDS); do                                     \
	    ln -sf $(PACKAGE_STORE)/$${i} device-nodes/packages; \
	done
	for node in device-nodes/nso-*; do                       \
	    ln -sf ../packages $${node};                         \
	done

app-all: service-node/init-data/nsos.xml
	for i in $(DN_PACKAGES); do \
	    $(MAKE) -C device-nodes/packages/$${i}/src all || exit 1; \
	done
	cd service-node/packages; \
	ln -s -f $(NCS_DIR)/packages/lsa/$(MNAME) .;                   \
	ncs-make-package --no-netsim                                   \
	  --lsa-netconf-ned ../../device-nodes/packages/l3vpn/src/yang \
	  --lsa-lower-nso $(MNAME) l3vpn-ned
	for p in $(SN_PACKAGES); do\
		$(MAKE) -C service-node/packages/$$p/src all; \
	done
	./mk-init.sh

service-node/init-data/nsos.xml: service-node/init-data/nsos.xml.in
	cat $< | sed "s/%%MNAME%%/$(MNAME)/g" > $@

DIRS=device-nodes/nso-1 \
     device-nodes/nso-2 \
     device-nodes/nso-3 \
     service-node

clean: app-clean
	for i in $(DN_PACKAGES); do \
		$(MAKE) -C device-nodes/packages/$${i}/src clean || exit 1; \
	done
	for i in $(DIRS); do                                 \
		rm -rf $${i}/ncs-cdb $${i}/state $${i}/logs; \
	done
	rm -rf netsim
	rm -rf service-node/packages/l3vpn-ned
	rm -f device-nodes/nso-*/packages
	for i in $(NEDS); do                                 \
	    rm -f device-nodes/packages/$${i};               \
	done
	rm -f service-node/init-data/nsos.xml

app-clean:
	for i in $(DIRS); do \
		$(MAKE) -C $${i} clean || exit 1; \
	done
	for i in $(DN_PACKAGES); do \
		$(MAKE) -C device-nodes/packages/$${i}/src clean || exit 1; \
	done
	for p in $(SN_PACKAGES); do                                     \
		if [ -d service-node/packages/$$p/src ]; then           \
			$(MAKE) -C service-node/packages/$$p/src clean; \
		fi;                                                     \
	done
	rm -f service-node/packages/cisco-nso-nc-*

start stop:
	ncs-netsim --async $@
	$(MAKE) app-$@

app-start:
	for i in $(DIRS); do \
		$(MAKE) -C $${i} $@ || exit 1; \
	done

app-stop:
	for i in $(DIRS); do \
		$(MAKE) -C $${i} $@ || true; \
	done

reset:
	ncs-netsim $@
	@for i in $(DIRS); do \
		$(MAKE) -C $${i}  $@ || true; \
	done

status:
	@for i in $(DIRS); do \
		$(MAKE) -C $${i}  $@ || exit 1; \
	done

cli:
	cd service-node; $(MAKE) cli
cli-nso-1:
	cd device-nodes/nso-1; $(MAKE) cli
cli-nso-2:
	cd device-nodes/nso-2; $(MAKE) cli
cli-nso-3:
	cd device-nodes/nso-3; $(MAKE) cli

demo:
	./demo.sh
