all: fxs
.PHONY: all fxs

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

YANGPATH += ./yang
YANGPATH += ../../core-network/src/yang
YANGPATH += ../../inventory/src/yang
YANGPATH += ../../service-assurance/src/yang
YANGPATH += ../../transport-slice-service/src/yang
NCSCPATH = $(YANGPATH:%=--yangpath %)

DIRS = ../load-dir
FXS = ../load-dir/transport-advisor.fxs

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
