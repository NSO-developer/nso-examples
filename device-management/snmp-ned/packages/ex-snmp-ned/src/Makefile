all:    fxs
	cd ../netsim && $(MAKE) all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

NS  = namespaces

SRC = $(wildcard mibs/*.mib)
FXS = $(SRC:mibs/%.mib=ncsc-out/modules/fxs/%.fxs)

fxs:    ncsc-out/modules

ncsc-out/modules:       $(SRC)
	$(NCSC) --ncs-compile-mib-bundle mibs \
		--ncs-ned-id tailf-ncs-ned:snmp         \
		--ncs-device-dir ncsc-out &&  \
	cp ncsc-out/modules/fxs/*.fxs ../load-dir;
	for f in `echo ../load-dir/*.fxs`; do \
	  n=`basename $$f | sed 's/\.fxs//'` || exit 1; \
	done
	touch -m ncsc-out/modules


clean:
	rm -rf ncsc-out/* ../fxs/* ../load-dir/*
	cd ../netsim && $(MAKE) clean || true
