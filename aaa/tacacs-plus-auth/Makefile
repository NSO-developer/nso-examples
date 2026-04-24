PKG_DIR = $(NCS_DIR)/packages/auth/cisco-nso-tacacs-auth

all: ncs-run/.ready

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

start:
	. pyvenv/bin/activate ; cd ncs-run && ncs

stop:
	ncs --stop || true

configure:
	ncs_load -l -m -u admin cisco-nso-tacacs-auth.xml

reset: all
	ncs-setup --reset --dest ncs-run

test:
	$(MAKE) stop
	$(MAKE) reset
	$(MAKE) start
	$(MAKE) configure
	$(MAKE) stop

clean:
	rm -rf ncs-run pyvenv

cli-c:
	ncs_cli -C -uadmin

cli-j:
	ncs_cli -J -uadmin
