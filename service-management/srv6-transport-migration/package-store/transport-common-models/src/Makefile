all: fxs
.PHONY: all fxs

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

LOCAL_YANG = ./yang

COMMON_MODELS = \
	iana-bgp-l2-encaps \
	iana-pseudowire-types \
	ietf-ethertypes \
	ietf-network \
	ietf-network-topology \
	ietf-packet-fields \
	ietf-netconf-acm \
	ietf-key-chain \
	ietf-ethernet-segment \
	ieee802-dot1q-types \
	ietf-vpn-common
LOCAL_MODELS = \
	ietf-routing-types \
	ietf-te-types

DIRS = ../load-dir
FXS = \
	$(COMMON_MODELS:%=../load-dir/%.fxs) \
	$(LOCAL_MODELS:%=../load-dir/%.fxs)

YANGPATH += $(LOCAL_YANG)
NCSCPATH = $(YANGPATH:%=--yangpath %)

fxs: $(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

define compile_from
../load-dir/$(1).fxs: $(2)/$(1).yang | $(DIRS)
	$(NCSC) --fail-on-warnings --use-description \
		$(NCSCPATH) \
		-c -o $$@ $$<
endef

$(foreach model,$(COMMON_MODELS),$(eval $(call compile_from,$(model),$(LOCAL_YANG))))
$(foreach model,$(LOCAL_MODELS),$(eval $(call compile_from,$(model),$(LOCAL_YANG))))

clean:
	rm -rf $(DIRS)
.PHONY: clean
