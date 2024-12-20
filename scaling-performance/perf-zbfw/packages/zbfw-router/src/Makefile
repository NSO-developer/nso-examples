all: fxs
	cd ../netsim && $(MAKE) all
.PHONY: all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

DIRS = ncsc-out ../load-dir

FXS = $(SRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)
DIRS = ncsc-out ../load-dir
NED_ID_ARG = $(shell [ -x ${NCS_DIR}/support/ned-ncs-ned-id-arg ] && \
               ${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in)

$(DIRS):
	mkdir -p $@

../package-meta-data.xml: package-meta-data.xml.in
	rm -rf $@
	if [ -x ${NCS_DIR}/support/ned-make-package-meta-data ]; then \
	    ${NCS_DIR}/support/ned-make-package-meta-data $<;         \
	else                                                          \
	    cp $< $@;                                                 \
	fi
	chmod -w $@

fxs: $(DIRS) ../package-meta-data.xml ncsc-out/.done
.PHONY: fxs

ncsc-out/.done: $(SRC)
	$(NCSC) --ncs-compile-bundle yang    \
	        --ncs-device-dir ncsc-out    \
	        --ncs-device-type netconf    \
	        $(NED_ID_ARG)             && \
	cp ncsc-out/modules/fxs/*.fxs ../load-dir;
	for f in `echo ../load-dir/*.fxs`; do \
	   n=`basename $$f | sed 's/\.fxs//'`; \
	done
	touch ncsc-out/.done

clean:
	rm -rf $(DIRS)
	rm -rf ../package-meta-data.xml
	cd ../netsim && $(MAKE) clean || true
.PHONY: clean
