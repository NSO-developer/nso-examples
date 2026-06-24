NCS_CORE_JARS = $(NCS_DIR)/java/jar/*
ACTIONS_JAR = packages/actions/private-jar/actions.jar
ACTIONS_CLASSES = packages/actions/src/java/build/classes
DEMO_MAAPI_CLASSPATH = $(NCS_CORE_JARS):$(ACTIONS_JAR):$(ACTIONS_CLASSES)

all:
	$(MAKE) -C packages/actions/src all

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf logs/* state/* ncs-cdb/*

start:
	ncs

stop:
	-ncs --stop
	@echo "##### All NSO instances stopped"

demo:
	./demo.sh

demo-maapi: all
	java -classpath "$(DEMO_MAAPI_CLASSPATH)" demo_maapi.java
