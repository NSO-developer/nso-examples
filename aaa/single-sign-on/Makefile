PKG_DIR = $(NCS_DIR)/packages/auth/cisco-nso-saml2-auth

all: ncs-run/.ready cisco-nso-saml2-auth.xml

.PHONY: all start stop configure reset test clean cli-c cli-j

ncs-run/.ready: aaa.conf pyvenv/.ready
	ncs-setup --dest ncs-run --no-config-section aaa --use-copy \
		--package $(PKG_DIR)
	@mkdir -p ncs-run/ncs.conf.d
	cp aaa.conf ncs-run/ncs.conf.d/
	for f in ncs-run/packages/*/src; do \
		$(MAKE) -C $$f || exit 1; \
	done
	@touch $@

pyvenv/.ready:
	python3 -m venv pyvenv
	pyvenv/bin/pip $(PIP_OPTS) install pip --upgrade
	pyvenv/bin/pip $(PIP_OPTS) install -r $(PKG_DIR)/requirements.txt
	@touch $@

cisco-nso-saml2-auth.xml:
	$(MAKE) -C idp keys
	./cisco-nso-saml2-auth.xml.gen

start:
	. pyvenv/bin/activate ; cd ncs-run && ncs

.PHONY: start-idp
start-idp:
	$(MAKE) -C idp start

stop:
	ncs --stop || true
	$(MAKE) -C idp stop

configure:
	ncs_load -l -m -u admin cisco-nso-saml2-auth.xml

reset: all
	ncs-setup --reset --dest ncs-run

test:
	$(MAKE) stop
	$(MAKE) reset
	$(MAKE) start start-idp
	$(MAKE) configure
	sleep 1
	curl -isfS http://localhost:8080/sso/saml/metadata/
	curl -isfS http://localhost:8080/sso/saml/login/
	$(MAKE) stop

clean:
	rm -rf ncs-run pyvenv
	rm -f cisco-nso-saml2-auth.xml
	$(MAKE) -C idp clean

cli-c:
	ncs_cli -C -uadmin

cli-j:
	ncs_cli -J -uadmin
