all: fxs ../package-meta-data.xml
.PHONY: all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
DIRS = ../load-dir
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)

YANGPATH += ../../inventory/src/yang

NCSCPATH   = $(YANGPATH:%=--yangpath %)
YANGERPATH = $(YANGPATH:%=--path %)

fxs: $(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

../load-dir/%.fxs: yang/%.yang
	$(NCSC)  `ls $*-ann.yang  > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
		--fail-on-warnings --use-description \
		$(NCSCPATH) \
		-c -o $@ $<

../package-meta-data.xml: package-meta-data.xml.in
	@if [ -n "$$USE_RM" ]; then \
		sed -e 's/^<!--//' -e 's/-->$$//' $< >$@ ;\
	else \
		cat $< >$@ ;\
	fi

clean:
	rm -rf $(DIRS) ../package-meta-data.xml
.PHONY: clean
