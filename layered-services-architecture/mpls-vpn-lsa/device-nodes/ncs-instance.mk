PORT = $(shell xmllint --xpath \
"//*[local-name()='ncs-ipc-address']/*[local-name()='port']/text()" ncs.conf)

ifeq ($(PORT),)
$(error "Could not find ncs ipc port. Is xmllint installed?")
endif

ifeq ($(NCS_IPC_PATH),)
ENV = NCS_IPC_PORT=$(PORT)
else
ENV = NCS_IPC_PATH=$(NCS_IPC_PATH).$(PORT)
endif

all:

start app-start:
	export NCS_JAVA_VM_OPTIONS="-Xmx500M";
	  $(ENV) sname=$(NCS_NAME) ncs --with-package-reload $(NCS_FLAGS)
	$(ENV) ../init.sh

stop app-stop:
	@$(ENV) ncs --stop >/dev/null 2>&1; true

cli:
	$(ENV) ncs_cli -u admin

cli-j: cli
cli-c:
	$(ENV) ncs_cli -C -u admin

status:
	@$(ENV) ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then echo "$(NCS_NAME): UP"; \
            else echo "$(NCS_NAME): ERR"; fi

clean reset: stop
	@ncs-setup --reset


.PHONY: all init start stop cli status reset clean
