all:
	$(MAKE) -C packages/cowlog/src all

pyvenv:
	python3 -m venv $@
	$@/bin/pip install -r packages/cowlog/python/requirements.txt
	echo "./$@" > packages/cowlog/python/use_venv

cowlog-deps:
	python3 -m pip install --target packages/cowlog/python/ -r packages/cowlog/python/requirements.txt

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	rm -rf logs/* state/* ncs-cdb/*
	rm -rf packages/cowlog/python/cowsay* packages/cowlog/python/bin
	rm -rf pyvenv packages/cowlog/python/use_venv

start:
	ncs

stop:
	-ncs --stop

demo:
	./demo.sh

demo-nonstop:
	NONINTERACTIVE=1 ./demo.sh
