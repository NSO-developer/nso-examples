# Makefile for NED Package Processing

# Version definitions
A10_ACOS_VSN      := 3.23
ALU_SR_VSN        := 8.58
CISCO_ASA_VSN     := 6.18
CISCO_IOS_VSN     := 6.109
CISCO_IOSXR_VSN   := 7.64
CISCO_NX_VSN      := 5.27
DELL_FTOS_VSN     := 3.4
JUNIPER_JUNOS_VSN := 4.18

# Python command
PYTHON := python3

# Directories
NETSIM_YANG_DIR := netsim-yang
NED_PACKAGES_DIR := ned-packages
DEST_BASE := ../packages

# Sanitize script
SANITIZE_SCRIPT := ./tailf_ned_sanitize.py

# Create output directory
$(shell mkdir -p $(NETSIM_YANG_DIR))

# Define all targets
TARGETS := \
	$(NETSIM_YANG_DIR)/netsim-ned-a10-acos.yang \
	$(NETSIM_YANG_DIR)/netsim-ned-alu-sr.yang \
	$(NETSIM_YANG_DIR)/netsim-ned-cisco-asa.yang \
	$(NETSIM_YANG_DIR)/netsim-ned-cisco-ios.yang \
	$(NETSIM_YANG_DIR)/netsim-ned-cisco-ios-xr.yang \
	$(NETSIM_YANG_DIR)/netsim-ned-cisco-nx.yang \
	$(NETSIM_YANG_DIR)/netsim-ned-dell-ftos.yang \
	$(NETSIM_YANG_DIR)/netsim-ned-juniper-junos.yang

# Define copy targets
COPY_TARGETS := \
	$(DEST_BASE)/a10-acos-netsim-cli-1.0/src/yang/netsim-ned-a10-acos.yang \
	$(DEST_BASE)/alu-sr-netsim-cli-1.0/src/yang/netsim-ned-alu-sr.yang \
	$(DEST_BASE)/cisco-asa-netsim-cli-1.0/src/yang/netsim-ned-cisco-asa.yang \
	$(DEST_BASE)/cisco-ios-netsim-cli-1.0/src/yang/netsim-ned-cisco-ios.yang \
	$(DEST_BASE)/cisco-iosxr-netsim-cli-1.0/src/yang/netsim-ned-cisco-ios-xr.yang \
	$(DEST_BASE)/cisco-nx-netsim-cli-1.0/src/yang/netsim-ned-cisco-nx.yang \
	$(DEST_BASE)/dell-ftos-netsim-cli-1.0/src/yang/netsim-ned-dell-ftos.yang \
	$(DEST_BASE)/juniper-junos-netsim-nc-1.0/src/yang/netsim-ned-juniper-junos.yang

# Default target
.PHONY: all
all: $(COPY_TARGETS)

# Pattern rules for standard NED packages
$(NETSIM_YANG_DIR)/netsim-ned-a10-acos.yang: $(NED_PACKAGES_DIR)/a10-acos-cli-$(A10_ACOS_VSN)/netsim/tailf-ned-a10-acos.yang $(SANITIZE_SCRIPT)
	$(PYTHON) $(SANITIZE_SCRIPT) $< > $@

$(NETSIM_YANG_DIR)/netsim-ned-alu-sr.yang: $(NED_PACKAGES_DIR)/alu-sr-cli-$(ALU_SR_VSN)/netsim/tailf-ned-alu-sr.yang $(SANITIZE_SCRIPT)
	$(PYTHON) $(SANITIZE_SCRIPT) $< > $@

$(NETSIM_YANG_DIR)/netsim-ned-cisco-asa.yang: $(NED_PACKAGES_DIR)/cisco-asa-cli-$(CISCO_ASA_VSN)/netsim/tailf-ned-cisco-asa.yang $(SANITIZE_SCRIPT)
	$(PYTHON) $(SANITIZE_SCRIPT) $< > $@

$(NETSIM_YANG_DIR)/netsim-ned-cisco-ios.yang: $(NED_PACKAGES_DIR)/cisco-ios-cli-$(CISCO_IOS_VSN)/netsim/tailf-ned-cisco-ios.yang $(SANITIZE_SCRIPT)
	$(PYTHON) $(SANITIZE_SCRIPT) $< > $@

$(NETSIM_YANG_DIR)/netsim-ned-cisco-nx.yang: $(NED_PACKAGES_DIR)/cisco-nx-cli-$(CISCO_NX_VSN)/netsim/tailf-ned-cisco-nx.yang $(SANITIZE_SCRIPT)
	$(PYTHON) $(SANITIZE_SCRIPT) $< > $@

$(NETSIM_YANG_DIR)/netsim-ned-dell-ftos.yang: $(NED_PACKAGES_DIR)/dell-ftos-cli-$(DELL_FTOS_VSN)/netsim/tailf-ned-dell-ftos.yang $(SANITIZE_SCRIPT)
	$(PYTHON) $(SANITIZE_SCRIPT) $< > $@

