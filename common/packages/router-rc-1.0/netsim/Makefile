EXTRA_FILES = scripts/archive-log
include $(NCS_DIR)/src/ncs/build/include-netsim-ned-src.mk

# install and install-main-netsim are defined in the included makefile
# anything that should be installed in addition - should be a target
# that install depends on, if anything from the usual netsim is needed
# the additional install target should depend on 'install-main-netsim'
# here we need confd.conf.netsim from the usual netsim
install-restconf-conf: install-main-netsim
	@RESTCONF_IPC_PORT=$$(( 18022 + $(COUNTER) )); \
	sed -i.bak 's|</confdConfig>|<restconf><enabled>true</enabled></restconf><webui><enabled>true</enabled><transport><tcp><enabled>false</enabled></tcp><ssl><enabled>true</enabled><port>'"$$RESTCONF_IPC_PORT"'</port></ssl></transport></webui></confdConfig>|' $${DEST_DIR}/confd.conf.netsim && rm -f $${DEST_DIR}/confd.conf.netsim.bak

install: install-restconf-conf
