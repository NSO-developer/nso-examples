JAVA_PACKAGE = com.example.vlan
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces
DIRS = ../load-dir ../private-jar ../shared-jar java/src/$(JDIR)/$(NS)

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/$(NS)

all:	$(DIRS) fxs javac

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)
FXS = $(SRC:yang/%.yang=../load-dir/%.fxs)

$(DIRS):
	mkdir -p $@

fxs:	$(FXS)

../load-dir/%.fxs:	yang/%.yang
	$(NCSC)  `ls $*-ann.yang  > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
             $(YANGPATH) -c -o $@ $<
	$(NCSC) $(JFLAGS)/$*.java $@

javac:
	cd java && ant -q all

clean:
	rm -rf $(DIRS)
	cd java && ant -q clean || true
