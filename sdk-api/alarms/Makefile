# The order of packages is significant as there are dependencies bewteen
# the packafes. Typically generated namespaces are used by other packages.
PACKAGE_DIRS = router-nc-1.1 alarms

all:
	for i in $(PACKAGE_DIRS); do \
		$(MAKE) -C packages/$${i}/src all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim  --dir netsim create-network packages/router-nc-1.1 3 ex


clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf netsim logs/* state/* ncs-cdb/*.cdb

demo:
	./demo.sh
