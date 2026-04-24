NCSCERT_NOCIPHER=1
export NCSCERT_NOCIPHER

all: ha

demo: all

cert: ncs-run/ha-tls/node.crt

ha: ncs-run/.ready cert
	@echo '##### Configure NSO to use the certificates in ha-tls/'
	cp ha.conf ncs-run/ncs.conf.d/ha.conf
	cat ha.conf

raft: ncs-run/.ready cert
	@echo '##### Configure NSO to use the certificates in ha-tls/'
	cp raft.conf ncs-run/ncs.conf.d/ha.conf
	cat raft.conf

ncs-run/.ready:
	ncs-setup --dest ncs-run
	mkdir -p ncs-run/ncs.conf.d
	touch $@

ncs-run/ha-tls/node.crt:
	@echo '##### Initialize a new CA (optional)'
	#../ca/init-new-ca
	@echo '##### Generate the TLS certificate'
	@echo 'You can specify hostname or IP address (or more of them)'
	../ca/create-cert somenode.example.com 198.51.100.1 || true
	@echo '##### Copy the certificate and other files into ncs-run/ha-tls/'
	../ca/export-cert somenode.example.com ncs-run/ha-tls/

test:
	ncs --stop || true
	$(MAKE) clean
	$(MAKE) ha
	cd ncs-run && ncs && ncs --stop
	$(MAKE) clean
	$(MAKE) raft
	cd ncs-run && ncs && ncs --stop

stop:

clean:
	rm -fr ca-* ncs-run/

.PHONY: all cert ha raft test clean
