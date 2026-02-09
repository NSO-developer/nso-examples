DEV_NAME = a10-acos
PACKAGE_NAME = $(DEV_NAME)-netsim
JAVA_PACKAGE = com.tailf.packages.ned.netsimcli

all: javac
	$(MAKE) -C ../netsim  all

include $(NCS_DIR)/src/ncs/build/include.ncs.mk

NS  = namespaces

JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --java-package $(JAVA_PACKAGE).$(NS) \
		 --fail-on-warnings \
         --emit-java java/src/$(JDIR)/$(NS)

javac: fxs
	cd java && ant -q all

SRC = $(wildcard yang/*.yang)
NFXS = ncsc-out/modules/fxs
FXS = $(SRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)
DIRS =  ncsc-out ../load-dir
DIRS += ../shared-jar ../private-jar java/src/$(JDIR)/$(NS)
NED_ID_ARG = $(shell [ -x ${NCS_DIR}/support/ned-ncs-ned-id-arg ] && \
               ${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in)

YANGPATH += $(NCS_DIR)/src/ncs/yang
NCSCPATH   = $(YANGPATH:%=--yangpath %)

.NOTPARALLEL: fxs
$(FXS): | $(DIRS)
fxs: ../package-meta-data.xml $(FXS) ncscj
.PHONY: fxs

$(DIRS):
	mkdir -p $@

../package-meta-data.xml: package-meta-data.xml.in
	rm -rf $@
	if [ -x ${NCS_DIR}/support/ned-make-package-meta-data ]; then \
	    ${NCS_DIR}/support/ned-make-package-meta-data $<;         \
	else                                                          \
	    grep -v 'to be replaced by build' $< > $@;                \
	fi
	chmod -w $@

$(NFXS)/netsim-ned-$(DEV_NAME).fxs: yang/netsim-ned-$(DEV_NAME).yang
	$(NCSC) --ncs-compile-module $<                     \
		--ncs-device-dir ncsc-out                       \
		--ncs-skip-statistics                           \
		--fail-on-warnings -w TAILF_SUGGEST_COMBO		\
		--ncs-device-type cli-ned                       \
		$(NCSCPATH)                                     \
		$(NED_ID_ARG) &&                                \
	cp $@ ../load-dir

$(NFXS)/netsim-ned-$(DEV_NAME)-stats.fxs: yang/netsim-ned-$(DEV_NAME)-stats.yang
	$(NCSC) --ncs-compile-module $<                     \
		--ncs-device-dir ncsc-out                       \
		--ncs-skip-template                             \
		--fail-on-warnings -w TAILF_SUGGEST_COMBO		\
		--ncs-device-type cli-ned                       \
		$(NCSCPATH)                                     \
		$(NED_ID_ARG) &&                                \
	cp $@ ../load-dir

ncscj:
	for f in `echo ../load-dir/*.fxs`; do \
	    n=`basename $$f | sed 's/\.fxs//'`; \
	    $(NCSC) $(JFLAGS)/$${n}.java $$f  || exit 1; \
	   true; \
	done

clean:
	rm -rf $(DIRS)
	rm -rf ../package-meta-data.xml
	$(MAKE) -C ../netsim clean
	cd java && ant -q clean || true
	rm -f *netsim-cli*.yang
.PHONY: clean
