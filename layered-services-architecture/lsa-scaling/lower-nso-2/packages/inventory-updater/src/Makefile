NS  = namespaces

YANGPATH = --yangpath ./yang

all:
	(cd ../erlang-lib/ec_inventory_updater/src ; $(MAKE) all)

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

clean:
	(cd ../erlang-lib/ec_inventory_updater/src ; $(MAKE) clean)
	rm -rf ncsc-out/* ../load-dir/* ncsc-out/*

TARGET_PACKAGE_NAME=stash-inventory-updater-1.0.tgz
PACKAGE_FOLDER=inventory-updater
delivery:
	mkdir -p ../build
	tar -czvh --exclude '.git' --exclude '.gitkeep' --exclude '.settings' --exclude 'build' --exclude 'doc' --exclude 'src'   --exclude 'test' -C ../..  -f ../build/$(TARGET_PACKAGE_NAME) $(PACKAGE_FOLDER)
