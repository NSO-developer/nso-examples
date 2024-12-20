######################################################################
# NSO transaction performance example
#
# See the README file for more information
######################################################################
T3YANG = packages/t3-service/src/yang/t3.yang
CDB_DIR       := ncs-cdb
INITS         := $(wildcard *_init.xml)
CDB_INITS     := $(addprefix $(CDB_DIR)/,$(INITS))
NDEVS         := 10

$(CDB_DIR)/%.xml: %.xml
	rm -f $@ ; cp $< $@

all: python-pkg parallel netsim $(CDB_INITS)

clean:
	rm -rf packages/router-nc-1.1
	rm -rf netsim packages/t3-service logs/* state/* ncs-cdb/*

start: stop
	ncs-netsim -a start
	ncs -c ncs.conf
	ncs_cmd -c 'wait-start 2'
	ncs_cmd -u admin -c 'maction /devices/sync-from'

stop:
	-ncs --stop
	-ncs-netsim -a stop
	@echo "##### All NSO and netsim instances stopped"

python-pkg:
	cp -r package-repository/t3-service-python packages/t3-service

java-pkg:
	cp -r package-repository/t3-service-java packages/t3-service

parallel:
	cp -r ../../common/packages/router-nc-1.1 ./packages/
	sed -i.bak 's|container sys {|container sys { leaf trans-delay { type uint32; default 0; }|' ./packages/router-nc-1.1/src/yang/router.yang
	rm -rf ./packages/router-nc-1.1/netsim
	cp -r ./package-repository/router-netsim ./packages/router-nc-1.1/netsim
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done

serial:
	sed -i.bak 's|; /\*| |' $(T3YANG)
	sed -i.bak 's|\*/||' $(T3YANG)
	cp -r ../../common/packages/router-nc-1.1 ./packages/
	sed -i.bak 's|container sys {|container sys { leaf trans-delay { type uint32; default 0; }|' ./packages/router-nc-1.1/src/yang/router.yang
	rm -rf ./packages/router-nc-1.1/netsim
	cp -r ./package-repository/router-netsim ./packages/router-nc-1.1/netsim
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 $(NDEVS) ex --dir ./netsim
	ncs-netsim ncs-xml-init > ncs-cdb/devices.xml

enable-cq:
	ncs_cmd -u admin -c 'mset /devices/global-settings/commit-queue/enabled-by-default true; mcreate /devices/global-settings/commit-queue/sync'

# Setup variants
python: stop clean python-pkg parallel netsim $(CDB_INITS) start

java: stop clean java-pkg parallel netsim $(CDB_INITS) start

python-serial: stop clean python-pkg serial netsim $(CDB_INITS) start

java-serial: stop clean java-pkg serial netsim $(CDB_INITS) start

# Measure using default values
measure:
	python3 measure.py

# Showcase running the Python, Java, and serial variants
showcase:
	./showcase.sh
