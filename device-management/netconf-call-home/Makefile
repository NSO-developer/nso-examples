all:
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 2 ex --dir ./netsim
	: # Make sure they have unique ssh host keys
	rm -f netsim/ex/ex*/ssh/ssh_host_*
	for d in netsim/ex/ex*/ssh ; do \
	  ( cd $$d && ssh-keygen -m PEM -t ed25519 -N '' -f ssh_host_ed25519_key ) ; \
	done

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $${f} clean || exit 1; \
	done
	rm -rf ./netsim logs/* state/* notif-app.pid
	rm -f ncs-cdb/*.cdb
	rm -rf ncs-cdb/{A,O,S}

demo:
	./demo.sh
