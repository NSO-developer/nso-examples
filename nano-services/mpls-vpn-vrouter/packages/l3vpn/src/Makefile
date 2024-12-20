
JAVA_PACKAGE = com.example.l3vpn
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/namespaces

JFLAGSENUM = --java-disable-prefix \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/namespaces

NOW:=$(shell date)
all:	fxs javac
	@echo ${NOW}

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

DIRS = ncsc-out ../load-dir ../shared-jar ../private-jar
SRC = $(wildcard yang/*.yang)
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)
YANGPATH = --yangpath ./yang \
	   --yangpath ../../esc/src/ncsc-out/modules/yang \
	   --yangpath ../../resource-manager/src/yang \
	   --yangpath ../../vm-manager/src/yang

fxs: $(DIRS) $(FXS)

$(DIRS):
	mkdir -p $@

../load-dir/l3vpn-types.fxs: yang/l3vpn-types.yang
	$(NCSC)  `ls $*-ann.yang  > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
             $(YANGPATH) -c -o $@ $<
	$(NCSC) $(JFLAGSENUM)/l3vpn-types.java $@

../load-dir/l3vpn.fxs:	yang/l3vpn.yang
	$(NCSC)  `ls $*-ann.yang  > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
             $(YANGPATH) -c -o $@ $<
	$(NCSC) $(JFLAGS)/l3vpn.java $@

javac:
	cd java && ant -q all

clean:
	rm -rf $(DIRS)
	rm -f ../*jar/*.jar
	cd java && ant -q clean || true
	rm -f java/src/$(JDIR)/$(NS)/*.java
