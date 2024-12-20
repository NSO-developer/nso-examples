all clean:
	for p in packages/*; do \
		$(MAKE) -C $$p/src $@ || exit 1; \
	done

reset:
	ncs --stop || true
	rm -rf state/* logs/* ncs-cdb/*

demo:
	./demo.sh
