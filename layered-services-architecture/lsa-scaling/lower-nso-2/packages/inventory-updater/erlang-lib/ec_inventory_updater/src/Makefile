include ../vsn.mk

APP_NAME   = ec_inventory_updater
ERL_FILES  = $(wildcard *.erl)
MODULES    = $(ERL_FILES:%.erl=%)
EBIN_FILES = $(MODULES:%=../ebin/%.beam) ../ebin/$(APP_NAME).app

ERLC       = erlc

# This Perl script can be replaced by support/app_script.py with same invocation
APPSCRIPT = '$$vsn=shift; $$mods=""; while(@ARGV){ $$_=shift; \
	s/^([A-Z].*)$$/\'\''$$1\'\''/; $$mods.=", " if $$mods; \
	$$mods .= $$_; } while(<>) { s/%VSN%/$$vsn/; \
	s/%MODULES%/$$mods/; print; }'


ifeq ($(TYPE),debug)
DEBUG_FLAGS = -Ddebug
else
DEBUG_FLAGS =
endif

LOAD_FILES=$(MODULES:%=../../../load-dir/%.$(EMULATOR))

NCS_VERSION = $(shell ncs --version | cut -d "." -f 1,2 -)

ifeq "$(NCS_VERSION)" "3.3"
 ERLC_FLAGS+=-I ../include_pre_3.4
else
 ERLC_FLAGS+=-I ../include_post_3.4
endif

ERLC_FLAGS+=-W +debug_info $(DEBUG_FLAGS) \
	 -I $(NCS_DIR)/erlang/econfd/include -I ../include

all: $(EBIN_FILES)
	@echo $(EBIN_FILES)

#
# Targets
#

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

../ebin/%.app: %.app.src ../vsn.mk Makefile
	perl -e $(APPSCRIPT) "$(VSN)" $(MODULES) < $< > $@

../ebin/%.beam: %.erl
	$(ERLC) $(ERLC_FLAGS) -o ../ebin $<

$(EBIN_FILES): $(ERL_FILES) ../ebin

../ebin:
	-mkdir ../ebin

clean:
	rm -f $(EBIN_FILES) erl_crash.dump core* *~
