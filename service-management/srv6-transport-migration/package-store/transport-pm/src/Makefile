all: fxs templates
.PHONY: all templates

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
DIRS = ../load-dir
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)

YANGPATH += ../../core-network/src/yang \
	../../transport-te/src/yang

NCSCPATH = $(YANGPATH:%=--yangpath %)

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
