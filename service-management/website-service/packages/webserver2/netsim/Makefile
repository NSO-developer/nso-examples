include $(NCS_DIR)/src/ncs/build/include.netsim.mk

## If you need additional init xml files, put them in
## the current directory, and they will be picked up here.

XML = $(wildcard *.xml)
XML += $(NCS_DIR)/netsim/confd/var/confd/cdb/aaa_init.xml

EXTRA_FILES = confd.conf.netsim \
        interface_stats notifier_builtin_replay_store

YANG = $(wildcard ../src/yang/*.yang ../src/yang-deviation/*.yang)
SUBMODULES          = $(shell grep -l belongs-to ../src/yang/*.yang \
                        | xargs grep -l submodule)

ANNOTATION_MODULES  = $(wildcard ../src/yang/*-ann.yang)
DEVIATION_MODULE    = $(wildcard ../src/yang-deviation/*-deviation.yang)

YANG_MODULES = $(filter-out \
        $(SUBMODULES) $(ANNOTATION_MODULES) $(DEVIATION_MODULE),$(YANG))

FXS0 = $(YANG_MODULES:../src/yang/%.yang=%.fxs)
FXS  = $(FXS0:../src/yang-deviation/%.yang=%.fxs)

FLAGS = --yangpath ../src/yang

all:
	@echo fxs: $(FXS)
	$(MAKE) $(FXS) interfaces.h \
	interface_stats notifier_builtin_replay_store

webserver.fxs: ../src/yang-deviation/webserver.yang $(DEVIATION_MODULE)
	$(CONFDC) $(FLAGS)                                    \
	    --deviation $(DEVIATION_MODULE)                   \
	    -c -o $@ ../src/yang-deviation/webserver.yang

%.fxs:  ../src/yang/%.yang
	$(CONFDC) $(FLAGS) \
          `ls ../src/yang/$*-ann.yang  > /dev/null 2>&1 && \
              echo "-a ../src/yang/$*-ann.yang"` \
          -c -o $@  $<

interfaces.h:	interfaces@2009-12-06.fxs
	$(CONFDC) --emit-h $@  $<
interface_stats: interface_stats.o
	$(CC) interface_stats.o $(LIBS) $(CFLAGS) -o $@
notifier_builtin_replay_store: notif.h notifier_builtin_replay_store.o
	$(CC) notifier_builtin_replay_store.o $(LIBS) $(CFLAGS) -o $@


# Invoked by netsim, who will sed substitute the %var% PORT variables
# in the confd.conf.netsim file
# The install target here will be invoked multiple times by ncs-netsim,
# once for each device in the simulation network
# The following env variable will be set when ncs-netsim invokes this
# install target
# - DEST_DIR this is where all the files shall go, it's the directory
#   that will be used as execution environment for this ConfD instance
# - NAME this is the name of the managed device
# - COUNTER this is the number of the managed device

install:
	@if [ -z "$${DEST_DIR}" ]; then echo "No DEST_DIR  var"; exit 1; fi
	@if [ ! -d "$${DEST_DIR}" ]; then "echo No DEST_DIR"; exit 1; fi
	mkdir -p $${DEST_DIR}/cdb 2>/dev/null || true
	mkdir -p $${DEST_DIR}/logs 2>/dev/null || true
	cp -f $(XML) $${DEST_DIR}/cdb
	cp -f $(FXS) $${DEST_DIR}
	cp -f $(EXTRA_FILES) $${DEST_DIR}
	cp -af $(NCS_DIR)/netsim/confd/etc/confd/ssh $${DEST_DIR}
clean:
	@rm -f *.fxs
