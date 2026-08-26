NETSIM_DEVICES = xr-1 xr-2 xr-3 xr-4
IOSXR_NED = cisco-iosxr-netsim-cli-1.0
IOSXR_NED_ID = $(IOSXR_NED)
IOSXR_NED_SOURCE = ../../common/packages/$(IOSXR_NED)
CFS_PACKAGE = packages/base-cfs
RFS_PACKAGE = packages/base-rfs
CFS_FXS = $(CFS_PACKAGE)/load-dir/base-cfs.fxs
RFS_FXS = $(RFS_PACKAGE)/load-dir/base-rfs.fxs
NETSIM_START_SCRIPT = device-config/netsim-start.sh
DHCP_ZTP_CLIENT = device-config/dhcp-ztp-client.py
DHCP_LEASES = device-config/dhcp-leases.json
ZTP_PAYLOAD = device-config/ztp-provision.py
ZTP_INVENTORY = device-config/ztp-inventory.json
ZTP_PRE_CONFIG = device-config/ztp-pre.cli
ZTP_HTTP_PORT = 30604
NCS_IPC_PATH ?= /tmp/nso/ztp-base-config-ipc
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH)
PYTHON ?= $(HOME)/.venv/bin/python
PYTHON_BIN_DIR = $(dir $(abspath $(PYTHON)))
NETSIM_ENV = IPC_PORT=6010 CLI_SSH_PORT=21022 \
	NETCONF_SSH_PORT=22022 NETCONF_TCP_PORT=23022 SNMP_PORT=24022
export PYTHON

all: nso/.configured netsim/.created

$(CFS_FXS): $(wildcard $(CFS_PACKAGE)/src/yang/*.yang)
	$(MAKE) -C $(CFS_PACKAGE)/src all

$(RFS_FXS): $(wildcard $(RFS_PACKAGE)/src/yang/*.yang)
	$(MAKE) -C $(RFS_PACKAGE)/src all

nso/.configured: $(CFS_FXS) $(RFS_FXS)
	ncs-setup --no-netsim --dest nso
	sed -e 's#<port>2024</port>#<port>2124</port>#' \
	    -e 's#<port>2023</port>#<port>2123</port>#' \
	    -e 's#<port>2022</port>#<port>2122</port>#' \
	    -e 's#<port>8080</port>#<port>8180</port>#' \
	    nso/ncs.conf > nso/ncs.conf.tmp
	mv nso/ncs.conf.tmp nso/ncs.conf
	ln -s ../../$(CFS_PACKAGE) nso/packages/base-cfs
	ln -s ../../$(RFS_PACKAGE) nso/packages/base-rfs
	cp -a $(IOSXR_NED_SOURCE) nso/packages/
	touch $@

netsim/.created: nso/.configured $(NETSIM_START_SCRIPT) \
		$(DHCP_ZTP_CLIENT) $(DHCP_LEASES) $(ZTP_PAYLOAD) \
		$(ZTP_INVENTORY) $(ZTP_PRE_CONFIG) \
		$(wildcard device-config/*-ztp.cli)
	env $(NETSIM_ENV) ncs-netsim --dir ./netsim create-device \
	    ./nso/packages/$(IOSXR_NED) xr-1
	env $(NETSIM_ENV) ncs-netsim --dir ./netsim add-device \
	    ./nso/packages/$(IOSXR_NED) xr-2
	env $(NETSIM_ENV) ncs-netsim --dir ./netsim add-device \
	    ./nso/packages/$(IOSXR_NED) xr-3
	env $(NETSIM_ENV) ncs-netsim --dir ./netsim add-device \
	    ./nso/packages/$(IOSXR_NED) xr-4
	@for device in $(NETSIM_DEVICES); do \
		install -m 755 $(NETSIM_START_SCRIPT) \
		    netsim/$$device/$$device/start.sh; \
	done
	touch $@

start: start-nso

start-nso: all
	cd nso; env PATH=$(PYTHON_BIN_DIR):$$PATH $(ENV) \
	    ZTP_HTTP_ROOT=$(abspath device-config) \
	    ZTP_HTTP_PORT=$(ZTP_HTTP_PORT) \
	    sname=ztp-base-config ncs -c ncs.conf
	env $(ENV) ncs --wait-started 30

start-netsim: all
	env PYTHON=$(PYTHON) ncs-netsim --dir ./netsim --async start

stop:
	-env $(ENV) ncs --stop
	-ncs-netsim --dir ./netsim --async stop

status:
	@ncs-netsim --dir ./netsim is-alive
	@env $(ENV) ncs --status >/dev/null 2>&1 && echo "NSO: UP" || echo "NSO: DOWN"
	@$(PYTHON) -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$(ZTP_HTTP_PORT)/ztp/ztp-provision.py', timeout=1).close()" \
	    >/dev/null 2>&1 && echo "ZTP HTTP server: UP" || \
	    echo "ZTP HTTP server: DOWN"

cli:
	env $(ENV) ncs_cli -n -C -u admin

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh

clean:
	$(MAKE) -C $(CFS_PACKAGE)/src clean
	$(MAKE) -C $(RFS_PACKAGE)/src clean
	rm -rf nso netsim

.PHONY: all start start-nso start-netsim stop status cli demo clean
