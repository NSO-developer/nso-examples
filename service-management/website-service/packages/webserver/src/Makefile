all: fxs javac
	 $(MAKE) -C ../netsim all
.PHONY: all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

JAVA_PACKAGE = com.example.websiteservice.webserver
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix                \
         --exclude-enums                      \
         --fail-on-warnings                   \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/$(NS)

NOREVSRC = yang/notif.yang yang/sys.yang yang/webserver.yang
FXS = $(NOREVSRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)
FXS += ncsc-out/modules/fxs/interfaces.fxs
DIRS = ncsc-out ../load-dir ../shared-jar ../private-jar \
       java/src java/src/$(JDIR)/$(NS)
NED_ID_ARG = $(shell [ -x ${NCS_DIR}/support/ned-ncs-ned-id-arg ] && \
               ${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in)

## We choose to do the revision compilation of the interfaces module
## here, the same module also occurs in the 'lb' package

IFREVS = yang/interfaces@2006-11-05.yang yang/interfaces@2009-12-06.yang

fxs: $(DIRS) ../package-meta-data.xml $(FXS)
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

## a module with two different revisions
ncsc-out/modules/fxs/interfaces.fxs:	$(IFREVS)
	set -e;                                   \
	for target in $^; do                      \
	    $(NCSC) --ncs-compile-module $$target \
	    --ncs-device-dir ncsc-out             \
	    --ncs-device-type netconf             \
	    $(NED_ID_ARG);                        \
	done
	cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/interfaces.java $@

ncsc-out/modules/fxs/%.fxs:	yang/%.yang
	$(NCSC) --ncs-compile-module $<      \
	        --ncs-device-dir ncsc-out    \
	        --ncs-device-type netconf    \
	        $(NED_ID_ARG) &&             \
	cp $@ ../load-dir; \
	$(NCSC) $(JFLAGS)/$*.java $@

javac:
	cd java && ant -q all
.PHONY: javac

clean:
	rm -rf $(DIRS)
	rm -rf ../package-meta-data.xml
	cd java && ant -q clean || true
	$(MAKE) -C ../netsim clean
.PHONY: clean
