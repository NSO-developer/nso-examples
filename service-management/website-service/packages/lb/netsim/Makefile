
include $(NCS_DIR)/src/ncs/build/include.netsim.mk

XML = $(wildcard *.xml)
XML += $(CONFD_DIR)/var/confd/cdb/aaa_init.xml
YANG = ../src/yang/interfaces@2006-11-05.yang ../src/yang/lb.yang
FXS = interfaces.fxs lb.fxs
FILES = confd.conf.netsim interface_stats

.PHONY: all
all: $(FXS) interfaces.h interface_stats

interfaces.fxs:	../src/yang/interfaces@2006-11-05.yang
	$(CONFDC) --fail-on-warnings $(EXTRA_LINK_FLAGS) -c -o $@  $<

%.fxs:	../src/yang/%.yang
	$(CONFDC) --fail-on-warnings $(EXTRA_LINK_FLAGS) -c -o $@  $<

interface_stats: interface_stats.o
	$(CC) interface_stats.o $(LIBS) $(CFLAGS) -o $@

## Invoked by netsim, who will sed substitute the %var% variables
## in the .netsim files
install:
	@if [ -z "$${DEST_DIR}" ]; then echo "No DEST_DIR  var"; exit 1; fi
	@if [ ! -d "$${DEST_DIR}" ]; then "echo No DEST_DIR"; exit 1; fi
	mkdir -p $${DEST_DIR}/cdb
	mkdir -p $${DEST_DIR}/logs
	cp -f $(XML) $${DEST_DIR}/cdb
	cp -f $(FXS) $${DEST_DIR}
	cp -f $(FILES) $${DEST_DIR}
	cp -af $(CONFD_DIR)/etc/confd/ssh $${DEST_DIR}

clean:
	@rm -f interface_stats
	@rm -f *.h *.o *.fxs
