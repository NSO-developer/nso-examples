NCS_NAME=nso-srv

ifeq ($(NCS_IPC_PATH),)
ENV = NCS_IPC_PORT=
else
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH).
endif

all:

start app-start:
	export NCS_JAVA_VM_OPTIONS="-Xmx500M"; \
	env $(ENV)4569 sname=$(NCS_NAME) ncs --with-package-reload $(NCS_FLAGS)
	env $(ENV)4569 ./init.sh

stop app-stop:
	@env $(ENV)4569 ncs --stop >/dev/null 2>&1; true

cli:
	@env $(ENV)4569 ncs_cli -u admin

cli-j: cli
cli-c:
	@env $(ENV)4569 ncs_cli -C -u admin

status:
	@env $(ENV)4569 ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then echo "$(NCS_NAME): UP"; \
            else echo "$(NCS_NAME): ERR"; fi

clean reset: stop
	@ncs-setup --reset

.PHONY: all init start stop cli status reset clean
