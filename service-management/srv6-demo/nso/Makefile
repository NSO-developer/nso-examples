all: packages ncs-run/.ready

.PHONY: packages
packages:
	for p in packages/*/src; do $(MAKE) -C $$p || exit 1; done

ncs-run/.ready:
	[ -d ncs-run ] || ncs-setup --dest ncs-run
	(cd ncs-run && rm -rf packages && ln -s ../packages .)
	cp ncs-run/ncs.conf ncs-run/ncs.conf.in
	ncs_conf_tool -a '<style>c</style>' ncs-config cli \
		<ncs-run/ncs.conf.in >ncs-run/ncs.conf
	cp config/* ncs-run/ncs-cdb/
	touch $@

start: packages ncs-run/.ready
	cd ncs-run && ncs

stop:
	ncs --stop || true

reset:
	(cd ncs-run && ncs-setup --reset)

add-labsim:
	$(MAKE) -C ../labsim all
	ncs_load -lmu admin ../labsim/netsim/devices.xml
	ncs_cmd -u admin -c 'maction /devices/sync-from'

demo:
	$(MAKE) -C ../labsim stop clean
	$(MAKE) stop clean
	$(MAKE) -C ../labsim start
	$(MAKE) start add-labsim
	ncs_cmd -u admin -c 'maction_th /lab/reset'
	ncs_cmd -u admin -c 'maction_th /core-network/srv6:provision'
	ncs_load -lmu admin -F c sample-eline.txt
	ncs_load -lmu admin -F c sample-l3vpn.txt

test:
	$(MAKE) demo
	$(MAKE) stop clean
	$(MAKE) -C ../labsim stop clean

clean:
	for p in packages/*/src; do $(MAKE) -C $$p clean; done
	rm -fr ncs-run/
