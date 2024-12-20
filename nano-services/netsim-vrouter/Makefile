######################################################################
# Example showing use of Nano Services for Staged Provisioning of a
# Virtual Router
#
# See the README file for more information
######################################################################
CDB_DIR       := ncs-cdb
INITS         := $(wildcard *_init.xml)
CDB_INITS     := $(addprefix $(CDB_DIR)/,$(INITS))

all: $(CDB_INITS)
	cp -r ../../common/packages/router-nc-1.1 ./packages/
	rm -rf ./packages/router-nc-1.1/netsim
	cp -r netsim-router ./packages/router-nc-1.1/netsim
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done

$(CDB_DIR)/%.xml: %.xml
	rm -f $@ ; cp $< $@

clean:
	rm -rf packages/router-nc-1.1
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	-rm -rf ./netsim logs/* state/* ncs-cdb/*

start:
	ncs

stop:
	-ncs --stop
	-ncs-netsim stop
	@echo "##### All NSO and netsim instances stopped"

demo:
	./demo.sh 3

demo-rc:
	python3 demo_rc.py 3
