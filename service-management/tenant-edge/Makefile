all:
	$(MAKE) -C ../../common/packages/cisco-iosxr-netsim-cli-1.0/src all
	$(MAKE) -C ../../common/packages/arista-dcs-netsim-cli-1.0/src all
	for p in package-store/*/src; do $(MAKE) -C $$p || exit 1; done
	[ -d nso-run ] || ncs-setup --dest nso-run
	cp -RPf ./package-store/* ./nso-run/packages/
	cp -RPf ../../common/packages/cisco-iosxr-netsim-cli-1.0 ./nso-run/packages/
	cp -RPf ../../common/packages/arista-dcs-netsim-cli-1.0 ./nso-run/packages/
	cp service-config/catalog.xml nso-run/ncs-cdb/
	ncs-netsim --dir nso-run/netsim create-network ./nso-run/packages/cisco-iosxr-netsim-cli-1.0 1 dev
	ncs-netsim --dir nso-run/netsim add-to-network ./nso-run/packages/arista-dcs-netsim-cli-1.0 1 dev
	sed -i~ -f device_names nso-run/netsim/.netsiminfo
	ncs-netsim --dir nso-run/netsim ncs-xml-init > nso-run/ncs-cdb/devices.xml
	$(MAKE) init-devices

init-devices:
	cp device-config/xr-pe-1-startup.xml nso-run/netsim/dev/dev0/cdb/startup.xml
	cp device-config/eos-leaf-1-startup.xml nso-run/netsim/dev/dev1/cdb/startup.xml

start:
	ncs-netsim --dir nso-run/netsim start --async
	ncs --cd ./nso-run
	ncs_cmd -u admin -c 'maction /devices/sync-from'
	ncs_load -lmu admin -F x service-config/oob-init.xml

stop:
	ncs --cd nso-run --stop || true
	[ ! -d nso-run/netsim ] || ncs-netsim --dir nso-run/netsim stop --async

clean:
	for p in package-store/*/src; do $(MAKE) -C $$p clean || exit 1; done
	rm -fr nso-run/

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh
