#!/bin/sh

set -eu
. helpers/utility.sh


show_title "NED migration example"


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

next_step "Inspect service meta-data for a sample device"
exec_cli_np "show running-config devices device ex0 config sys dns\
 | display service-meta-data"
echo "
This data links the device configuration back to the service that created it.
Other devices have the same configuration, including meta-data, since they
were created by the same service.
"

next_step "Preview migrate of a single device to a new NED with dry-run"
exec_cli "devices device ex0 migrate new-ned-id router-nc-1.1\
 suppress-modified-paths without-instance-data verbose dry-run"
echo "
The output shows that the 'sys/dns/domain' leaf is removed in the new NED.

Using the 'suppress-modified-paths without-instance-data' allows you to
ignore changes in the NED that do not affect your configuration, while
'verbose' produces a list of service instances that are affected.
Additionally, if the no-networking option is used, no southbound
traffic is generated towards the devices; only the device
configuration in the CDB is used for the migration."
echo_bold "
Note: The 'affected-services' list is a best-effort estimate from NSO
      and should not be relied upon to always accurately find all the
      affected services. This is a known limitation in the current
      implementation."

next_step "Inspect device model changes"
diff -U2 packages/router-nc-1.0/src/yang \
    packages/router-nc-1.1/src/yang || true
echo "
In the new NED, the 'domain' leaf has changed into 'search' leaf-list.
This is a backward incompatible change and requires special provisions
in the service to work with both versions of the NED.
"

next_step "Inspect service XML template"
cat packages/acme-dns/templates/acme-dns.xml
echo "
The service template already uses 'if-ned-id' processing instruction
to handle this case, so it can work with both versions.
"

next_step "Migrate a single device to the new NED"
exec_cli "devices device ex0 migrate new-ned-id router-nc-1.1\
 suppress-modified-paths without-instance-data"

next_step "Inspect configuration for the affected service"
exec_cli_np "acme-dns get-modifications"

next_step "Show missing device configuration"
exec_cli_np "acme-dns re-deploy dry-run"
echo "
The service is missing the 'search' (previously 'domain') configuration
on the migrated ex0 device.
"

next_step "Re-deploy the affected services"
echo_bold "
Note: It is important to re-deploy all affected services touching the
      device after the device migration, even though there are no backwards
      incompatible data model changes affecting the service. When reading
      the reverse/forward diffset of a service, NSO will detect changes to
      the NED identity of a device touched by the service and migrate the
      diffset on the fly. Thus the diffsets are still valid, but until the
      new diffset is written (typically through a re-deploy), this migration
      procedure will add extra time in handling the reverse/forward diffset,
      such as when using the get-modifications action."

next_step "Find all services touching this device"
exec_cli_np "show devices device ex0 service-list"

next_step "Invoke re-deploy for found services"
services=$(ncs_cmd -o -c 'mget "/devices/device{ex0}/service-list"')
for s in $services; do
    s_cli=$(echo "$s" | tr '/{}' ' ')
    exec_cli $s_cli re-deploy
    printf '\n%s' "or using ncs_cmd: "
    exec_shell ncs_cmd -u admin -c "maction $s/re-deploy"
done

next_step "Observe different configuration depending on the NED version"
exec_cli_np "acme-dns get-modifications"

next_step "Preview migrate of the remaining devices"
exec_cli "\
devices migrate old-ned-id router-nc-1.0 new-ned-id router-nc-1.1 dry-run"
echo_bold "
Note: When migrating many devices, you should use the 'devices migrate'
      action, since it's more efficient than a single device migrate.
      Here, you must provide the 'old-ned-id' to select the devices which
      will be migrated.
"

next_step "Migrate the remaining devices and re-deploy the service"
exec_cli "\
devices migrate old-ned-id router-nc-1.0 new-ned-id router-nc-1.1
acme-dns re-deploy"

next_step "Verify the new service-produced configuration"
exec_cli_np "acme-dns get-modifications"

next_step "Finally verify the new device configuration is correct"
exec_cli_np "show running-config devices device ex0..2 config sys dns"

show_done
