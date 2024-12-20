all:
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done
	$(MAKE) netsim

netsim:
	ncs-netsim create-network ./packages/router-nc-1.1 3 ex --dir ./netsim
	ncs-netsim ncs-xml-init > ncs-cdb/devices.xml

showcase-clean-start:
	rm -rf ./netsim
	$(MAKE) all
	ncs-netsim stop >/dev/null
	ncs-netsim start
	@if [ -n '$(NSO_RUNDIR)' ] ; then \
		cp ncs-cdb/ncs_init.xml '$(NSO_RUNDIR)/ncs-cdb/'; \
		cp -a ./packages/router-nc-1.1 '$(NSO_RUNDIR)/packages/' || true; \
	else \
		echo "NSO_RUNDIR not set, skipping onboarding..."; \
	fi

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $${f} clean || exit 1; \
	done
	rm -rf netsim logs/* state/* ncs-cdb/* ssh-keydir

demo:
	./demo.sh
