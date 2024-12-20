include $(NCS_DIR)/src/ncs/build/include.netsim.mk

## If you need additional init xml files, put them in
## the current directory, and they will be picked up here.

XML = $(wildcard *.xml)
XML += $(wildcard *.xml.gz)
XML += $(wildcard *.xml.Z)
XML += $(NCS_DIR)/netsim/confd/var/confd/cdb/aaa_init.xml

EXTRA_FILES = confd.conf.netsim

ALL_MIBS    = $(wildcard ../src/mibs/*.mib)
STD_MIB_FXS = $(wildcard $(NCS_DIR)/netsim/confd/etc/confd/snmp/*.fxs)
STD_MIBS    = $(STD_MIB_FXS:$(NCS_DIR)/netsim/confd/etc/confd/snmp/%.fxs=../src/mibs/%.mib)
MIBS        = $(filter-out $(STD_MIBS), $(ALL_MIBS))
FXS         = $(MIBS:../src/mibs/%.mib=%.fxs)
BIN         = $(MIBS:../src/mibs/%.mib=%.bin)
PRGS        = validate

EXTRA_FILES += $(PRGS)

all: 
	$(MAKE) $(FXS) $(BIN) $(PRGS)

%.yang: ../src/mibs/%.mib
	$(CONFDC) --mib2yang $< > $@

%.fxs: %.yang
	a=$*-ann.yang; \
	$(CONFDC) --yangpath $(CONFD_DIR)/src/confd/snmp/yang \
	  -c -o $@  `[ -e $$a ] && echo "-a $$a"` $<

%.bin:  %.fxs
	$(CONFDC) -c ../src/mibs/$*.mib  \
	$*.fxs -f . -f $(CONFD_DIR)/etc/confd -f $(CONFD_DIR)/etc/confd/snmp

%.fxs:  ../src/yang/%.yang
	$(CONFDC) $(FLAGS) \
          `ls ../src/yang/$*-ann.yang  > /dev/null 2>&1 && \
              echo "-a ../src/yang/$*-ann.yang"` \
          -c -o $@  $<

validate: validate.o
	$(CC) validate.o $(LIBS) $(CFLAGS) -o $@

validate.o: validate.c BASIC-CONFIG-MIB.h

# Invoked by netsim, who will sed substitute the %var% PORT variables
# in the confd.conf.netsim file
install:
	@if [ -z "$${DEST_DIR}" ]; then echo "No DEST_DIR  var"; exit 1; fi
	@if [ ! -d "$${DEST_DIR}" ]; then "echo No DEST_DIR"; exit 1; fi
	mkdir $${DEST_DIR}/cdb 2>/dev/null || true
	mkdir $${DEST_DIR}/logs 2>/dev/null || true
	for i in $(XML); do \
		sed -e 's/%NAME%/$(NAME)/g' -e 's/%COUNTER%/$(COUNTER)/g' -e 's/%TWO_DIGIT_COUNTER%/$(TWO_DIGIT_COUNTER)/g' \
		  $$i > $${DEST_DIR}/cdb/`basename $$i`; \
	done
	cp -f $(FXS) $${DEST_DIR}
	cp -f $(EXTRA_FILES) $${DEST_DIR}
	cp -af $(NCS_DIR)/netsim/confd/etc/confd/ssh $${DEST_DIR}
	cp -f $(BIN) $${DEST_DIR}
	@echo '<SNMP-USER-BASED-SM-MIB xmlns="http://tail-f.com/ns/mibs/SNMP-USER-BASED-SM-MIB/200210160000Z"><usmUserTable>' > $${DEST_DIR}/cdb/usm_init.xml
	- # create a user 'admin' with passphrase 'admin'
	$(CONFDC) --snmpuser 80:00:61:81:05:$(TWO_DIGIT_COUNTER) admin sha aes admin \
	  >> $${DEST_DIR}/cdb/usm_init.xml
	@echo '</usmUserTable></SNMP-USER-BASED-SM-MIB>' >> $${DEST_DIR}/cdb/usm_init.xml

clean:  
	@rm *.fxs 2>  /dev/null || true
	@rm -rf $(YANG) $(BIN) $(PRGS) BASIC-CONFIG-MIB.h *.o
