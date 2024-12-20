# To use custom addresses, uncomment and change the following lines
#NSO_IP1 = 127.0.1.1
#NSO_IP2 = 127.0.2.1


define init_xml
	printf "<config xmlns=\"http://tail-f.com/ns/config/1.0\">\n\
	<high-availability xmlns=\"http://tail-f.com/ns/ncs\">\n\
		<token>super-secret</token>\n\
		<ha-node>\n\
			<id>n1</id>\n\
			<address>$(1)</address>\n\
			<nominal-role>primary</nominal-role>\n\
		</ha-node>\n\
		<ha-node>\n\
			<id>n2</id>\n\
			<address>$(2)</address>\n\
			<nominal-role>secondary</nominal-role>\n\
			<failover-primary>true</failover-primary>\n\
		</ha-node>\n\
		<settings>\n\
			<start-up>\n\
				<assume-nominal-role>true</assume-nominal-role>\n\
				<join-ha>true</join-ha>\n\
			</start-up>\n\
			<enable-failover>true</enable-failover>\n\
			<reconnect-interval>10</reconnect-interval>\n\
			<reconnect-attempts>6</reconnect-attempts>\n\
		</settings>\n\
	</high-availability>\n\
</config>"
endef

all: build

include ../common/node.mk

build: nso-node1 nso-node2

cdb-init/init.xml:
	@mkdir -p cdb-init
	$(call init_xml,$(NSO_IP1),$(NSO_IP2)) >$@

packages:
	@mkdir -p packages
	ncs-make-package --service-skeleton template --dest packages/dummy --build --no-test --root-container dummies dummy

clean: clean-nodes
	-rm -rf cdb-init packages

start: start-node1 start-node2
	$(NODE_IPC)1 ../common/wait-ha-connected n2

stop: stop-node1 stop-node2
	-ncs --stop
	@echo "##### All NSO instances stopped"

demo:
	./demo.sh -a $(NSO_IP1) -b $(NSO_IP2)

demo-rc:
	python3 ./demo_rc.py -a $(NSO_IP1) -b $(NSO_IP2)
