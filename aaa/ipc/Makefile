all: use-unix

demo: demo-unix

demo-unix:
	@$(MAKE) stop clean 2>&1 1>/dev/null
	@$(MAKE) use-unix
	@$(MAKE) start
	@$(MAKE) cli

demo-tcp:
	@$(MAKE) stop clean 2>&1 1>/dev/null
	@$(MAKE) use-tcp
	@$(MAKE) start
	@$(MAKE) cli

use-unix: ncs-run/.ready
	rm -f ncs-run/ncs.conf.d/*-ipc.conf
	cp unix-ipc.conf ncs-run/ncs.conf.d/
	@echo '###'
	@echo '### Using Unix domain sockets IPC through config:'
	@echo '###'
	@cat ncs-run/ncs.conf.d/unix-ipc.conf
	@echo ''
	@echo '###'
	@echo '### To access NSO use:'
	@echo '###'
	@cat ncs-run/unix-ipc.env
	@echo ''

use-tcp: ncs-run/.ready
	rm -f ncs-run/ncs.conf.d/*-ipc.conf
	cp tcp-ipc.conf ncs-run/ncs.conf.d/
	@echo '###'
	@echo '### Using TCP IPC with access check through config:'
	@echo '###'
	@cat ncs-run/ncs.conf.d/tcp-ipc.conf
	@echo ''
	@echo '###'
	@echo '### To access NSO use:'
	@echo '###'
	@cat ncs-run/tcp-ipc.env
	@echo ''

ncs-run/.ready: ncs-run/unix-ipc.env ncs-run/tcp-ipc.env ncs-run/ipc-secret
	ncs-setup --dest ncs-run --no-config-section ncs-ipc-access-check
	@mkdir -p ncs-run/ncs.conf.d
	cp users.xml ncs-run/ncs-cdb/
	@touch $@

ncs-run/unix-ipc.env:
	@mkdir -p $(dir $@)
	@echo "unset NCS_IPC_ADDRESS NCS_IPC_PORT NCS_IPC_ACCESS_FILE" >$@
	@echo "export NCS_IPC_PATH=/tmp/nso/nso-ipc" >>$@

ncs-run/tcp-ipc.env:
	@mkdir -p $(dir $@)
	@echo "unset NCS_IPC_PATH" >$@
	@echo "export NCS_IPC_ADDRESS=localhost" >>$@
	@echo "export NCS_IPC_PORT=4569" >>$@
	@echo "export NCS_IPC_ACCESS_FILE='$$(pwd -P)/ncs-run/ipc-secret'" >>$@

ncs-run/ipc-secret:
	@echo '###'
	@echo '### Generate a strong secret key for IPC'
	@echo '###'
	@mkdir -p $(dir $@)
	umask 0177 && python3 -c \
		'import secrets; print(secrets.token_urlsafe(32))' >$@

start:
	unset NCS_IPC_ADDRESS NCS_IPC_PORT NCS_IPC_ACCESS_FILE NCS_IPC_PATH ;\
		cd ncs-run && ncs
	@echo ''

stop:
	[ -e ncs-run/unix-ipc.env ] && . ncs-run/unix-ipc.env && ncs --stop || true
	[ -e ncs-run/tcp-ipc.env ] && . ncs-run/tcp-ipc.env && ncs --stop || true

test:
	$(MAKE) stop clean
	$(MAKE) use-unix
	$(MAKE) start
	. ncs-run/unix-ipc.env && ncs_cmd -c get_phase
	$(MAKE) stop

	$(MAKE) stop clean
	$(MAKE) use-tcp
	$(MAKE) start
	. ncs-run/tcp-ipc.env && ncs_cmd -c get_phase
	$(MAKE) stop

cli:
	@[ ! -e ncs-run/ncs.conf.d/unix-ipc.conf ] || . ncs-run/unix-ipc.env ;\
		[ ! -e ncs-run/ncs.conf.d/tcp-ipc.conf ] || . ncs-run/tcp-ipc.env ;\
		export | grep NCS_IPC_ ; set -x ; ncs_cli -C -u admin

clean:
	rm -rf ncs-run
