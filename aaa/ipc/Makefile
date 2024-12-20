all: use-unix

demo: demo-unix

demo-unix:
	@$(MAKE) stop clean 2>&1 1>/dev/null
	@$(MAKE) use-unix
	@$(MAKE) start
	@$(MAKE) cli-unix

demo-tcp:
	@$(MAKE) stop clean 2>&1 1>/dev/null
	@$(MAKE) use-tcp
	@$(MAKE) start
	@$(MAKE) cli-tcp

use-unix: ncs-run/.ready
	@rm -f ncs-run/ncs.conf.d/*
	@# Make local ipc path absolute
	@cat enable-unix-ipc.conf | ncs_conf_tool -e \
		"/tmp/unix-ipc.socket" ncs-local-ipc path \
		> ncs-run/ncs.conf.d/enable-unix-ipc.conf
	@echo '###'
	@echo '### Using Unix domain sockets IPC through config:'
	@echo '###'
	@cat ncs-run/ncs.conf.d/enable-unix-ipc.conf
	@echo ''
	@echo '###'
	@echo '### Accessing NSO now requires the following to be set:'
	@echo '###'
	@echo "NCS_IPC_PATH='/tmp/unix-ipc.socket'"
	@echo ''

use-tcp: ncs-run/.ready ncs-run/ipc-secret
	@rm -f ncs-run/ncs.conf.d/*
	@cat enable-ipc-key.conf | ncs_conf_tool -e \
		"$$(pwd -P)/ncs-run/ipc-secret" ncs-ipc-access-check filename \
		> ncs-run/ncs.conf.d/enable-ipc-key.conf
	@cp enable-tcp-ipc.conf ncs-run/ncs.conf.d/
	@echo '###'
	@echo '### Using TCP IPC with access check through config:'
	@echo '###'
	@cat ncs-run/ncs.conf.d/enable-tcp-ipc.conf
	@echo ''
	@cat ncs-run/ncs.conf.d/enable-ipc-key.conf
	@echo ''
	@echo '###'
	@echo '### Accessing NSO now requires the following to be set:'
	@echo '###'
	@echo "NCS_IPC_ACCESS_FILE='$$(pwd -P)/ncs-run/ipc-secret'"
	@echo ''

ncs-run/.ready:
	ncs-setup --dest ncs-run
	@ncs-setup --dest ncs-run --reset
	@mv ncs-run/ncs.conf ncs-run/ncs.conf~
	@# Remove ncs-ipc-access-check and ncs-local-ipc sections from ncs.conf
	@cat ncs-run/ncs.conf~ \
		| ncs_conf_tool -d ncs-config ncs-ipc-access-check \
		| ncs_conf_tool -d ncs-config ncs-local-ipc \
		> ncs-run/ncs.conf
	@mkdir -p ncs-run/ncs.conf.d
	cp users.xml ncs-run/ncs-cdb/
	@echo ''
	@touch $@

ncs-run/ipc-secret:
	@echo '###'
	@echo '### Generate a strong secret key for IPC'
	@echo '###'
	@mkdir -p $(dir $@)
	python3 -c 'import secrets; print(secrets.token_urlsafe(32))' >$@
	chmod 600 $@
	@echo ''

start:
	cd ncs-run && ncs

stop:
	# When using Unix sockets
	NCS_IPC_PATH=/tmp/unix-ipc.socket ncs --stop || true
	# When using TCP sockets with access check
	NCS_IPC_ACCESS_FILE=ncs-run/ipc-secret ncs --stop || true
	# Default
	ncs --stop || true

test:
	$(MAKE) stop clean
	$(MAKE) use-unix
	$(MAKE) start
	NCS_IPC_PATH=/tmp/unix-ipc.socket ncs_cmd -c get_phase
	$(MAKE) stop

	$(MAKE) stop clean
	$(MAKE) use-tcp
	$(MAKE) start
	NCS_IPC_ACCESS_FILE=ncs-run/ipc-secret ncs_cmd -c get_phase
	$(MAKE) stop

cli: cli-unix

cli-unix:
	NCS_IPC_PATH=/tmp/unix-ipc.socket ncs_cli -Cu admin

cli-tcp:
	NCS_IPC_ACCESS_FILE=ncs-run/ipc-secret ncs_cli -Cu admin

clean:
	rm -rf ncs-run
