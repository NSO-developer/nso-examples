######################################################################
# NSO transaction performance example
#
# See the README file for more information
######################################################################
ZBFWYANG = packages/zbfw/src/yang/zbfw.yang
CDB_DIR       := ncs-cdb
INITS         := $(wildcard *_init.xml)
CDB_INITS     := $(addprefix $(CDB_DIR)/,$(INITS))
NDEVS         := 10

$(CDB_DIR)/%.xml: %.xml
	rm -f $@ ; cp $< $@

all: parallel

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf netsim logs/* state/* ncs-cdb/* $(ZBFWYANG)

start: stop
	ncs-netsim -a start
	ncs -c ncs.conf
	ncs_cmd -c 'wait-start 2'
	ncs_cmd -u admin -c 'maction /devices/sync-from'

stop:
	-ncs --stop
	-ncs-netsim -a stop
	@echo "##### All NSO and netsim instances stopped"

parallel: $(CDB_INITS)
	cp packages/zbfw/src/yang/zbfw.yang.in packages/zbfw/src/yang/zbfw.yang
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

serial: $(CDB_INITS)
	cp packages/zbfw/src/yang/zbfw.yang.in packages/zbfw/src/yang/zbfw.yang
	sed -i.bak 's|; /\*| |' $(ZBFWYANG)
	sed -i.bak 's|\*/||' $(ZBFWYANG)
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/zbfw-router ${NDEVS} ex --dir ./netsim
	ncs-netsim ncs-xml-init > ncs-cdb/devices.xml

# Measure using default values
measure:
	python3 measure.py

# Showcase running the parallel and serial variants
showcase:
	./showcase.sh
