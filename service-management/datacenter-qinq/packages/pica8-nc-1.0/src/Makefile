all:	fxs javac
	cd ../netsim && $(MAKE) all
.PHONY: all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk


JAVA_PACKAGE = com.example.pica8
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
       java/src/$(JDIR)/$(NS)
NED_ID_ARG = $(shell [ -x ${NCS_DIR}/support/ned-ncs-ned-id-arg ] && \
               ${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in)

fxs:	$(DIRS) ../package-meta-data.xml ncsc-out/.done
.PHONY: fxs

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

javac:
	cd java && ant -q all
.PHONY: javac

ncsc-out/.done: $(SRC)
	$(NCSC) --ncs-compile-bundle yang                \
	        --ncs-device-dir ncsc-out                \
	        --ncs-device-type netconf                \
	        $(NED_ID_ARG)
	cp ncsc-out/modules/fxs/*.fxs ../load-dir
	touch ncsc-out/.done

clean:
	rm -rf $(DIRS)
	rm -rf ../package-meta-data.xml
	cd ../netsim && $(MAKE) clean || true
	cd java && ant -q clean || true
.PHONY: clean
