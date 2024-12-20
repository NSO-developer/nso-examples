######################################################################
# IETF Hardware NMDA example
#
# See the README file for more information
#####################################################################
# Where is ConfD installed? Make sure CONFD_DIR points it out
CONFD_DIR ?= ../..

# Include standard ConfD build definitions and rules
include $(CONFD_DIR)/src/confd/build/include.mk

# In case CONFD_DIR is not set (correctly), this rule will trigger
$(CONFD_DIR)/src/confd/build/include.mk:
	@echo 'Where is ConfD installed? Set $$CONFD_DIR to point it out!'
	@echo ''

######################################################################
RED=\033[0;31m
GREEN=\033[0;32m
PURPLE=\033[0;35m
NC=\033[0;m # No Color

CONFD_FLAGS = --addloadpath $(CONFD_DIR)/etc/confd --addloadpath fxs
CONFD_IPC_PORT ?= 4565
CONFD_NETCONF_PORT ?= 12022

CONFDC=$(CONFD_DIR)/bin/confdc

YANG_SOURCES        = $(wildcard yang/*.yang)
SUBMODULES          = $(shell grep -l belongs-to yang/*.yang)
ANNOTATION_MODULES  = $(wildcard yang/*-ann*.yang)

YANG_MODULES = $(filter-out $(SUBMODULES) $(ANNOTATION_MODULES),$(YANG_SOURCES))

ALL_FXS_FILES = $(YANG_MODULES:yang/%.yang=fxs/%.fxs)
FXS_DIR = ./fxs

fxs/%.fxs: yang/%.yang yang/%-ann.yang
	$(CONFDC) -c --no-features --yangpath yang \
	  --yangpath $(CONFD_DIR)/src/confd/aaa -o $@  -a yang/$*-ann.yang -- $<

fxs/%.fxs: yang/%.yang
	$(CONFDC) -c --no-features --yangpath yang \
	  --yangpath $(CONFD_DIR)/src/confd/aaa -o $@ -- $<

$(FXS_DIR):
	-mkdir -p $(FXS_DIR)

######################################################################
all: $(FXS_DIR) \
	$(ALL_FXS_FILES) \
	$(CDB_DIR) ssh-keydir
	@echo "Build complete"

######################################################################
clean:	iclean
	-rm -rf *.log *.trace hardware_state.* fxs cli-history

######################################################################
start:  stop
	@printf "\n${GREEN}### Move ConfD to start-phase 0\n${NC}"
	$(CONFD) --start-phase0 -c confd.conf $(CONFD_FLAGS)
	@printf "\n${GREEN}### Load the initial configuration data\n${NC}"
	CONFD_IPC_PORT=$(CONFD_IPC_PORT) confd_load -dd -i -o -m -l chassis_init.xml
	@printf "\n${GREEN}### Start the system-controller module\n${NC}"
	python3 src/system_controller.py $(CONFD_IPC_PORT) &

	@printf "\n${GREEN}### Wait for the system-controller to move ConfD to \
start-phase 1\n${NC}"
	confd_cmd -dd -p $(CONFD_IPC_PORT) -c 'wait-start 1'
	@printf "\n${GREEN}### Load the initial state data\n${NC}"
	CONFD_IPC_PORT=$(CONFD_IPC_PORT) confd_load -dd -O -m -l chassis_init.xml
	@printf "\n${GREEN}### Register an example card and have the card open up \
the nortbound interfaces by moving ConfD to start-phase 2\n${NC}"
	python3 src/card.py -p $(CONFD_IPC_PORT) -c "dummy" -a "82" -n "2391" \
	                    -m "tail-f" -r 1 -u 4 -l 1 -o 4 -i &

	@printf "\n${GREEN}### Wait for the card to move ConfD to \
start-phase 2\n${NC}"
	confd_cmd -dd -p $(CONFD_IPC_PORT) -c 'wait-start 2'
	@printf "\n${GREEN}### ConfD started\n### In another terminal window, run \
'make start-card' to register another example card\n${NC}"

start-card:
	@printf "\n${GREEN}### Register another example card\n${NC}"
	python3 src/card.py -p $(CONFD_IPC_PORT) -c "clone" -a "87" -n "9985" \
	                    -m "tail-f" -r 1 -u 4 -l 2 -o 2 &
######################################################################
stop:
	@printf "\n${GREEN}### Kill the ConfD daemon and the Python programs\n${NC}"
	$(CONFD) --stop || true

#   kill $$(pgrep -f "system_controller.py") $$(pgrep -f "card.py") || true

######################################################################
cli:
	$(CONFD_DIR)/bin/confd_cli -P $(CONFD_IPC_PORT) --user=admin \
	--groups=admin --interactive || echo Exit

cli-c:
	$(CONFD_DIR)/bin/confd_cli -P $(CONFD_IPC_PORT) -C --user=admin\
	 --groups=admin --interactive  || echo Exit

nc-get:
	netconf-console --port=$(CONFD_NETCONF_PORT) --rpc=nc-get-rpc.xml

nc-get-datax:
	netconf-console --port=$(CONFD_NETCONF_PORT) --rpc=nc-get-datax-rpc.xml

nc-get-datas:
	netconf-console --port=$(CONFD_NETCONF_PORT) --rpc=nc-get-datas-rpc.xml

nc-subscribe-hwstate:
	netconf-console --port=$(CONFD_NETCONF_PORT) \
	--create-subscription=hardware_state

nc-cfg-card:
	netconf-console --port=$(CONFD_NETCONF_PORT) --rpc=nc-cfg-card-rpc.xml
	netconf-console --port=$(CONFD_NETCONF_PORT) --commit

nc-rename-chassis:
	netconf-console --port=$(CONFD_NETCONF_PORT) --rpc=nc-rename-chassis-rpc.xml
	netconf-console --port=$(CONFD_NETCONF_PORT) --commit

######################################################################
