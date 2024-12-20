PKG_DIR = $(NCS_DIR)/packages/auth/cisco-nso-ldap-auth

.PHONY: all
all: packages/cisco-nso-ldap-auth cisco-nso-ldap-auth-deps logs pyvenv
	$(MAKE) -C packages/cisco-nso-ldap-auth/src all

packages/cisco-nso-ldap-auth:
	cp -a $(PKG_DIR) $@

logs:
	mkdir logs

pyvenv:
	virtualenv $@
	$@/bin/pip $(PIP_OPTS) install pip --upgrade

cisco-nso-ldap-auth-deps: pyvenv
	$</bin/pip $(PIP_OPTS) install -r $(PKG_DIR)/requirements.txt
	touch $@

.PHONY: clean
clean:
	rm -rf packages/cisco-nso-ldap-auth
	rm -rf ./netsim logs/* state/* ncs-cdb/*
	rm -rf pyvenv* __pycache__
	rm -f README.n* cisco-nso-ldap-auth-deps

.PHONY: clean-logs reset
clean-logs:
	rm -f logs/*

.PHONY: start
start:
	ncs -c ./ncs.conf

.PHONY: stop
stop:
	-ncs --stop

.PHONY: cli-c
cli-c:
	ncs_cli -C -uadmin

.PHONY: cli-j
cli-j:
	ncs_cli -J -uadmin
