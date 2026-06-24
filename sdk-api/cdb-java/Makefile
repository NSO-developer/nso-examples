NCS_CORE_JARS = $(NCS_DIR)/java/jar/*

all:
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim
	ncs-netsim ncs-xml-init > ncs-cdb/netsim_devices_init.xml

netsim:
	ncs-netsim create-network ./packages/router-nc-1.0 3 ex --dir ./netsim

clean:
	make -C packages/cdb/src clean
	rm -rf ./netsim logs/* state/* ncs-cdb/*.cdb ncs-cdb/{A,O,S} \
		ncs-cdb/netsim_devices_init.xml

start: stop
	ncs-netsim start
	ncs

stop:
	-ncs --stop
	-ncs-netsim -a stop
	@echo "##### All NSO and netsim instances stopped"

demo:
	./demo.sh

demo-maapi:
	java -classpath "$(NCS_CORE_JARS)" demo_maapi.java
