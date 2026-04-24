ifneq ($(NCS_IPC_PORT),)
ENV = NCS_IPC_PORT=
else
ENV = NCS_IPC_PATH=$(or $(NCS_IPC_PATH),/tmp/nso/nso-ipc).
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
