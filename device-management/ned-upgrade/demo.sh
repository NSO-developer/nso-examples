#!/bin/sh

set -eu
. helpers/utility.sh


show_title "NED update example"


next_step "Reset and start the example"
exec_shell make stop clean
exec_shell make all run

next_step "Perform initial sync-from"
exec_cli "devices sync-from"

next_step "Deploy the acme-dns service to all devices"
exec_cli "\
config
acme-dns devices [ ex0 ex1 ex2 ] default-search-domain acme.example.com
commit"

next_step "Inspect the NED devices are currently using"
exec_cli_np "show devices list"

next_step "Inspect the service-produced configuration"
exec_cli_np "acme-dns get-modifications"

next_step "Add an updated NED to the packages/ directory"
exec_shell ln -s ../files/router-nc-1.1 packages/

next_step "Load the new NED"
exec_cli "packages add"
echo_bold "
Note: The 'packages add' action will fail if any existing packages are
      modified or deleted. Moreover, adding a NED package that uses a
      modified shared data model is not allowed and will also fail
      (see product documentation for details)."

next_step "Inspect device model changes"
diff -U2 packages/router-nc-1.0/src/yang \
    packages/router-nc-1.1/src/yang || true
echo "
Alternatively, you can use the 'migrate dry-run' action to only list the
device model changes that affect your existing configurations."
exec_cli "devices migrate old-ned-id router-nc-1.0 new-ned-id router-nc-1.1\
 dry-run suppress-modified-paths without-instance-data verbose"
echo "
In the new NED, the 'domain' leaf has changed into 'search' leaf-list.
This is a backward incompatible change and requires special provisions
in the service to work with both versions of the NED."

next_step "Update service XML template"
echo "
The service template will need the 'if-ned-id' processing instruction
to handle this case, so it can work with both versions. For example:
"
diff -U2 files/acme-dns.xml files/acme-dns-new.xml || true
echo ""

next_step "Redeploy the service package to use the new template"
exec_shell cp files/acme-dns-new.xml packages/acme-dns/templates/acme-dns.xml
exec_cli "packages package acme-dns redeploy"

next_step "Migrate all the devices to use the new NED"
exec_cli "devices migrate old-ned-id router-nc-1.0 new-ned-id router-nc-1.1"

next_step "Re-deploy the affected service instances"
echo_bold "
Note: It is important to re-deploy all affected services touching the
      device after the device migration, even though there are no backwards
      incompatible data model changes affecting the service."
exec_cli "\
acme-dns re-deploy dry-run | nomore
acme-dns re-deploy"

next_step "Verify the new service-produced configuration"
exec_cli_np "acme-dns get-modifications"

next_step "Finally verify the new device configuration is correct"
exec_cli_np "show running-config devices device ex0..2 config sys dns"

show_done
