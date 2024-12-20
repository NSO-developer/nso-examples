all:
	cp -r package-repository/* packages
	cp -r ../../common/packages/router-nc-1.1 ./packages/
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 1 ex --dir ./netsim

start:
	ncs --addloadpath .
	ncs-netsim start --dir ./netsim
	echo "devices sync-from" | ncs_cli -C -u admin

stop:
	-ncs --stop
	-ncs-netsim -a stop

clean:
	rm -rf packages/* ./netsim logs/* state/* ncs-cdb/*.cdb target storedstate scripts/*.class

demo:
	./demo.sh
