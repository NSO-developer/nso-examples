# The order of packages is significant as there are dependencies bewteen
# the packafes. Typically generated namespaces are used by other packages.
PACKAGE_DIRS = router-nc-1.1 alarms
NCS_CORE_JARS = $(NCS_DIR)/java/jar/*
ALARMS_JAR = packages/alarms/private-jar/alarms.jar
ALARMS_CLASSES = packages/alarms/src/java/build/classes
DEMO_MAAPI_CLASSPATH = $(NCS_CORE_JARS):$(ALARMS_JAR):$(ALARMS_CLASSES)

all:
	for i in $(PACKAGE_DIRS); do \
		$(MAKE) -C packages/$${i}/src all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim  --dir netsim create-network packages/router-nc-1.1 3 ex


clean:
	make -C packages/alarms/src clean
	rm -rf netsim logs/* state/* ncs-cdb/*.cdb ncs-cdb/{A,O,S}

demo:
	./demo.sh

demo-maapi: all
	java -classpath "$(DEMO_MAAPI_CLASSPATH)" demo_maapi.java
