all:
	for p in */src; do $(MAKE) -C $$p || exit 1; done

stop:
	# pass

test:
	# pass

clean:
	for p in */src; do $(MAKE) -C $$p clean; done
