PACKAGE_URL := https://github.com/mx-moth/flask-saml2.git
PACKAGE_VSN := 0.3.0
PACKAGE_REF := f22ab443137aee1934a73134e18a3113bbe74f11

.PHONY: all keys start start-fg stop clean

all: .ready

keys: keys/.ready

.ready: keys/.ready flask_saml2/.ready pyvenv/.ready
	@touch $@

keys/.ready:
	./keys.gen
	@touch $@

flask_saml2/.ready:
	git clone --branch $(PACKAGE_VSN) $(PACKAGE_URL) flask-saml2.git
	# Verify correct ref to avoid supply chain issues
	cd flask-saml2.git && [ "$(PACKAGE_REF)" = "$$(git rev-parse HEAD)" ]
	# Make module available for import
	ln -sf flask-saml2.git/flask_saml2 ./
	# Patch flask-saml2 to use SHA-256 by default instead of SHA-1
	cd flask_saml2 && sed -i.bak 's/Sha1/Sha256/g' idp/idp.py sp/sp.py
	@touch $@

pyvenv/.ready:
	python3 -m venv pyvenv
	pyvenv/bin/pip $(PIP_OPTS) install pip --upgrade
	pyvenv/bin/pip $(PIP_OPTS) install -r requirements.txt
	@touch $@

start: .ready
	pyvenv/bin/python ./idp.py >>server.log 2>&1 & echo $$! >server.pid

start-fg: .ready
	pyvenv/bin/python ./idp.py

stop:
	[ -e server.pid ] && kill $$(cat server.pid) && rm server.pid || true

clean:
	rm -rf keys/ flask_saml2/ flask-saml2.git/ pyvenv/ __pycache__/ \
		server.log server.pid .ready
