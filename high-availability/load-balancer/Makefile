ifeq ($(HAPROXY),)
HAPROXY = haproxy
endif

all: build

include ../common/node.mk

build: nso-node1 nso-node2
	@echo 'export HASTATUS_ADDR=$(NSO_IP1)' >> nso-node1/run.env
	@echo 'export HASTATUS_ADDR=$(NSO_IP2)' >> nso-node2/run.env

cdb-init/init.xml:

start: start-node1 start-node2
	@if [ -z "`which $(HAPROXY)`" ]; then \
		echo ""; \
		echo "This example requires haproxy executable to run but it was not found."; \
		echo "It can usually be installed with 'apt install haproxy' or similar command."; \
		echo ""; \
		exit 1; \
	fi
	$(HAPROXY) -f haproxy.conf -p haproxy.pid
	$(NODE_IPC)1 ../common/wait-ha-connected n2

stop: stop-node1 stop-node2
	if [ -f haproxy.pid ]; then kill `cat haproxy.pid`; fi
	rm -f haproxy.pid

clean: clean-nodes
	rm -rf haproxy.pid
