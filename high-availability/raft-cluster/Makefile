all: certs packages-all node1 node2 node3
	$(MAKE) install-certs

packages-%:
	for p in packages/*/src; do \
		$(MAKE) -C $$p $* || exit 1; \
	done

certs:
	cp tpl/certext.in ncsd1.cnf
	cp tpl/certext.in ncsd2.cnf
	cp tpl/certext.in ncsd3.cnf
	# creates ca.crt, ca.key, ncsd[1-3].crt and ncsd[1-3].key in raft/
	./gen_tls_certs.sh -d raft ncsd1 ncsd2 ncsd3

install-certs:
	cp -f raft/certs/ca.crt raft/certs/ncsd1.crt raft/private/ncsd1.key node1
	cp -f raft/certs/ca.crt raft/certs/ncsd2.crt raft/private/ncsd2.key node2
	cp -f raft/certs/ca.crt raft/certs/ncsd3.crt raft/private/ncsd3.key node3
	# ensure only the user running the ncs.smp process has read access to
	# the private TLS key
	chmod 600 node*/*.key

node%:
	ncs-setup --dest $@
	NODE_ID=$* helpers/update-conf.sh $@/ncs.conf \
		$$(ls tpl/ncs-conf/*.xml tpl/ncs-conf/*.val | sort)

start:
	( cd node1 && ncs )
	( cd node2 && ncs )
	( cd node3 && ncs )

stop:
	NCS_IPC_PORT=4561 ncs --stop || true
	NCS_IPC_PORT=4562 ncs --stop || true
	NCS_IPC_PORT=4563 ncs --stop || true

demo:
	./demo.sh

demo-nonstop:
	./demo.sh -n

clean:  packages-clean
	$(RM) -r node* raft
	$(RM) ./*.cnf
