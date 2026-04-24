NCS_DIR ?= $(abspath ../../../../../../ncs_dir)
export NCS_DIR

all: fxs templates
.PHONY: all templates

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
DIRS = ../load-dir
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)

NCSCPATH =

fxs: $(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

../load-dir/%.fxs: yang/%.yang | $(DIRS)
	$(NCSC) --fail-on-warnings --use-description $(NCSCPATH) \
		-c -o $@ $<

clean:
	rm -rf $(DIRS) $(TEMPLATES_XML)
.PHONY: clean
