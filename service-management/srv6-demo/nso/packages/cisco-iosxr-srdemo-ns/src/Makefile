all: fxs javac netsim
.PHONY: all

include $(NCS_DIR)/src/ncs/build/include.ncs.mk
include package.mk


JDIR = java/src/$(shell echo $(JAVA_PACKAGE) | sed -e 's|\.|/|g')
SRC = $(wildcard yang/*.yang)
NFXS = ncsc-out/modules/fxs
FXS = $(SRC:yang/%.yang=$(NFXS)/%.fxs)
DIRS = ncsc-out ../load-dir
DIRS += ../shared-jar ../private-jar $(JDIR)


fxs: ncsc-out/.done
.PHONY: fxs

ncsc-out/.done:	$(SRC) $(DIRS) ../package-meta-data.xml
	$(NCSC) --ncs-compile-bundle yang \
	    --ncs-device-type cli-ned \
	    --ncs-device-dir ncsc-out \
	    `$${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in` \
	    $(NCSC_EXTRA_FLAGS)
	cp ncsc-out/modules/fxs/*.fxs ../load-dir
	touch $@

$(DIRS):
	mkdir -p $@

../package-meta-data.xml: package-meta-data.xml.in
	rm -f $@
	${NCS_DIR}/support/ned-make-package-meta-data $<
	chmod -w $@

package-meta-data.xml.in: package-meta-data.in.xml
	sed -e 's|PACKAGE_NAME|$(PACKAGE_NAME)|g' \
		-e 's|JAVA_PACKAGE|$(JAVA_PACKAGE)|g' $< > $@

javac: $(DIRS) $(JDIR)/CliNed.java
	cd java && ant -q all
.PHONY: javac

$(JDIR)/CliNed.java: CliNed.java.in
	mkdir -p $(dir $@)
	echo 'package $(JAVA_PACKAGE);' > $@
	cat CliNed.java.in >> $@

netsim:
	$(MAKE) -C ../netsim all
.PHONY: netsim

clean:
	rm -rf $(DIRS)
	rm -f ../package-meta-data.xml package-meta-data.xml.in
	rm -f $(PACKAGE_NAME)*.yang
	$(MAKE) -C ../netsim clean
	cd java && ant -q clean || true
.PHONY: clean
