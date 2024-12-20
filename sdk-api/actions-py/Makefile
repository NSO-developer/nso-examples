all:
	$(MAKE) -C packages/actions/src all
	cp logging.xml ncs-cdb

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf logs/* state/* ncs-cdb/*

start:
	ncs

stop:
	-ncs --stop

demo:
	./demo.sh
