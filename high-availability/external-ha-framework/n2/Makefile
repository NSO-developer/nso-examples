ifeq ($(NCS_IPC_PATH),)
ENV = NCS_IPC_PORT=
else
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH).
endif

start:
	env $(ENV)5758 sname=n2 NCS_HA_NODE=n2 ncs -c ncs.conf

start-i:
	env $(ENV)5758 sname=n2 NCS_HA_NODE=n2 ncs -c ncs.conf -i \
	--with-package-reload

stop:
	env $(ENV)5758 sname=n2 NCS_HA_NODE=n2 ncs --stop

cli:
	env $(ENV)5758 ncs_cli -u admin
