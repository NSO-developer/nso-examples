include ../vsn.mk

APP_DIR    = $(shell dirname $(PWD))
APP_NAME   = $(shell basename $(APP_DIR))
ERL_FILES  = $(wildcard *.erl)
HRL_FILES  = $(wildcard *.hrl) $(wildcard ../*.hrl)
MODULES    = $(ERL_FILES:%.erl=%)
EBIN_FILES = $(MODULES:%=../ebin/%.beam) ../ebin/$(APP_NAME).app

ERLC       = erlc
ERLC       = $(NCS_DIR)/bin/erlc
ERLC_FLAGS := \
	+warnings_as_errors \
	+debug_info \
        -I $(NCS_DIR)/erlang/econfd/include \
	-pa $(NCS_DIR)/erlang/econfd/ebin \
	-pa ../../ec_vlan/ebin


# This Perl script can be replaced by support/app_script.py with same invocation
APPSCRIPT = '$$vsn=shift; $$mods=""; while(@ARGV){ $$_=shift; s/^([A-Z].*)$$/\'\''$$1\'\''/; $$mods.=", " if $$mods; $$mods .= $$_; } while(<>) { s/%VSN%/$$vsn/; s/%MODULES%/$$mods/; print; }'

all: $(NCS_DIR)/erlang/econfd/ebin $(EBIN_FILES)

$(NCS_DIR)/erlang/econfd/ebin:
	@echo 'Where is NCS installed? Set $$NCS_DIR to point it out!'
	exit 1

clean:
	rm -f $(EBIN_FILES) erl_crash.dump core* *~

../ebin/%.app: %.app.src ../vsn.mk Makefile
	perl -e $(APPSCRIPT) "$(VSN)" $(MODULES) < $< > $@

../ebin/%.beam: %.erl
	$(ERLC) $(ERLC_FLAGS) -o ../ebin $<

$(EBIN_FILES): $(ERL_FILES) $(HRL_FILES)
