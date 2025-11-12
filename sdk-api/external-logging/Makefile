PACKAGE_STORE=$(NCS_DIR)/examples.ncs/common/packages

all:	ncs.conf

netsim:
	make -C $(PACKAGE_STORE)/cisco-ios-netsim-cli-1.0/src all
	ncs-netsim create-network $(PACKAGE_STORE)/cisco-ios-netsim-cli-1.0 2 ios

ncs.conf: netsim
	ncs-setup --dest .
# replace the syslog-config with external log configuration setting
# the command to ./log_filter.py
	ncs_conf_tool -R "<external> \
    <enabled>true</enabled> \
    <command>./log_filter.py</command> \
  </external>" ncs-config logs syslog-config < ncs.conf > ncs.conf.tmp
# replace the snmp-log with ext-log configuration
	ncs_conf_tool -R "<ext-log> \
    <enabled>true</enabled> \
    <filename>./logs/ext-log.log</filename> \
  </ext-log>" ncs-config logs snmp-log < ncs.conf.tmp > ncs.conf
	rm ncs.conf.tmp

reset:
	ncs-setup --reset
	ncs-netsim reset

clean:
	$(RM) README.ncs README.netsim ncs.conf storedstate ./*.log
	$(RM) -r logs netsim packages state ncs-cdb scripts
	$(RM) -f ned-cisco-ios-netsim-cli-1.0-ios1_filtered.trace

start:
	ncs-netsim start
	ncs

stop:
	-ncs --stop
	ncs-netsim stop

clean-logs:
	$(RM) logs/ned-cisco-ios-netsim-cli-1.0-ios0.trace \
	      filtered_ned-cisco-ios-netsim-cli-1.0-ios1.trace

grep-logs:
	@echo "logs/ned-cisco-ios-netsim-cli-1.0-ios0.trace" && \
	grep '^banner motd' logs/ned-cisco-ios-netsim-cli-1.0-ios0.trace && \
	echo "ned-cisco-ios-netsim-cli-1.0-ios1_filtered.trace" && \
	grep '^banner motd' ned-cisco-ios-netsim-cli-1.0-ios1_filtered.trace

demo:
	./demo.sh
