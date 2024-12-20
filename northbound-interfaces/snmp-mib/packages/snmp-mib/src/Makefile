NCS_FXS_DIR = $(NCS_DIR)/etc/ncs

all:	fxs ../load-dir/TAIL-F-TEST-MIB.bin

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

%.yang: %.mib
	$(NCSC) --mib2yang $< > $@

../load-dir/TAIL-F-TEST-MIB.bin: TAIL-F-TEST-MIB.mib TAIL-F-TEST-MIB.miba
	$(NCSC) -c TAIL-F-TEST-MIB.mib -f $(NCS_FXS_DIR) \
          --mib-annotation TAIL-F-TEST-MIB.miba ../load-dir/simple.fxs && \
	mv TAIL-F-TEST-MIB.bin ../load-dir

SRC = $(wildcard *.yang)
FXS = $(SRC:%.yang=../load-dir/%.fxs)

fxs:	$(FXS)

../load-dir/%.fxs:	%.yang
	$(NCSC)  `ls $*-ann.yang  > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
             $(YANGPATH) -c -o $@ $<

clean:
	rm -rf  *.fxs *.bin ../load-dir/*
