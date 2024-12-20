######################################################################
# Example showing a YANG version upgrade using either CDM or
# revision-merge.
#
# See the README file for more information
######################################################################
RED=\033[0;31m
GREEN=\033[0;32m
NC=\033[0;m # No Color
NUM_DEVICES=2
BACKUP_FILE=backup.tar.gz

all:
	@printf "\n${GREEN}#### Do the original setup before upgrade\n${NC}"
	@printf "\n${GREEN}## Create the NED package\n${NC}"
	mkdir nc-ned
	cp nc-ned-yang/example-serial@2020-02-27.yang nc-ned
	cp nc-ned-yang/router@2020-02-27.yang nc-ned
	ncs-make-package --verbose --no-java --no-python --no-test --dest packages/router --package-version 1.0 --netconf-ned nc-ned router
	rm -rf nc-ned
	@printf "\n${GREEN}## Build the NED package\n${NC}"
	make -C packages/router/src all
	@printf "\n${GREEN}## Create the service package\n${NC}"
	ncs-make-package --verbose --no-test --dest packages/link --service-skeleton template link
	cp service-yang/link.yang packages/link/src/yang
	cp service-yang/link-template.xml.2020-02-27 packages/link/templates/link-template.xml
	@printf "\n${GREEN}## Build the service package\n${NC}"
	make -C packages/link/src all
	ncs-netsim create-network packages/router $(NUM_DEVICES) ex --dir ./netsim
	ncs-setup --dest .
	@printf "\n${GREEN}## Generate the initial device configuration\n${NC}"
	python3 init_cfggen.py $(NUM_DEVICES) > ncs-cdb/init.xml

revmerge:
	@printf "\n${GREEN}#### Upgrade to the NSO NED YANG model revison\n${NC}"
	@printf "\n${GREEN}## Add a new YANG model that is compatible with revision-merge. I.e. no backward incompatible changes\n${NC}"
	cp nc-ned-yang/router@2020-09-18.yang.revmerge packages/router/src/yang/router@2020-09-18.yang
	diff -u packages/router/src/yang/router@2020-02-27.yang packages/router/src/yang/router@2020-09-18.yang || :
	@printf "\n${GREEN}## revision-merge YANG model compilations cannot have the --fail-on-warnings flag set\n${NC}"
	sed -i.bak "s/--fail-on-warnings//" packages/router/src/Makefile
	rm packages/router/src/Makefile.bak
	@printf "\n${GREEN}## Update the package version to 1.0.1 - the NED ID will still be 1.0\n${NC}"
	sed -i.bak "s/<package-version>1.0/<package-version>1.0.1/" packages/router/src/package-meta-data.xml.in
	rm packages/router/src/package-meta-data.xml.in.bak
	@printf "\n${GREEN}## Rebuild the package\n${NC}"
	make -C packages/router/src all
	@printf "\n${GREEN}#### Upgrade the ex0 netsim device\n${NC}"
	@printf "\n${GREEN}## Replace the YANG model FXS in the netsim device directory for ex0\n${NC}"
	rm -f netsim/ex/ex0/router@2020-02-27.fxs
	cp packages/router/netsim/router@2020-09-18.fxs netsim/ex/ex0
	@printf "\n${GREEN}## Update the service template to use both the enabled-new leaf and set the interface type depending on the device YANG model revision\n${NC}"
	diff -u packages/link/templates/link-template.xml service-yang/link-template.xml.2020-09-18-revmerge || :
	cp service-yang/link-template.xml.2020-09-18-revmerge packages/link/templates/link-template.xml

cdm:
	@printf "\n${GREEN}#### Upgrade to the NSO NED YANG model revison\n${NC}"
	@printf "\n${GREEN}## Add a new package for devices that use the YANG model that will be upgraded with non-backward compatible changes\n${NC}"
	mkdir nc-ned
	cp nc-ned-yang/example-serial@2020-02-27.yang nc-ned
	cp nc-ned-yang/router@2022-01-25.yang.cdm nc-ned/router@2022-01-25.yang
	ncs-make-package --verbose --no-java --no-python --no-test --dest packages/router-new --package-version 1.1 --netconf-ned nc-ned router
	rm -rf nc-ned
	diff -u packages/router/src/yang/router@2020-09-18.yang packages/router-new/src/yang/router@2022-01-25.yang || :
	@printf "\n${GREEN}# Build the new package\n${NC}"
	make -C packages/router-new/src all
	@printf "\n${GREEN}#### Upgrade the ex0 netsim device\n${NC}"
	@printf "\n${GREEN}## Replace the YANG model FXS in the netsim device directory for ex0\n${NC}"
	rm -f netsim/ex/ex0/router@2020-02-27.fxs netsim/ex/ex0/router@2020-09-18.fxs
	cp packages/router-new/netsim/router@2022-01-25.fxs netsim/ex/ex0
	@printf "\n${GREEN}## Update the service template to set the config depending on the device NED ID\n${NC}"
	diff -u packages/link/templates/link-template.xml service-yang/link-template.xml.2022-01-25-cdm || :
	cp service-yang/link-template.xml.2022-01-25-cdm packages/link/templates/link-template.xml

start-nso:
	ncs --with-package-reload

start-netsim:
	ncs-netsim --async start

start: start-nso start-netsim

stop-nso:
	-ncs --stop

stop-netsim:
	-ncs-netsim --async stop

stop: stop-nso stop-netsim
	@echo "##### All NSO and netsim instances stopped"

clean:
	rm -rf nc-ned state logs ncs-cdb packages scripts README.ncs storedstate target netsim README.netsim $(BACKUP_FILE)

backup:
ifeq ($(OS),Darwin)
	-tar cfz $(BACKUP_FILE) logs ncs-cdb netsim packages scripts state
else
	-tar --warning=no-file-changed --gzip -cf $(BACKUP_FILE) logs ncs-cdb netsim packages scripts state
endif
	@printf "\n${GREEN}$(BACKUP_FILE) created\n${NC}"

restore:
ifneq ("$(wildcard $(BACKUP_FILE))","")
		rm -rf logs ncs-cdb netsim packages scripts state
		tar xvfz $(BACKUP_FILE)
		@printf "\n${GREEN}$(BACKUP_FILE) restored\n${NC}"
else
		@printf "\n${RED}$(BACKUP_FILE) does not exist!\n${NC}"
endif

demo:
	./demo.sh

demo-rc:
	python3 demo_rc.py