$(NETSIM_YANG_DIR)/netsim-ned-juniper-junos.yang: $(NED_PACKAGES_DIR)/juniper-junos-nc-$(JUNIPER_JUNOS_VSN)/src/yang/junos.yang $(SANITIZE_SCRIPT)
	$(PYTHON) $(SANITIZE_SCRIPT) $< > $@

# Special rule for Cisco IOS-XR (uses sub-makefile)
$(NETSIM_YANG_DIR)/netsim-ned-cisco-ios-xr.yang: $(SANITIZE_SCRIPT)
	$(MAKE) -C get-xr-netsim-yang CISCO_IOSXR_VSN=$(CISCO_IOSXR_VSN)
	$(PYTHON) $(SANITIZE_SCRIPT) get-xr-netsim-yang/tailf-ned-cisco-ios-xr.yang > $@

# Copy rules
$(DEST_BASE)/a10-acos-netsim-cli-1.0/src/yang/netsim-ned-a10-acos.yang: $(NETSIM_YANG_DIR)/netsim-ned-a10-acos.yang
	cp $< $@

$(DEST_BASE)/alu-sr-netsim-cli-1.0/src/yang/netsim-ned-alu-sr.yang: $(NETSIM_YANG_DIR)/netsim-ned-alu-sr.yang
	cp $< $@

$(DEST_BASE)/cisco-asa-netsim-cli-1.0/src/yang/netsim-ned-cisco-asa.yang: $(NETSIM_YANG_DIR)/netsim-ned-cisco-asa.yang
	cp $< $@

$(DEST_BASE)/cisco-ios-netsim-cli-1.0/src/yang/netsim-ned-cisco-ios.yang: $(NETSIM_YANG_DIR)/netsim-ned-cisco-ios.yang
	cp $< $@

$(DEST_BASE)/cisco-iosxr-netsim-cli-1.0/src/yang/netsim-ned-cisco-ios-xr.yang: $(NETSIM_YANG_DIR)/netsim-ned-cisco-ios-xr.yang
	cp $< $@

$(DEST_BASE)/cisco-nx-netsim-cli-1.0/src/yang/netsim-ned-cisco-nx.yang: $(NETSIM_YANG_DIR)/netsim-ned-cisco-nx.yang
	cp $< $@

$(DEST_BASE)/dell-ftos-netsim-cli-1.0/src/yang/netsim-ned-dell-ftos.yang: $(NETSIM_YANG_DIR)/netsim-ned-dell-ftos.yang
	cp $< $@

$(DEST_BASE)/juniper-junos-netsim-nc-1.0/src/yang/netsim-ned-juniper-junos.yang: $(NETSIM_YANG_DIR)/netsim-ned-juniper-junos.yang
	cp $< $@

# Clean targets
.PHONY: clean-iosxr
clean-iosxr:
	$(MAKE) -C get-xr-netsim-yang clean

.PHONY: clean
clean: clean-iosxr
	rm -f *.yin
	rm -f $(TARGETS)
	rm -rf $(NETSIM_YANG_DIR)

# Individual device targets for convenience
.PHONY: a10-acos alu-sr cisco-asa cisco-ios cisco-iosxr cisco-nx dell-ftos

a10-acos: $(DEST_BASE)/a10-acos-netsim-cli-1.0/src/yang/netsim-ned-a10-acos.yang
alu-sr: $(DEST_BASE)/alu-sr-netsim-cli-1.0/src/yang/netsim-ned-alu-sr.yang
cisco-asa: $(DEST_BASE)/cisco-asa-netsim-cli-1.0/src/yang/netsim-ned-cisco-asa.yang
cisco-ios: $(DEST_BASE)/cisco-ios-netsim-cli-1.0/src/yang/netsim-ned-cisco-ios.yang
cisco-iosxr: $(DEST_BASE)/cisco-iosxr-netsim-cli-1.0/src/yang/netsim-ned-cisco-ios-xr.yang
cisco-nx: $(DEST_BASE)/cisco-nx-netsim-cli-1.0/src/yang/netsim-ned-cisco-nx.yang
dell-ftos: $(DEST_BASE)/dell-ftos-netsim-cli-1.0/src/yang/netsim-ned-dell-ftos.yang
juniper-junos: $(DEST_BASE)/juniper-junos-netsim-nc-1.0/src/yang/netsim-ned-juniper-junos.yang

# Help target
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  all        - Build all YANG files (default)"
	@echo "  clean      - Remove generated YANG files"
	@echo ""
	@echo "Individual device targets:"
	@echo "  a10-acos   - Build A10 ACOS YANG files"
	@echo "  alu-sr     - Build ALU SR YANG files"
	@echo "  cisco-asa  - Build Cisco ASA YANG files"
	@echo "  cisco-ios  - Build Cisco IOS YANG files"
	@echo "  cisco-iosxr - Build Cisco IOS-XR YANG files"
	@echo "  cisco-nx   - Build Cisco NX YANG files"
	@echo "  dell-ftos  - Build Dell FTOS YANG files"
	@echo "  juniper-junos - Build Juniper Junos YANG files"

# Parallel execution safety
.NOTPARALLEL: $(NETSIM_YANG_DIR)/netsim-ned-cisco-ios-xr.yang