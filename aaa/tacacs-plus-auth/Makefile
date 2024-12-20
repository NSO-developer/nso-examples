PKG_DIR = $(NCS_DIR)/packages/auth/cisco-nso-tacacs-auth

.PHONY: all
all: packages/cisco-nso-tacacs-auth cisco-nso-tacacs-auth-deps ncs.conf logs
	$(MAKE) -C packages/cisco-nso-tacacs-auth/src all

packages/cisco-nso-tacacs-auth:
	cp -a $(PKG_DIR) $@

ncs.conf:
	cp ncs.conf.example $@

logs:
	mkdir logs

pyvenv:
	virtualenv $@
	$@/bin/pip $(PIP_OPTS) install pip --upgrade


cisco-nso-tacacs-auth-deps: pyvenv
	$</bin/pip $(PIP_OPTS) install -r $(PKG_DIR)/requirements.txt
	touch $@


.PHONY: clean
clean:
	rm -rf packages/cisco-nso-tacacs-auth
	rm -rf ./netsim running.DB logs/* state/* ncs-cdb/*
	rm -rf pyvenv* __pycache__
	rm -f README.n* cisco-nso-tacacs-auth-deps ncs.conf

.PHONY: clean
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
