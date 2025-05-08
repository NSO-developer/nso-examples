XR_NED ?= ../nso/packages/cisco-iosxr-srdemo-ns

all: netsim/.ready

netsim/.ready:
	$(MAKE) -C ../nso packages
	ncs-netsim create-network $(XR_NED) 8 xr
	sed -i~ -f device_names netsim/.netsiminfo
	ncs-netsim ncs-xml-init > netsim/devices.xml
	touch $@

start: netsim/.ready
	ncs-netsim start --async

stop:
	[ ! -d netsim ] || ncs-netsim stop --async

clean:
	rm -rf netsim/
