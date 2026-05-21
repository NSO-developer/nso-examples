all: ncs-run/.ready jwt

.PHONY: all start stop configure reset test clean cli-c cli-j

ncs-run/.ready: aaa.conf pyvenv/.ready
	ncs-setup --dest ncs-run --no-config-section aaa --use-copy \
		--package packages/nso-example-jwt-auth
	@mkdir -p ncs-run/ncs.conf.d
	cp aaa.conf ncs-run/ncs.conf.d/
	for f in ncs-run/packages/*/src; do \
		$(MAKE) -C $$f || exit 1; \
	done
	@touch $@

jwt: pyvenv/.ready
	pyvenv/bin/python mkjwt.py < jwt.txt > jwt

pyvenv/.ready:
	python3 -m venv pyvenv
	pyvenv/bin/pip $(PIP_OPTS) install pip --upgrade
	pyvenv/bin/pip $(PIP_OPTS) install -r requirements.txt
	@touch $@

start:
	. pyvenv/bin/activate ; cd ncs-run && ncs

stop:
	ncs --stop || true

configure:
	ncs_load -l -m -u admin -F c config.example

reset: all
	ncs-setup --reset --dest ncs-run

test:
	$(MAKE) all
	$(MAKE) start
	$(MAKE) configure
	sed 's/.*\.\(.*\)\..*/\1=/' < jwt | base64 -d | python -m json.tool
	curl -isfS -H "Authorization: Bearer $$(cat jwt)" \
		http://localhost:8080/restconf
	$(MAKE) stop

clean:
	rm -rf ncs-run pyvenv jwt

cli-c:
	ncs_cli -C -uadmin

cli-j:
	ncs_cli -J -uadmin
