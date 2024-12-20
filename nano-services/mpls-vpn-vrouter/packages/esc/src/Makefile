all: fxs javac
	$(MAKE) -C ../netsim all
.PHONY: all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk


JAVA_PACKAGE = com.cisco.escned
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/$(NS)


SRC = $(wildcard yang/*.yang)
FXS = $(SRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)
DIRS = ncsc-out ../load-dir ../shared-jar ../private-jar \
       java/src java/src/$(JDIR)/$(NS)
NED_ID_ARG = $(shell [ -x ${NCS_DIR}/support/ned-ncs-ned-id-arg ] && \
               ${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in)

fxs: $(DIRS) ../package-meta-data.xml ncsc-out/.done
.PHONY: fxs

javac:
	cd java && ant -q all
.PHONY: javac

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

ncsc-out/.done:	$(SRC)
	$(NCSC) --ncs-compile-bundle yang     \
	        --ncs-device-dir ncsc-out     \
	        --ncs-device-type netconf     \
	        $(NED_ID_ARG) &&              \
	cp ncsc-out/modules/fxs/*.fxs ../load-dir;
	for f in `echo ../load-dir/*.fxs`; do \
	   n=`basename $$f | sed 's/\.fxs//'`; \
	   $(NCSC) $(JFLAGS)/$${n}.java $$f  || exit 1; \
	done
	touch ncsc-out/.done

clean:
	rm -rf $(DIRS)
	rm -rf ../package-meta-data.xml
	cd java && ant -q clean || true
	$(MAKE) -C ../netsim clean
.PHONY: clean
