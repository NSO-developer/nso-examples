######################################################################
# NSO stacked services transaction performance example
#
# See the README file for more information
######################################################################
CDB_DIR       := nso-rundir/ncs-cdb
INITS         := $(wildcard *_init.xml)
CDB_INITS     := $(addprefix $(CDB_DIR)/,$(INITS))
NDEVS         := 10

all: setup $(CDB_INITS)

$(CDB_DIR)/%.xml: %.xml
	rm -f $@ ; cp $< $@

setup:
	ncs-setup --dest nso-rundir
	cp -r package-repository/*-t3 nso-rundir/packages/
	cp -r ../../common/packages/router-nc-1.1 nso-rundir/packages/
	sed -i.bak 's|container sys {|container sys { leaf trans-delay { type uint32; default 0; }|' nso-rundir/packages/router-nc-1.1/src/yang/router.yang
	rm -rf nso-rundir/packages/router-nc-1.1/netsim
	cp -r package-repository/router-netsim nso-rundir/packages/router-nc-1.1/netsim
	for f in nso-rundir/packages/*/src; do \
		$(MAKE) -C $$f clean all || exit 1; \
	done
	ncs-netsim create-network nso-rundir/packages/router-nc-1.1 \
	           $(NDEVS) ex --dir nso-rundir/netsim
	ncs-netsim --dir nso-rundir/netsim ncs-xml-init \
	           > nso-rundir/ncs-cdb/devices.xml

clean:
	rm -rf nso-rundir

start: stop
	cd nso-rundir; \
	ncs-netsim -a start; \
	ncs -c ncs.conf
	ncs_cmd -c 'wait-start 2'
	ncs_cmd -u admin -c 'maction /devices/sync-from'

stop:
	-ncs --stop
	-ncs-netsim --dir nso-rundir/netsim -a stop
	@echo "##### All NSO and netsim instances stopped"

# Showcase using the CLI
showcase:
	./showcase.sh

# Showcase using RESTCONF
showcase-rc:
ifdef NONINTERACTIVE
	python3 ./showcase_rc.py -i
else
	python3 ./showcase_rc.py
endif
