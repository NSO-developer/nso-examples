Compliance Reporting
====================

This example demonstrates the fundamentals of the compliance reporting feature.
It illustrates simple compliance checks for services as well as how compliance
templates can be set up for device configuration compliance.

Example Network
---------------

The example network consists of Cisco ASR 9k and Juniper core routers (P and
PE) and Cisco IOS-based CE routers.

The Layer3 VPN service configures the CE/PE routers for all endpoints in the
VPN with BGP as the CE/PE routing protocol. The service itself is out of scope
for this example, it is added to illustrate how service checks can be made
by the compliance report.

Running The Example from the CLI and Web UI
-------------------------------------------

To run the steps below in this README from a demo shell script:

    make demo

Setup the Environment
---------------------

The below steps are similar to the demo script.

Ensure you start clean, i.e, no old configuration data is present. If you have
been running this or some other example before, stop any NSO or simulated
network nodes, `ncs-netsim`, you may have running. Outputs like 'connection
refused (stop)' mean no previous NSO was running, and 'DEVICE ce0 connection
refused (stop)...' no simulated network was running, which is good.

    make stop clean all start
    ncs_cli -u admin -C

Before configuring the compliance report, we must sync the configuration
from all network devices and deploy the configured services. For
demonstration purposes we configure some QOS configuration to the services
without deploying these changes to the devices. This makes the services
out of sync.

    devices sync-from
    vpn l3vpn * re-deploy
    vpn l3vpn volvo qos qos-policy SILVER
    vpn l3vpn ford qos qos-policy BRONZE
    commit no-deploy

Configure Simple Device and Service Checks
------------------------------------------

Enter configuration mode and configure a new compliance report. Out of
sync checks are set up for the P and PE device groups as well as for all
service instances. Device checks can be set up for a selection of device groups,
specific devices, and devices matched by an XPath expression. Service
checks can similarly be set up for service types and specific service instances.

    config
    compliance reports report Compliance-Audit device-check device-group [ P PE ] current-out-of-sync true
    top
    compliance reports report Compliance-Audit service-check all-services current-out-of-sync true
    config
    commit dry-run
    commit
    
Then run the configured compliance report:

    compliance reports report Compliance-Audit run

The result can then be viewed:

    show compliance report-results

The reports are stored in a SQLite database file but can be exported to
different formats with the `export` command. Every consecutive run of the report
is stored in the same SQLite database. This allows for comparing the report
results over time.

Compliance reports can just as easily be created, run, and managed in the
web UI. Viewing the results of a run is easiest done from here. Let's log in to
the web UI to create a new session:

Open http://localhost:8080/ in your browser.

Go to http://localhost:8080/webui-one/Compliance/ReportResults once logged in.

Here all report results are listed. Click on the latest run to view its
details. Click the Services tab to view the non-compliant services.

The services was never deployed to the network. Re-deploy them to send the
configuration to the network:

    vpn l3vpn * re-deploy

Then re-run the report:

    compliance reports report Compliance-Audit run

Return to the Report result overview page to check the latest run.

Add compliance templates
------------------------

Services are the preferred way to manage device configuration in NSO as they
provide numerous benefits. However, on your journey to full automation, perhaps
you only use NSO to configure a subset of all the services (configuration) on
the devices. In this case, you can still perform generic configuration
validation on other parts with the help of device configuration checks.

Often, each device will have a somewhat different configuration, such as its own
set of IP addresses, which makes checking against a static template impossible.
For this reason, NSO supports compliance templates.

These templates are similar to but separate from, device templates. With
compliance templates, you use regular expressions to check compliance, instead
of simple fixed values. You can also define and reference variables that get
their values when a report is run. All selected devices are then checked against
the compliance template and the differences (if any) are reported as a
compliance violation.

Load the pre-defined template provided with the example:

    load merge templates/acl_deny_options.xml
    load merge templates/disable_propagate_ttl.xml
    load merge templates/interface_unreachables.xml
    load merge templates/line_console_strict.xml
    load merge templates/service_encrypt.xml
    load merge templates/service_small_servers.xml
    load merge templates/timezone.xml
    commit dry-run
    commit

