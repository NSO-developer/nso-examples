all: pyvenv
	$(MAKE) -C packages/nso-example-jwt-auth/src all

pyvenv:
	virtualenv $@
	$@/bin/pip $(PIP_OPTS) install -r requirements.txt

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf ./netsim logs/* state/* ncs-cdb/* scripts pyvenv
	rm -f ncs-cdb/logging.xml  README.n*

start:
	ncs

stop:
	-ncs --stop

cli-c:
	ncs_cli -C -uadmin

cli-j:
	ncs_cli -J -uadmin
