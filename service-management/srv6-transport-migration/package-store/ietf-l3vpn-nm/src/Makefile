all: fxs templates
.PHONY: all fxs templates

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

LOCAL_YANG = ./yang
LOCAL_COMMON = ../../transport-common-models/src/yang

DIRS = ../load-dir
FXS = \
	../load-dir/00-ietf-l3vpn-ntw.fxs \
	../load-dir/10-l3vpn-nm-service.fxs

YANGPATH += $(LOCAL_YANG)
YANGPATH += $(LOCAL_COMMON)
NCSCPATH = $(YANGPATH:%=--yangpath %)

fxs: $(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

define compile_fxs
$(NCSC) --use-description \
	$(NCSCPATH) \
	-c -o $@ $<
endef

../load-dir/00-ietf-l3vpn-ntw.fxs: \
		$(LOCAL_YANG)/ietf-l3vpn-ntw.yang | $(DIRS)
	$(compile_fxs)

../load-dir/10-l3vpn-nm-service.fxs: \
		$(LOCAL_YANG)/l3vpn-nm-service.yang | $(DIRS)
	$(compile_fxs)

templates:

clean:
	rm -rf $(DIRS)
.PHONY: clean
