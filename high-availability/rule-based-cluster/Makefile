# Make example run smoother - this is not recommended for production.
NCSCERT_NOCIPHER=1
export NCSCERT_NOCIPHER


all: node1/.ready node2/.ready node3/.ready

node%/.ready: packages/.ready
	# Create base file structure with ncs-setup
	ncs-setup --dest node$* --package packages/dummy
	cp ha.xml node$*/ncs-cdb/
	# Generate node's TLS certificate and use it
	../ca/create-cert n$* 127.0.$*.1 ::1 || true
	../ca/export-cert n$* node$*/ha-tls
	# Copy node-specific config to the ncs.conf.d directory
	cp -rv n$*.config node$*/ncs.conf.d
	# Tweak ncs.conf to make example behave nicely
	# (optional, because instances share the same host)
	../common/reconfigure node$*/ncs.conf $*
	@touch $@

packages/.ready:
	@mkdir -p packages
	ncs-make-package --service-skeleton template --dest packages/dummy \
		--build --no-test --root-container dummies dummy
	@touch $@

reset: all
	( cd node1 && ncs-setup --reset )
	( cd node2 && ncs-setup --reset )
	( cd node3 && ncs-setup --reset )

start:
	( cd node1 && ncs )
	( cd node2 && ncs )
	( cd node3 && ncs )
	NCS_IPC_PATH=/tmp/nso/nso-ipc1 ncs_cmd -c 'maction /high-availability/enable'
	NCS_IPC_PATH=/tmp/nso/nso-ipc2 ncs_cmd -c 'maction /high-availability/enable'
	NCS_IPC_PATH=/tmp/nso/nso-ipc3 ncs_cmd -c 'maction /high-availability/enable'
	NCS_IPC_PATH=/tmp/nso/nso-ipc1 ../common/wait-ha-connected n2
	NCS_IPC_PATH=/tmp/nso/nso-ipc1 ../common/wait-ha-connected n3

stop:
	NCS_IPC_PATH=/tmp/nso/nso-ipc1 ncs --stop || true
	NCS_IPC_PATH=/tmp/nso/nso-ipc2 ncs --stop || true
	NCS_IPC_PATH=/tmp/nso/nso-ipc3 ncs --stop || true

configure:

test:
	$(MAKE) demo
	$(MAKE) demo-rc

demo:
	./demo.sh -a 127.0.1.1 -b 127.0.2.1 -c 127.0.3.1

demo-rc:
	python3 ./demo_rc.py -a 127.0.1.1 -b 127.0.2.1 -c 127.0.3.1

clean:
	rm -rf node* ca-* packages

include ../common/node-ops.mk
