all: fxs
.PHONY: all

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
DIRS = ../load-dir
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)
YANGPATH += ../../router-nc-1.1/src/yang

NCSCPATH = $(YANGPATH:%=--yangpath %)

fxs: $(DIRS) $(FXS)
.PHONY: fxs

$(DIRS):
	mkdir -p $@

../load-dir/%.fxs: yang/%.yang
	$(NCSC) `ls $*-ann.yang > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
		--fail-on-warnings \
		$(NCSCPATH) \
		-c -o $@ $<

clean:
	rm -rf $(DIRS)
.PHONY: clean
