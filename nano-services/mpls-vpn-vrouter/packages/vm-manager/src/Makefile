
JAVA_PACKAGE = com.cisco.vmmanager
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/namespaces

TEMPLATES_IN=$(wildcard ../templates/*.xml.in)
TEMPLATES=$(TEMPLATES_IN:../templates/%.xml.in=../templates/%.xml)

all:	fxs javac $(TEMPLATES)

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

DIRS = ncsc-out ../load-dir ../shared-jar ../private-jar
SRC = $(wildcard yang/*.yang)
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)
YANGPATH = --yangpath ../../resource-manager/src/yang \
	   --yangpath ../../esc/src/ncsc-out/modules/yang \
           --yangpath ./yang

fxs:	$(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

../load-dir/%.fxs:	yang/%.yang
	$(NCSC)  $(YANGPATH) -c -o $@ $<
	$(NCSC) $(JFLAGS)/$*.java $@

%.xml: %.xml.in
ifeq "$(NCS_VERSION)" "3.3"
	sed -e 's|<local-user>admin</local-user>||g' $< > $@
else
	cp $< $@
endif

javac:
	cd java && ant -q all

clean:
	rm -rf $(TEMPLATES)
	rm -rf $(DIRS)
	rm -f ../*jar/*.jar
	cd java && ant -q clean || true
	rm -f java/src/$(JDIR)/$(NS)/*.java
