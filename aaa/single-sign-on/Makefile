PKG_DIR = $(NCS_DIR)/packages/auth/cisco-nso-saml2-auth

.PHONY: all
all: packages/cisco-nso-saml2-auth \
		cisco-nso-saml2-auth.xml \
		cisco-nso-saml2-auth-deps \
		flask-saml2
	$(MAKE) -C packages/cisco-nso-saml2-auth/src all

packages/cisco-nso-saml2-auth:
	cp -a $(PKG_DIR) $@

pyvenv:
	python3 -m venv $@
	$@/bin/pip $(PIP_OPTS) install pip --upgrade

pyvenv.idp:
	python3 -m venv $@
	$@/bin/pip $(PIP_OPTS) install pip --upgrade

flask-saml2: pyvenv.idp
	git clone https://github.com/mx-moth/flask-saml2.git
	# Patch flask-saml2 to use SHA-256 by default instead of SHA-1
	(cd flask-saml2/flask_saml2; sed -i.bak 's/Sha1/Sha256/g' idp/idp.py sp/sp.py)
	(cd flask-saml2; ../$</bin/pip $(PIP_OPTS) install -e .)
	$</bin/pip $(PIP_OPTS) install -r flask-saml2/tests/requirements.txt
	# pyOpenSSL 22.0.0 is needed on platforms with OpenSSL 3.0.
	# Note that flask-saml2 requires pyOpenSSL < 18, but works with newer.
	# pyOpenSSL <24.3 is needed because crypto.sign is removed in later ver.
	$</bin/pip $(PIP_OPTS) install "pyopenssl<24.3" --upgrade

cisco-nso-saml2-auth-deps: pyvenv
	$</bin/pip $(PIP_OPTS) install -r $(PKG_DIR)/requirements.txt
	touch $@

keys:
	./$@.gen

cisco-nso-saml2-auth.xml: keys
	./$@.gen

.PHONY: start-idp
start-idp: flask-saml2
	(. pyvenv.idp/bin/activate ; ./idp.py )

.PHONY: clean
clean:
	$(MAKE) -C packages/cisco-nso-saml2-auth/src clean || true
	rm -rf ./netsim logs/* state/* ncs-cdb/*
	rm -rf pyvenv* keys flask-saml2 __pycache__
	rm -f README.n* ncs.conf
	rm -f cisco-nso-saml2-auth-deps cisco-nso-saml2-auth.xml

.PHONY: clean
clean-logs:
	rm -f logs/*

.PHONY: start
start:
	ncs

.PHONY: stop
stop:
	-ncs --stop

.PHONY: cli-c
cli-c:
	ncs_cli -C -uadmin

.PHONY: cli-j
cli-j:
	ncs_cli -J -uadmin
