TOPTARGETS := all clean

ALL_SUBDIRS := $(wildcard ./*/src)
JUNOS_SUBDIRS := $(filter ./juniper-junos%/src, $(ALL_SUBDIRS))

ifeq ($(SKIP_JUNOS_NED),true)
    $(info ==========================================================)
    $(info SKIP_JUNOS_NED=true: Skipping juniper-junos NED(s).)
    $(info ==========================================================)
    SUBDIRS := $(filter-out $(JUNOS_SUBDIRS), $(ALL_SUBDIRS))
else
    SUBDIRS := $(ALL_SUBDIRS)
endif

$(TOPTARGETS): $(SUBDIRS)
$(SUBDIRS):
	$(MAKE) -C $@ $(MAKECMDGOALS)

.PHONY: $(TOPTARGETS) $(SUBDIRS)
