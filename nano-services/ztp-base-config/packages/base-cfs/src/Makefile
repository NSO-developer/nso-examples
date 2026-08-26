all: fxs
.PHONY: all

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
DIRS = ../load-dir
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)

NCSCPATH = $(YANGPATH:%=--yangpath %)

fxs: $(FXS)

$(DIRS):
	mkdir -p $@

../load-dir/%.fxs: yang/%.yang | $(DIRS)
	$(NCSC) --fail-on-warnings $(NCSCPATH) -c -o $@ $<

clean:
	rm -rf $(DIRS)
.PHONY: clean
