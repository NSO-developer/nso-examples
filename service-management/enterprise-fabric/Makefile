all:
	$(MAKE) -C ../../common/packages/cisco-ios-netsim-cli-1.0/src all
	$(MAKE) -C ../../common/packages/juniper-junos-netsim-nc-1.0/src all
	for p in package-store/*/src; do $(MAKE) -C $$p || exit 1; done
	[ -d nso-run ] || ncs-setup --dest nso-run
	cp -RPf ./package-store/* ./nso-run/packages/
	cp -RPf ../../common/packages/cisco-ios-netsim-cli-1.0 \
		./nso-run/packages/
	cp -RPf ../../common/packages/juniper-junos-netsim-nc-1.0 \
		./nso-run/packages/
	cp service-config/catalog.xml nso-run/ncs-cdb/
	ncs-netsim --dir nso-run/netsim create-network \
		./nso-run/packages/cisco-ios-netsim-cli-1.0 8 dev
	ncs-netsim --dir nso-run/netsim add-to-network \
		./nso-run/packages/juniper-junos-netsim-nc-1.0 2 dev
	sed -i~ -f device_names nso-run/netsim/.netsiminfo
	ncs-netsim --dir nso-run/netsim ncs-xml-init \
		> nso-run/ncs-cdb/devices.xml
	$(MAKE) init-devices

init-devices:
	cp device-config/lon-core1-startup.xml nso-run/netsim/dev/dev0/cdb/startup.xml
	cp device-config/man-core1-startup.xml \
		nso-run/netsim/dev/dev1/cdb/startup.xml
	cp device-config/nyc-core1-startup.xml \
		nso-run/netsim/dev/dev2/cdb/startup.xml
	cp device-config/sgp-core1-startup.xml nso-run/netsim/dev/dev3/cdb/startup.xml
	cp device-config/lon-br1-startup.xml nso-run/netsim/dev/dev4/cdb/startup.xml
	cp device-config/man-br1-startup.xml nso-run/netsim/dev/dev5/cdb/startup.xml
	cp device-config/sgp-br1-startup.xml nso-run/netsim/dev/dev6/cdb/startup.xml
	cp device-config/nyc-br1-startup.xml nso-run/netsim/dev/dev7/cdb/startup.xml
	cp device-config/lon-tr1-startup.xml \
		nso-run/netsim/dev/dev8/cdb/startup.xml
	cp device-config/nyc-tr1-startup.xml \
		nso-run/netsim/dev/dev9/cdb/startup.xml

start:
	ncs-netsim --dir nso-run/netsim start --async
	ncs --cd ./nso-run
	ncs_cmd -u admin -c 'maction /devices/sync-from'
	ncs_load -lmu admin -F x service-config/oob-init.xml
	ncs_load -lmu admin -F x service-config/compliance-init.xml

stop:
	ncs --cd nso-run --stop || true
	[ ! -d nso-run/netsim ] || \
		ncs-netsim --dir nso-run/netsim stop --async

clean:
	for p in package-store/*/src; do $(MAKE) -C $$p clean || exit 1; done
	rm -fr nso-run/

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh
