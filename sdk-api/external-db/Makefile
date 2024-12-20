all:
	$(MAKE) -C packages/extern-db/src all

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf running.DB logs/* state/* ncs-cdb/*

start:
	ncs

stop:
	-ncs --stop
	@echo "##### All NSO instances stopped"

demo:
	./demo.sh
