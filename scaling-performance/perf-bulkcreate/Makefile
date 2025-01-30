######################################################################
# NSO MAAPI set_values(XML) performance example
#
# See the README file for more information
######################################################################
CDB_DIR       := nso-rundir/ncs-cdb
INITS         := $(wildcard *_init.xml)
CDB_INITS     := $(addprefix $(CDB_DIR)/,$(INITS))
NDEVS         := 1
TEST  	  	  := "py_setvals"

all: setup $(CDB_INITS)

$(CDB_DIR)/%.xml: %.xml
	rm -f $@ ; cp $< $@

setup:
	ncs-setup --dest nso-rundir
	cat nso-rundir/ncs.conf | ncs_conf_tool -e false ncs-config logs xpath-trace-log enabled > nso-rundir/ncs.conf.new
	mv nso-rundir/ncs.conf.new nso-rundir/ncs.conf
	cp -r ${NCS_DIR}/packages/neds/cisco-asa-cli-6.6 nso-rundir/packages/
	if [ ${TEST} == "j_setvals_xml" ]; then \
		cp -r package-repository/rfs-acl-java-setvals nso-rundir/packages/rfs-acl; \
	elif [ ${TEST} == "py_setvals_xml" ]; then \
		cp -r package-repository/rfs-acl-py-setvals nso-rundir/packages/rfs-acl; \
	elif [ ${TEST} == "py_create" ]; then \
		cp -r package-repository/rfs-acl-py-create nso-rundir/packages/rfs-acl; \
	elif [ ${TEST} == "py_mload_xml" ]; then \
		cp -r package-repository/rfs-acl-py-mload nso-rundir/packages/rfs-acl; \
	elif [ ${TEST} == "java_mload_xml" ]; then \
		cp -r package-repository/rfs-acl-java-mload nso-rundir/packages/rfs-acl; \
	elif [ ${TEST} == "py_template" ]; then \
		cp -r package-repository/rfs-acl-py-tmpl nso-rundir/packages/rfs-acl; \
	fi
	for f in nso-rundir/packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	ncs-netsim create-network nso-rundir/packages/cisco-asa-cli-6.6 \
	           $(NDEVS) asa --dir nso-rundir/netsim
	ncs-netsim --dir nso-rundir/netsim ncs-xml-init \
	           > nso-rundir/ncs-cdb/devices.xml

clean:
	rm -rf nso-rundir

start: stop
	cd nso-rundir; \
	ncs-netsim --dir netsim -a start; \
	echo "##### Netsim instances started"; \
	ncs -c ncs.conf
	@echo "##### NSO starting..."
	ncs_cmd -c 'wait-start 2'
	@echo "##### NSO started"
	ncs_cmd -u admin -c 'maction /devices/sync-from'
	@echo "##### Sync from netsim devices done"
.PHONY: clean

stop:
	-ncs --stop
	-ncs-netsim --dir nso-rundir/netsim -a stop
	@echo "##### All NSO and netsim instances stopped"
.PHONY: stop

measure:
	./measure.sh
.PHONY: measure

showcase:
	./showcase.sh
.PHONY: showcase
