all:
	$(MAKE) -C ../../common/packages/cisco-iosxr-netsim-cli-1.0/src all
	$(MAKE) -C ../../common/packages/juniper-junos-netsim-nc-1.0/src all
	$(MAKE) -C ../../common/packages/alu-sr-netsim-cli-1.0/src all
	for p in package-store/*/src; do $(MAKE) -C $$p || exit; done
	[ -d nso-run ] || ncs-setup --dest nso-run
	cp -RPf ./package-store/* ./nso-run/packages/
	cp -RPf ../../common/packages/cisco-iosxr-netsim-cli-1.0 ./nso-run/packages/
	cp -RPf ../../common/packages/juniper-junos-netsim-nc-1.0 ./nso-run/packages/
	cp -RPf ../../common/packages/alu-sr-netsim-cli-1.0 ./nso-run/packages/
	cp service-config/* nso-run/ncs-cdb/
	ncs-netsim --dir nso-run/netsim create-network ./nso-run/packages/cisco-iosxr-netsim-cli-1.0 6 dev
	ncs-netsim --dir nso-run/netsim add-to-network ./nso-run/packages/juniper-junos-netsim-nc-1.0 1 dev
	ncs-netsim --dir nso-run/netsim add-to-network ./nso-run/packages/alu-sr-netsim-cli-1.0 1 dev
	sed -i~ -f device_names nso-run/netsim/.netsiminfo
	ncs-netsim --dir nso-run/netsim ncs-xml-init > nso-run/ncs-cdb/devices.xml
	$(MAKE) init-devices

init-devices:
	cp device-config/core-2-startup.xml nso-run/netsim/dev/dev0/cdb/startup.xml
	cp device-config/core-3-startup.xml nso-run/netsim/dev/dev1/cdb/startup.xml
	cp device-config/core-4-startup.xml nso-run/netsim/dev/dev2/cdb/startup.xml
	cp device-config/core-5-startup.xml nso-run/netsim/dev/dev3/cdb/startup.xml
	cp device-config/pe-02-startup.xml nso-run/netsim/dev/dev4/cdb/startup.xml
	cp device-config/ce-1-3-startup.xml nso-run/netsim/dev/dev5/cdb/startup.xml
	cp device-config/pe-01-startup.xml nso-run/netsim/dev/dev6/cdb/startup.xml
	cp device-config/core-1-startup.xml nso-run/netsim/dev/dev7/cdb/startup.xml

start:
	ncs-netsim --dir nso-run/netsim start --async
	ncs --cd ./nso-run
	ncs_cmd -u admin -c 'maction /devices/sync-from'

stop:
	ncs --stop || true
	[ ! -d nso-run/netsim ] || ncs-netsim --dir nso-run/netsim stop --async

clean:
	for p in package-store/*/src; do $(MAKE) -C $$p clean || exit 1; done
	rm -fr nso-run/

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh
