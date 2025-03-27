.PHONY: all
all: build start

.PHONY: demo
demo: all webui

.PHONY: build
build:
	ncs-setup --dest .

clean: stop
	for f in packages/*/src; do \
		$(MAKE) -C $${f} clean || exit 1; \
	done
	-rm -rf README.ncs logs ncs-cdb ncs.conf scripts state

.PHONY: start
start:
	ncs

.PHONY: stop
stop:
	ncs --stop || true

.PHONY: webui
webui:
	open http://localhost:8080

.PHONY: redeploy-package
redeploy-package:
	ncs_cli -u admin <<< 'request packages package webui-basic-example redeploy'

.PHONY: redeploy-package-watch
redeploy-package-watch:
	@command -v entr >/dev/null 2>&1 || { \
		echo >&2 "entr command not found. Please install entr to use this target."; \
		exit 1; \
	}
	find ./packages/webui-basic-example/webui -name '*' | \
	entr sh -c "ncs_cli -u admin <<< 'request packages package webui-basic-example redeploy'"
