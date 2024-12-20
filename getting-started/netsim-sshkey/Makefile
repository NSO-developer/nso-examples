######################################################################
# Example showing the use of nano services for staged provisioning of
# keys for public key authentication with network elements managed by
# NSO
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
	ncs-netsim create-network ./packages/ne 3 ex --dir ./netsim
	ncs-netsim ncs-xml-init > ncs-cdb/devices.xml

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	-rm -rf ./netsim logs/* state/* ncs-cdb/* ./*_ed25519* storedstate target

start:
	ncs-netsim start
	ncs

stop:
	-ncs --stop
	-ncs-netsim stop
	@echo "##### All NSO and netsim instances stopped"

showcase:
	./showcase.sh 3

showcase-rc:
	python3 showcase_rc.py 3

showcase-maapi:
	python3 showcase_maapi.py 3
