######################################################################
# Example showing use of Nano Services for Staged Provisioning of a
# Virtual Router
#
# See the README file for more information
######################################################################
CDB_DIR       := ncs-cdb
INITS         := $(wildcard *_init.xml)
CDB_INITS     := $(addprefix $(CDB_DIR)/,$(INITS))

all: $(CDB_INITS)
	for f in packages/*/src; do \
		$(MAKE) -C $$f all || exit 1; \
	done

$(CDB_DIR)/%.xml: %.xml
	rm -f $@ ; cp $< $@

clean:
	for f in packages/*/src; do \
		$(MAKE) -C $$f clean || exit 1; \
	done
	-rm -rf logs/* state/* ncs-cdb/*

start:
	ncs

stop:
	-ncs --stop

demo:
	./demo.sh

demo-rc:
	python3 demo_rc.py
