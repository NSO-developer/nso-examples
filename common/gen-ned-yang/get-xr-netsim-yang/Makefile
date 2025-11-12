# Makefile for Cisco IOS-XR netsim YANG Extraction

# Default version (can be overridden from command line or parent Makefile)
CISCO_IOSXR_VSN ?= 7.64

# Commands
NCS_NETSIM := ncs-netsim
NCS_SETUP := ncs-setup
NETCONF_CONSOLE := netconf-console

# Directories and files
NSO_RUNDIR := nso-rundir
NETSIM_DIR := $(NSO_RUNDIR)/netsim
NED_PACKAGE := ../ned-packages/cisco-iosxr-cli-$(CISCO_IOSXR_VSN)
OUTPUT_YANG := tailf-ned-cisco-ios-xr.yang
NETCONF_PORT := 12022

# Sentinel files for tracking state
NETWORK_CREATED := .network_created
SETUP_DONE := .setup_done
NETSIM_RUNNING := .netsim_running

# Default target
.PHONY: all
all: $(OUTPUT_YANG)

# Main target: Extract YANG schema
$(OUTPUT_YANG): $(SETUP_DONE)
	@echo "Starting netsim..."
	@$(NCS_NETSIM) --dir $(NETSIM_DIR) start
	@touch $(NETSIM_RUNNING)
	@echo "Extracting YANG schema..."
	@$(NETCONF_CONSOLE) --port=$(NETCONF_PORT) --get-schema=tailf-ned-cisco-ios-xr > $@.tmp
	@mv $@.tmp $@
	@echo "Stopping netsim..."
	@$(NCS_NETSIM) --dir $(NETSIM_DIR) stop
	@rm -f $(NETSIM_RUNNING)
	@echo "Successfully created $@"

# Setup NCS with netsim directory
$(SETUP_DONE): $(NETWORK_CREATED)
	@echo "Setting up NCS..."
	@$(NCS_SETUP) --netsim-dir $(NETSIM_DIR) --dest $(NSO_RUNDIR)
	@touch $@

# Create network simulation
$(NETWORK_CREATED): check-ned-package
	@echo "Creating network simulation with version $(CISCO_IOSXR_VSN)..."
	@mkdir -p $(NETSIM_DIR)
	@$(NCS_NETSIM) --dir $(NETSIM_DIR) create-network $(NED_PACKAGE) 1 xr
	@touch $@

# Check if NED package exists
.PHONY: check-ned-package
check-ned-package:
	@if [ ! -d "$(NED_PACKAGE)" ]; then \
		echo "Error: NED package not found at $(NED_PACKAGE)"; \
		echo "Please ensure cisco-iosxr-cli-$(CISCO_IOSXR_VSN) is available"; \
		exit 1; \
	fi

# Clean targets
.PHONY: clean
clean: stop-if-running
	@echo "Cleaning generated files..."
	@rm -f $(OUTPUT_YANG) $(OUTPUT_YANG).tmp
	@rm -f $(NETWORK_CREATED) $(SETUP_DONE) $(NETSIM_RUNNING)
	@rm -rf $(NSO_RUNDIR)

# Stop netsim if it's running
.PHONY: stop-if-running
stop-if-running:
	@if [ -f $(NETSIM_RUNNING) ]; then \
		echo "Stopping running netsim..."; \
		$(NCS_NETSIM) --dir $(NETSIM_DIR) stop || true; \
		rm -f $(NETSIM_RUNNING); \
	fi

# Force regeneration of YANG file
.PHONY: force
force: clean all

# Status check
.PHONY: status
status:
	@echo "Cisco IOS-XR version: $(CISCO_IOSXR_VSN)"
	@echo "NED package path: $(NED_PACKAGE)"
	@if [ -f $(OUTPUT_YANG) ]; then \
		echo "Output YANG file: $(OUTPUT_YANG) (exists)"; \
	else \
		echo "Output YANG file: $(OUTPUT_YANG) (not generated)"; \
	fi
	@if [ -f $(NETSIM_RUNNING) ]; then \
		echo "netsim status: Running"; \
	else \
		echo "netsim status: Not running"; \
	fi

# Help target
.PHONY: help
help:
	@echo "Cisco IOS-XR netsim YANG Extraction Makefile"
	@echo ""
	@echo "Usage:"
	@echo "  make [target] [CISCO_IOSXR_VSN=version]"
	@echo ""
	@echo "Targets:"
	@echo "  all       - Extract YANG schema (default)"
	@echo "  clean     - Remove generated files"
	@echo "  force     - Force regeneration of YANG file"
	@echo "  status    - Show current status"
	@echo "  help      - Show this help message"
	@echo ""
	@echo "Examples:"
	@echo "  make                           # Use default version"
	@echo "  make CISCO_IOSXR_VSN=7.64     # Specify version"
	@echo "  make clean                     # Clean generated files"
	@echo ""
	@echo "Current configuration:"
	@echo "  CISCO_IOSXR_VSN = $(CISCO_IOSXR_VSN)"

# Ensure netsim is stopped on error
.DELETE_ON_ERROR:
.INTERMEDIATE: $(NETWORK_CREATED) $(SETUP_DONE)