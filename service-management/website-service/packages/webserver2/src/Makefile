all: fxs javac
	$(MAKE) -C ../netsim  all
.PHONY: all

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

NS  = namespaces

JAVA_PACKAGE = com.example.webserver2
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/$(NS)

NOREVSRC = yang/notif.yang yang/sys.yang
NOREVDEVSRC = yang-deviation/webserver.yang
FXS = $(NOREVSRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)
FXS += $(NOREVDEVSRC:yang-deviation/%.yang=ncsc-out/modules/fxs/%.fxs)
FXS += ncsc-out/modules/fxs/interfaces.fxs
DIRS = ncsc-out ../load-dir java/src/$(JDIR)/$(NS) \
       ../private-jar ../shared-jar

NCSC_NED_ARGS = --ncs-device-dir ncsc-out \
	--ncs-device-type netconf
NED_ID_ARG = $(shell [ -x ${NCS_DIR}/support/ned-ncs-ned-id-arg ] && \
               ${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in)

NCSC_NED_ARGS += $(NED_ID_ARG)

## We choose to do the revision compilation of the interfaces module
## here, the same module also occurs in the 'lb' package

IFREVS = yang/interfaces@2006-11-05.yang yang/interfaces@2009-12-06.yang
WSDEV = yang-deviation/webserver.yang yang-deviation/webserver-deviation.yang

fxs: $(DIRS) ../package-meta-data.xml
	$(MAKE) $(FXS)

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
	set -e; \
	for target in $^; do \
		$(NCSC) --ncs-compile-module $$target \
		$(NCSC_NED_ARGS); \
	done
	cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/interfaces.java $@

ncsc-out/modules/fxs/webserver.fxs:	$(WSDEV)
	$(NCSC) --ncs-compile-bundle yang-deviation \
		$(NCSC_NED_ARGS) --ncs-apply-deviations && \
	cp $@ ../load-dir; \
	$(NCSC) $(JFLAGS)/webserver.java $@

ncsc-out/modules/fxs/%.fxs:	yang/%.yang
	$(NCSC) --ncs-compile-module $< \
		$(NCSC_NED_ARGS) && \
	cp $@ ../load-dir; \
	$(NCSC) $(JFLAGS)/$*.java $@

javac:
	cd java && ant -q all

clean:
	rm -f ../package-meta-data.xml
	rm -rf $(DIRS)
	cd java && ant -q clean || true
	 $(MAKE) -C ../netsim clean
	rm -rf java/src
.PHONY: clean
