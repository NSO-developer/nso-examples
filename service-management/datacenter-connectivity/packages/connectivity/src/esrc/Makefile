
ERLC       = $(NCS_DIR)/bin/erlc
ERLC_FLAGS = -I $(NCS_DIR)/erlang/econfd/include

all: build
	cp if_complete.beam ../../load-dir


## manual build step, we have checked in the beam file
## to make life easier in the release

build:
	$(ERLC) $(ERLC_FLAGS) if_complete.erl
	cp if_complete.beam ../../load-dir
