NCS_NAME=nso-srv
all:

start app-start:
	export NCS_JAVA_VM_OPTIONS="-Xmx500M"; \
	sname=$(NCS_NAME) ncs --with-package-reload $(NCS_FLAGS)
	./init.sh

stop app-stop:
	ncs --stop >/dev/null 2>&1; true

cli:
	ncs_cli -u admin

cli-j: cli
cli-c:
	ncs_cli -C -u admin

status:
	@ncs --status > /dev/null 2>&1; \
	if [ $$? = 0 ]; then echo "$(NCS_NAME): UP"; \
            else echo "$(NCS_NAME): ERR"; fi

clean reset: stop
	@ncs-setup --reset

.PHONY: all init start stop cli status reset clean
