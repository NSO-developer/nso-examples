include $(NCS_DIR)/src/ncs/build/include.netsim.mk

## If you need additional init xml files, put them in
## the current directory, and they will be picked up here.

XML = $(wildcard *.xml)
XML += $(CONFD_DIR)/var/confd/cdb/aaa_init.xml

EXTRA_FILES = confd.conf.netsim

YANG = $(wildcard ../src/yang/*.yang)
SUBMODULES          = $(shell grep -l belongs-to ../src/yang/*.yang \
                        | xargs grep -l submodule)
ANNOTATION_MODULES  = $(wildcard ../src/yang/*-ann.yang)
YANG_MODULES = $(filter-out $(SUBMODULES) $(ANNOTATION_MODULES),$(YANG))

FXS = $(YANG_MODULES:../src/yang/%.yang=%.fxs)
FLAGS = --yangpath ../src/yang

all: $(FXS)

%.fxs:  ../src/yang/%.yang
	$(CONFDC) $(FLAGS) \
          `ls ../src/yang/$*-ann.yang  > /dev/null 2>&1 && \
              echo "-a ../src/yang/$*-ann.yang"` \
          -c -o $@  $<

# Invoked by netsim which will sed substitute the %var% PORT variables
# in the confd.conf.netsim file
install:
	@if [ -z "$${DEST_DIR}" ]; then echo "No DEST_DIR  var"; exit 1; fi
	@if [ ! -d "$${DEST_DIR}" ]; then "echo No DEST_DIR"; exit 1; fi
	mkdir $${DEST_DIR}/cdb 2>/dev/null || true
	mkdir $${DEST_DIR}/logs 2>/dev/null || true
	cp -af scripts $${DEST_DIR}
	cp -f $(XML) $${DEST_DIR}/cdb
	cp -f $(FXS) $${DEST_DIR}
	cp -f $(EXTRA_FILES) $${DEST_DIR}
	cp -af $(CONFD_DIR)/etc/confd/ssh $${DEST_DIR}

clean:
	@rm -f *.fxs *.ccl
