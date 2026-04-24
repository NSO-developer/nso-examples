# Make example run smoother - this is not recommended for production.
NCSCERT_NOCIPHER=1
export NCSCERT_NOCIPHER


all: node1/.ready node2/.ready node3/.ready packages/.ready

node%/.ready:
	# Create base file structure with ncs-setup
	ncs-setup --dest node$*
	# Generate node's TLS certificate and use it
	../ca/create-cert n$* 127.0.0.1 ::1 || true
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

stop:
	NCS_IPC_PATH=/tmp/nso/nso-ipc1 ncs --stop || true
	NCS_IPC_PATH=/tmp/nso/nso-ipc2 ncs --stop || true
	NCS_IPC_PATH=/tmp/nso/nso-ipc3 ncs --stop || true

configure:
	printf 'ha-raft create-cluster member [ %s ]\n' \
		'ncsd2@127.0.0.1:4572 ncsd3@127.0.0.1:4573' | \
		NCS_IPC_PATH=/tmp/nso/nso-ipc1 ncs_cli -nsCu admin -g admin

test:
	$(MAKE) stop
	$(MAKE) reset
	$(MAKE) start
	$(MAKE) configure
	$(MAKE) stop

demo:
	./demo.sh

demo-nonstop:
	./demo.sh -n

clean:
	rm -rf node* ca-* packages

include ../common/node-ops.mk
