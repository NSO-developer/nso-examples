all: fxs templates
.PHONY: all fxs templates

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

LOCAL_YANG = ./yang
LOCAL_COMMON = ../../transport-common-models/src/yang

DIRS = ../load-dir
FXS = \
	../load-dir/00-ietf-te.fxs \
	../load-dir/10-te-tunnel-service.fxs \
	../load-dir/10-te-tunnel-extensions.fxs

YANGPATH += $(LOCAL_YANG)
YANGPATH += $(LOCAL_COMMON)
NCSCPATH = $(YANGPATH:%=--yangpath %)

fxs: $(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

define compile_fxs
$(NCSC) --fail-on-warnings --use-description $(NCSC_ANNOTATION) \
	$(NCSCPATH) \
	-c -o $@ $<
endef

../load-dir/00-ietf-te.fxs: NCSC_ANNOTATION = -a $(LOCAL_YANG)/ietf-te-ann.yang
../load-dir/00-ietf-te.fxs: \
		$(LOCAL_YANG)/ietf-te.yang \
		$(LOCAL_YANG)/ietf-te-ann.yang | $(DIRS)
	$(compile_fxs)

../load-dir/10-te-tunnel-service.fxs: \
		$(LOCAL_YANG)/te-tunnel-service.yang | $(DIRS)
	$(compile_fxs)

../load-dir/10-te-tunnel-extensions.fxs: \
		$(LOCAL_YANG)/te-tunnel-extensions.yang | $(DIRS)
	$(compile_fxs)

templates:

clean:
	rm -rf $(DIRS)
.PHONY: clean
