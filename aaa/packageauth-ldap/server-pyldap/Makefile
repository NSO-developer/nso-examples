.PHONY: all
all: ldapvenv/.ready

ldapvenv/.ready:
	python3 -m venv ldapvenv
	ldapvenv/bin/pip $(PIP_OPTS) install pip --upgrade
	ldapvenv/bin/pip $(PIP_OPTS) install -r requirements.txt
	@touch $@

.PHONY: start
start: ldapvenv/.ready
	ldapvenv/bin/python server.py >server.log & echo $$! >server.pid

.PHONY: stop
stop:
	[ -e server.pid ] && kill $$(cat server.pid) && rm server.pid || true

.PHONY: clean
clean:
	rm -rf ldapvenv server.log server.pid
