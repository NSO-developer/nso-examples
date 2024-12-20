# To use custom addresses, uncomment and change the following lines
#NSO_IP1 = 127.0.1.1
#NSO_IP2 = 127.0.2.1

BACKUP_FILE = backup.tar.gz


define init_xml
	printf "<config xmlns=\"http://tail-f.com/ns/config/1.0\">\n\
	<high-availability xmlns=\"http://tail-f.com/ns/ncs\">\n\
		<token>super-secret</token>\n\
		<ha-node>\n\
			<id>n1</id>\n\
			<address>$(1)</address>\n\
			<nominal-role>primary</nominal-role>\n\
		</ha-node>\n\
		<ha-node>\n\
			<id>n2</id>\n\
			<address>$(2)</address>\n\
			<nominal-role>secondary</nominal-role>\n\
			<failover-primary>true</failover-primary>\n\
		</ha-node>\n\
		<settings>\n\
			<start-up>\n\
				<assume-nominal-role>true</assume-nominal-role>\n\
				<join-ha>true</join-ha>\n\
			</start-up>\n\
			<enable-failover>true</enable-failover>\n\
			<reconnect-interval>10</reconnect-interval>\n\
			<reconnect-attempts>6</reconnect-attempts>\n\
		</settings>\n\
	</high-availability>\n\
</config>"
endef

all: build

include ../common/node.mk

build: nso-node1 nso-node2 packages
	cp package-store/dummy-1.0.tar.gz nso-node1/packages/
	cp package-store/dummy-1.0.tar.gz nso-node2/packages/

cdb-init/init.xml:
	@mkdir -p cdb-init
	$(call init_xml,$(NSO_IP1),$(NSO_IP2)) >$@

packages: package-store

package-store:
	@mkdir package-store
	cd package-store ; \
	ncs-make-package --service-skeleton template --dest dummy-1.0 --build --no-test --root-container dummies dummy ; \
	tar cvfz dummy-1.0.tar.gz dummy-1.0 ; \
	rm -rf dummy-1.0 ; \
	ncs-make-package --service-skeleton template --dest inert-1.0 --build --no-test --root-container inerts inert ; \
	tar cvfz inert-1.0.tar.gz inert-1.0 ; \
	rm -rf inert-1.0 ; \
	ncs-make-package --service-skeleton template --dest dummy-1.1 --no-test --root-container dummies dummy ; \
	sed -i.bak -e "s/1.0/1.1/g" dummy-1.1/package-meta-data.xml ; \
	sed -i.bak -e "s%// replace with your own stuff here%leaf description {type string;}%g" dummy-1.1/src/yang/dummy.yang ; \
	make -C dummy-1.1/src clean all ; \
	tar cvfz dummy-1.1.tar.gz dummy-1.1 ; \
	rm -rf dummy-1.1

system: nso-system1 nso-system2
	cp package-store/dummy-1.0.tar.gz nso-node1/packages/
	cp package-store/dummy-1.0.tar.gz nso-node2/packages/
	(cd nso-node1/var/opt/ncs/packages && ln -s ../../../../opt/ncs/packages/* ./)
	(cd nso-node2/var/opt/ncs/packages && ln -s ../../../../opt/ncs/packages/* ./)

clean: clean-nodes
	-rm -rf cdb-init package-store $(BACKUP_FILE)

start: start-node1 start-node2
	$(NODE_IPC)1 ../common/wait-ha-connected n2

stop: stop-node1 stop-node2
	-ncs --stop
	@echo "##### All NSO instances stopped"

upgrade-nso:
	./upgrade_nso.sh -a $(NSO_IP1) -b $(NSO_IP2)

upgrade-nso-rc:
	python3 ./upgrade_nso_rc.py -a $(NSO_IP1) -b $(NSO_IP2)

upgrade-pkgs:
	./upgrade_pkgs.sh -a $(NSO_IP1) -b $(NSO_IP2)

upgrade-pkgs-rc:
	python3 ./upgrade_pkgs_rc.py -a $(NSO_IP1) -b $(NSO_IP2)

upgrade-pkgs-sys:
	./upgrade_pkgs_sys.sh -a $(NSO_IP1) -b $(NSO_IP2)

upgrade-pkgs-sys-rc:
	python3 ./upgrade_pkgs_sys_rc.py -a $(NSO_IP1) -b $(NSO_IP2)


# tar exits with 1 on warnings, in particular 'file changed as we read it'
backup:
	-tar czf $(BACKUP_FILE) nso-node*
	@printf "\n${GREEN}$(BACKUP_FILE) created\n${NC}"

restore:
ifneq ("$(wildcard $(BACKUP_FILE))","")
	rm -rf nso-node*
	tar xvzf $(BACKUP_FILE)
	@printf "\n${GREEN}$(BACKUP_FILE) restored\n${NC}"
else
	@printf "\n${RED}$(BACKUP_FILE) does not exist\n${NC}"
endif
