
JAVA_PACKAGE = com.example.xmlrpcdevice.xmlrpc
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/namespaces

all:	fxs javac

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

YANGPATH = --yangpath yang

FXS =   ncsc-out/modules/fxs/interfaces.fxs \
	ncsc-out/modules/fxs/interfaces-stats.fxs \
	../load-dir/rpc-ned-meta.fxs \
	../load-dir/gen-xml-rpc-id.fxs

fxs:	$(FXS)


ncsc-out/modules/fxs/interfaces.fxs:     yang/interfaces.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
                --ncs-skip-statistics \
		--ncs-ned-id gen-xml-rpc-id:genxmlrpc \
                --ncs-device-type generic-ned && cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/interfaces.java $@


ncsc-out/modules/fxs/interfaces-stats.fxs:     yang/interfaces-stats.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
                --ncs-skip-config \
                --ncs-skip-template \
		--ncs-ned-id gen-xml-rpc-id:genxmlrpc \
                --ncs-device-type generic-ned && cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/interfaces_stats.java $@

../load-dir/%.fxs:     %.yang
	$(NCSC) --yangpath yang -c $< -o $@

javac:
	cd java && ant -q all

clean:
	rm -rf  ncsc-out/* ../load-dir/*
	cd java && ant -q clean || true
	rm -f java/src/$(JDIR)/$(NS)/*.java
