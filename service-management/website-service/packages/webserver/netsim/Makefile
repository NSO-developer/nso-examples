include $(NCS_DIR)/src/ncs/build/include.netsim.mk

all:
	$(MAKE) $(FXS) interfaces.h \
	interface_stats \
	notifier_builtin_replay_store \
	notifier_by_action

XML = $(wildcard *.xml)
XML += $(CONFD_DIR)/var/confd/cdb/aaa_init.xml
YANG =  ../src/yang/interfaces@2009-12-06.yang \
	../src/yang/notif.yang \
	../src/yang/notifctrl.yang \
	../src/yang/sys.yang \
	../src/yang/webserver.yang
FXS = $(YANG:../src/yang/%.yang=%.fxs)
FXS += interfaces.fxs

FILES = confd.conf.netsim  \
        interface_stats \
	notifier_builtin_replay_store \
	notifier_by_action

interfaces.fxs:	../src/yang/interfaces@2009-12-06.yang
	$(CONFDC) --fail-on-warnings $(EXTRA_LINK_FLAGS) -c -o $@  $<
%.fxs:	../src/yang/%.yang
	$(CONFDC) --fail-on-warnings $(EXTRA_LINK_FLAGS) -c -o $@  $<

interface_stats: interface_stats.o
	$(CC) interface_stats.o $(LIBS) $(CFLAGS) -o $@
notifier_builtin_replay_store: notif.h notifier_builtin_replay_store.o
	$(CC) notifier_builtin_replay_store.o $(LIBS) $(CFLAGS) -o $@
notifier_by_action: notifctrl.h notifier_by_action.o
	$(CC) notifier_by_action.o $(LIBS) $(CFLAGS) -o $@

## Invoked by netsim, who will sed substitute the %var% PORT variables
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
	@rm -f interface_stats notifier_builtin_replay_store notifier_by_action
	@rm -f *.h *.o *.fxs
