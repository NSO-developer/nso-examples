all: fxs
.PHONY: all fxs

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

YANGPATH += ./yang
YANGPATH += ../../transport-common-models/src/yang
YANGPATH += ../../core-network/src/yang
NCSCPATH = $(YANGPATH:%=--yangpath %)

DIRS = ../load-dir
FXS = \
	../load-dir/ietf-te-topology.fxs \
	../load-dir/te-topology-bridge.fxs

fxs: $(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

../load-dir/%.fxs: yang/%.yang | $(DIRS)
	$(NCSC) --fail-on-warnings --use-description \
		$(NCSCPATH) \
		-c -o $@ $<

clean:
	rm -rf $(DIRS)
.PHONY: clean
