all:
	for f in packages/*/src/Makefile; do \
	  (cd `dirname $$f` && $(MAKE) $@ ) || exit 1; \
	done; \
       $(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 3 ex --dir ./netsim

clean:
	for f in packages/*/src/Makefile; do \
	  (cd `dirname $$f` && $(MAKE) $@ ) || exit 1; \
	done; \
	rm -rf  netsim logs/* state/* ncs-cdb/*.cdb

demo:
	./demo.sh