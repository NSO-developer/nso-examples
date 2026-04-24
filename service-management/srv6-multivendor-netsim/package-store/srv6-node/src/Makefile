all: fxs templates
.PHONY: all templates

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
DIRS = ../load-dir
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)

YANGPATH += ../../core-network/src/yang

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

# NSO requires the full template on package load for template validation.
# Instead of a huge switch-like statement in the template for every type
# of IOS XR interface, we rather opt for a pregenerated set, selecting
# the right one to apply in service code.
TEMPLATES_SRC = $(wildcard ../templates/*.in)
TEMPLATES_XML = $(TEMPLATES_SRC:%.in=%.xml)

templates: $(TEMPLATES_XML)
../templates/%.xml: ../templates/%.in ../templates/srv6-node-intf.tpl
	python3 ../util/xlate-templates.py $< <../templates/srv6-node-intf.tpl >$@

clean:
	rm -rf $(DIRS) $(TEMPLATES_XML)
.PHONY: clean
