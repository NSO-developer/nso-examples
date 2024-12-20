all:
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf logs/* state/* ncs-cdb/*.cdb

demo:
	./demo.sh
