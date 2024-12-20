######################################################################
# NSO northbound notifications example
#
# See the README file for more information
######################################################################
CDB_DIR       := ncs-cdb
INITS         := $(wildcard *_init.xml)
CDB_INITS     := $(addprefix $(CDB_DIR)/,$(INITS))

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

all: $(CDB_INITS)
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

$(CDB_DIR)/%.xml: %.xml
	rm -f $@ ; cp $< $@

netsim:
	ncs-netsim create-network ./packages/hw-chassis 1 c --dir ./netsim
	ncs-netsim ncs-xml-init > ncs-cdb/devices.xml

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	-rm -rf ./netsim logs/* state/* ncs-cdb/* storedstate target

start:
	ncs-netsim start
	ncs -c ncs.conf

stop:
	-ncs --stop
	-ncs-netsim stop
	@echo "##### All NSO and netsim instances stopped"

demo-rc:
	python3 demo_rc.py

demo-nc:
	python3 demo_nc.py
