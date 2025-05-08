ifeq ($(NSO_IP1),)
NSO_IP1 = 127.0.1.1
endif
ifeq ($(NSO_IP2),)
NSO_IP2 = 127.0.2.1
endif
ifeq ($(NSO_IP3),)
NSO_IP3 = 127.0.3.1
endif


ifeq ($(NODE_IPC),)
ifeq ($(NCS_IPC_PATH),)
NODE_IPC = NCS_IPC_ADDR=127.0.0.1 NCS_IPC_PORT=456
else
NODE_IPC = NCS_IPC_PATH=$(NCS_IPC_PATH).456
endif
endif


nso-node%: cdb-init/init.xml packages
	@echo "Setting up run directory for $@"
	ncs-setup --dest $@
	@sed -i.bak \
		-e '/<ncs-ip-address>/,/<\/ncs-ip-address>/d' \
		-e '/<ha>/,/<\/ha>/d' \
		-e '/<\/ncs-config>/d' \
		$@/ncs.conf
	cat ../common/extra-ncs.conf >> $@/ncs.conf
	@echo '</ncs-config>' >> $@/ncs.conf
	@sed -i.bak \
		-e 's/0\.0\.0\.0/$(NSO_IP$*)/g' \
		-e 's/127\.0\.0\.1/$(NSO_IP$*)/g' \
		-e 's/4569/456$*/g' \
		-e 's/u@ncs/u@n$*/g' \
		-e 's|<webui>|<webui><match-host-name>false</match-host-name>|g' \
		$@/ncs.conf
	if [ -e packages ]; then \
		cd $@/packages/ && ln -sf ../../packages/* ./ ; fi
	if [ -e cdb-init ]; then \
		cd $@/ncs-cdb/ && ln -sf ../../cdb-init/*.xml ./ ; fi
	@echo "sname=ncsd$*; export sname" > $@/run.env

.SECONDARY: nso-node*

nso-system%: nso-node%
	@echo "Updating nso-node$* to emulate system install"
	@(cd nso-node$* && mkdir -p opt/ncs var/opt/ncs/packages)
	@(cd nso-node$* && ln -s ${NCS_DIR} opt/ncs/current)
	@(cd nso-node$* && ln -s ../../packages opt/ncs/)
	@sed -i.bak \
		-e 's|<dir>$${NCS_DIR}/etc/ncs|<dir>opt/ncs/current/etc/ncs|g' \
		-e 's|<dir>./packages|<dir>var/opt/ncs/packages|' \
		nso-node$*/ncs.conf
	@sed -e 's|%ROOT%|$(shell pwd)/nso-node$*|' \
		-e 's/%NODEID%/$*/' ../common/system.env > nso-node$*/run.env

start-node%:
	(cd nso-node$* && . ./run.env && $(NODE_IPC)$* ncs)
	$(NODE_IPC)$* ncs_cmd -c 'maction /high-availability/enable'

stop-node%:
	-$(NODE_IPC)$* ncs --stop

cli%:
	 $(NODE_IPC)$* ncs_cli -Cu admin

clean-nodes:
	rm -rf nso-node*
