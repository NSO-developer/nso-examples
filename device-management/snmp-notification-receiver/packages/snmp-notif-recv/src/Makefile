
# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

JAVA_PACKAGE = com.example.snmpnotificationreceiver
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/namespaces

all:	fxs javac

YANG = $(wildcard yang/*.yang)
FXS = $(YANG:yang/%.yang=../load-dir/%.fxs)

fxs:	$(FXS)

../load-dir/%.fxs:	yang/%.yang
	$(NCSC)  `ls $*-ann.yang  > /dev/null 2>&1 && echo "-a $*-ann.yang"` \
             $(YANGPATH) -c -o $@ $<
	$(NCSC) $(JFLAGS)/$*.java $@ 

javac:
	cd java && ant -q all && cp build/jar/*.jar ../../private-jar

clean:
	rm -rf  ../load-dir/*
	rm -f ../*jar/*.jar
	cd java && ant -q clean || true
	rm -f java/src/$(JDIR)/$(NS)/*.java 

