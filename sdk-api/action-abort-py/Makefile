netsim:
	ln -sf ../../../common/packages/cisco-iosxr-netsim-cli-1.0 packages/cisco-iosxr-netsim-cli-1.0
	$(MAKE) -C packages/cisco-iosxr-netsim-cli-1.0/src all
	ncs-netsim create-network packages/cisco-iosxr-netsim-cli-1.0 3 xr
	ncs-netsim ncs-xml-init > ncs-cdb/devices.xml

all: netsim
	$(MAKE) -C packages/abort-action/src all

clean:
	rm -f packages/cisco-iosxr-netsim-cli-1.0
	$(MAKE) -C packages/abort-action/src clean
	rm -rf netsim logs/* state/* ncs-cdb/*

start:
	ncs
	ncs-netsim --async start

stop:
	-ncs --stop
	-ncs-netsim --async stop

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh
