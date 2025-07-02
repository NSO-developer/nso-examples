PKG_DIR = $(NCS_DIR)/packages/auth/cisco-nso-ldap-auth
DOCKER_CONTAINER_NAME ?= nso-ldap-example
DOCKER_OPTS ?= --ulimit nofile=1024
SLAPD_OPTS ?= -d 1 -h "ldap:/// ldapi:///"

.PHONY: all
all: packages/cisco-nso-ldap-auth cisco-nso-ldap-auth-deps pyvenv \
     build-container
	$(MAKE) -C packages/cisco-nso-ldap-auth/src all

packages:
	mkdir packages

packages/cisco-nso-ldap-auth: packages
	cp -a $(PKG_DIR) $@

pyvenv:
	python3 -m venv $@
	$@/bin/pip $(PIP_OPTS) install pip --upgrade

cisco-nso-ldap-auth-deps: pyvenv
	$</bin/pip $(PIP_OPTS) install -r $(PKG_DIR)/requirements.txt
	touch $@

.PHONY: build-container
build-container: Dockerfile
	docker build $(DOCKER_OPTS) -t $(DOCKER_CONTAINER_NAME) .

.PHONY: start-container
start-container:
	# Run OpenLDAP in the foreground
	docker run -d -p 1389:389 $(DOCKER_OPTS) --name $(DOCKER_CONTAINER_NAME) \
		$(DOCKER_CONTAINER_NAME) slapd $(SLAPD_OPTS)

start-container-fg:
	# Run OpenLDAP in the foreground
	docker run -p 1389:389 $(DOCKER_OPTS) --name $(DOCKER_CONTAINER_NAME) \
		$(DOCKER_CONTAINER_NAME) slapd $(SLAPD_OPTS)

.PHONY: stop-container
stop-container:
	docker rm -f $(DOCKER_CONTAINER_NAME) || true

.PHONY: clean
clean:
	rm -rf ncs-cdb netsim logs packages scripts state
	rm -rf pyvenv* __pycache__
	rm -f README.n* cisco-nso-ldap-auth-deps ncs.conf

.PHONY: clean-logs reset
clean-logs:
	rm -f logs/*

ncs.conf: ncs.conf.example
	ncs-setup --dest .
	cp $< $@

.PHONY: start
start: pyvenv start-container ncs.conf
	(. pyvenv/bin/activate; NCS_LOG_DIR=$(PWD)/logs ncs -c ./ncs.conf)

.PHONY: stop
stop: stop-container
	-ncs --stop

.PHONY: cli-c
cli-c:
	ncs_cli -C -uadmin

.PHONY: cli-j
cli-j:
	ncs_cli -J -uadmin