The pre-defined templates demonstrates common compliance patterns:

1. Example exact match:

    Without any options given, a compliance template checks whether the
    configuration is present on the device.

        compliance template service-encrypt
         ned-id cisco-ios-cli-6.108
          config
           service password-encryption

2. Example variable substitution:

    In this example, we defined reference variables that get their values when
    a report is run.

        compliance template timezone
         ned-id cisco-ios-cli-6.108
          config
           clock timezone {$TIMEZONE}
           clock timezone {$OFFSET_HOURS}
           clock timezone {$OFFSET_MINUTES}

3. Example regular expression:

    Often regular expressions are used instead of simple fixed values to check
    compliance.

        compliance template acl_deny_options
         ned-id cisco-ios-cli-6.108
          config
           ip access-list extended {$IPV4_PROTECT}
            ".*deny ip any any option any-options"

4. Example absent match:

    In order to ensure that configuration does not exist on a device, the
    `absent` tag can be used. This example will result in a violation if
    `service udp-small-servers` or `service tcp-small-servers` are configured on the
    device.

        compliance template service-small-servers
         ned-id cisco-ios-cli-6.108
          config
           ! Tags: absent
           service udp-small-servers
           ! Tags: absent
           service tcp-small-servers

5. Example configuration section:

    By adding the `strict` tag, the check will not only look whether the
    required configuration is present but also reports any configuration
    present on the device that is not part of the template.

        compliance template line_console_strict
         ned-id cisco-ios-cli-6.108
          config
           ! Tags: strict
           line console 0
            exec-timeout 0
            login authentication {$AUTH_NAME}
            
6. Example nested match:

    In this example, we are only interested in interfaces that are actually
    configured on the device. This is where the `allow-empty` tag comes in.
    By setting this tag on each interface, the check will only be run if there
    are interfaces configured of that type, all nested configuration below the
    parent are only matched if the interface exists.

        compliance template unreachable
         ned-id cisco-ios-cli-6.108
          config
           ! Tags: allow-empty
           interface GigabitEthernet .*
            ip unreachables false
           !
           ! Tags: allow-empty
           interface TenGigabitEthernet .*
            ip unreachables false

Compliance templates have a `check` command that allows you to quickly check
compliance against a set of devices:

    compliance template timezone check device-group PE variable { name TIMEZONE value EST } variable { name OFFSET_HOURS value -6 } variable { name OFFSET_MINUTES value 0 }

Add the templates to the report:

    compliance reports report Compliance-Audit device-check template acl_deny_options
    variable IPV4_PROTECT value filter_traffic
    exit
    compliance reports report Compliance-Audit device-check template disable_propagate_ttl
    compliance reports report Compliance-Audit device-check template interface_unreachables
    compliance reports report Compliance-Audit device-check template line_console_strict
    variable AUTH_NAME value default
    exit
    compliance reports report Compliance-Audit device-check template service_encrypt
    compliance reports report Compliance-Audit device-check template service_small_servers
    compliance reports report Compliance-Audit device-check template timezone
    variable TIMEZONE value EST
    exit
    variable OFFSET_HOURS value -5
    exit
    variable OFFSET_MINUTES value 0
    top
    commit dry-run
    commit

Re-run the compliance report:

    compliance reports report Compliance-Audit run
    
View the new results in the web UI by clicking on the latest results
on the Report results overview page.

As you can see in the Devices tab we have several new violations. Click on the
individual devices to see how configuration compares against the template.

Let's fix the non-compliant configuration:

    devices device * config ios:clock timezone EST -5 0
    top
    devices device * config cisco-ios-xr:clock timezone EST -5 0
    top
    devices device * config cisco-ios-xr:mpls ip-ttl-propagate disable
    top
    commit dry-run outformat native
    commit

Then re-run the compliance report one last time:

    compliance reports report Compliance-Audit run
    
Return to the Report results overview page and click on the latest results.
Now there are no violations. Notice the historical graph on how violations have
changed over time.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Operation & Usage Guide: Compliance Reporting
+ The `demo.sh` script
