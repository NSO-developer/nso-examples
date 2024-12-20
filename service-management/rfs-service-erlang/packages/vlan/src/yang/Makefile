# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRCS = $(wildcard *.yang)
FXSS = $(SRCS:%.yang=../../load-dir/%.fxs)

all: $(FXSS)

../../load-dir/%.fxs: %.yang
	$(NCSC)  `ls $*-ann.yang  > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
             $(YANGPATH) -c -o $@ $<

clean:
	rm -rf $(FXSS)
