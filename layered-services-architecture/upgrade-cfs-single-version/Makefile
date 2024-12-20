######################################################################
# Example showing a NSO version upgrade of the upper NSO instance for
# the single-version-deployment example,
#
# See the README file for more information
######################################################################
RED=\033[0;31m
NC=\033[0;m # No Color
BACKUP_FILE=backup.tar.gz
OS=$(shell uname -s)

ifeq ($(NCS_IPC_PATH),)
ENV = NCS_IPC_PORT=
else
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH).
endif

all: stop clean

check:
	@if [ -z ${NCS_DIR} ]; then \
	  printf "${RED}Where is NSO installed? Source ncsrc or set NCS_DIR to"; \
		printf " point it out!\n${NC}"; \
		exit 1; \
	fi

upgrade:
	./upper_nso_upgrade.sh -h
	./upper_nso_upgrade.sh

start: check upgrade

stop: check
	if [ -d lsa-single-version-deployment/netsim ]; then \
		ncs-netsim --dir lsa-single-version-deployment/netsim stop; \
	fi
	-env $(ENV)4569 ncs --stop
	-env $(ENV)4570 ncs --stop
	-env $(ENV)4571 ncs --stop
	@echo "##### All NSO and netsim instances stopped"

clean:
	rm -rf lsa-single-version-deployment
	rm -rf packages $(BACKUP_FILE)

backup:
ifeq ($(OS),Darwin)
	-tar cfz $(BACKUP_FILE) lsa-single-version-deployment packages
else
	-tar --warning=no-file-changed --gzip -cf $(BACKUP_FILE) lsa-single-version-deployment packages
endif
	@printf "\n${GREEN}$(BACKUP_FILE) created\n${NC}"

restore:
ifneq ("$(wildcard $(BACKUP_FILE))","")
		rm -rf lsa-single-version-deployment packages
		tar xvfz $(BACKUP_FILE)
		@printf "\n${GREEN}$(BACKUP_FILE) restored\n${NC}"
else
		@printf "\n${RED}$(BACKUP_FILE) does not exist\n${NC}"
endif
