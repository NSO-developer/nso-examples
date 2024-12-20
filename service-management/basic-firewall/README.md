Basic Firewall Example
======================

In every service provider or enterprise network, all network equipment needs a
firewall or access list that limits management access. For example, Telnet or
SSH access is limited to specific IP networks or hosts, and SNMP
access is limited to the Network Operations Center (NOC) management stations.

Keeping these management firewalls updated or making changes across the
network is time-consuming.

To make life easier, we create a service model for the a basic firewall. The
model will serve as an abstraction from the vendor-specific configuration in
the network.

Preparations
------------

Make sure you have sourced the `ncsrc` file in `$NCS_DIR`. This sets up paths
and environment variables to run NSO.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Set up a simulated netsim network with one Cisco IOS router and one Juniper
router, and build the service package:

    make all

Start the netsim network:

    ncs-netsim start

Start NSO:

    ncs

Sync the configuration from the devices:

    ncs_cli --user=admin
    > request devices sync-from

Create a device group for the two devices:

    > configure
    % set devices device-group core-routers device-name [ c0 j0 ]
    % commit

Create a first basic firewall and point out the device group where it should be
applied:

    % set services basic-firewall fw1 firewall-device-group core-routers

Create the rules to limit SSH and SNMP access:

    % set services basic-firewall fw1 rule 192.168.1.155/32 22 tcp
    % set services basic-firewall fw1 rule 10.1.1.0/24 161 udp

Commit the service:

    % commit dry-run
    % commit

Let's review the resulting service and device configuration:

    % exit
    > show configuration services basic-firewall

    basic-firewall fw1 {
        rule 10.1.1.0/24 161 udp;
        rule 192.168.1.155/32 22 tcp;
        firewall-device-group [ core-routers ];
    }

     > show configuration devices device c0

    ..
        access-list {
            extended {
                ext-named-acl fw1 {
                    ext-access-list-rule
                        "permit udp 10.1.1.0 0.0.0.255 any eq 161";
                    ext-access-list-rule
                        "permit tcp 192.168.1.155 0.0.0.0 any eq 22";
                }
            }
        }
    ..
        ios:interface {
            Loopback 0 {
            ip {
                access-group {
                    access-list fw1;
                    direction   in;
                }
            }
        }
    }

    ...

    > show configuration devices device j0

    ..
    filter fw1 {
        term term0 {
            from {
                source-address 10.1.1.0/24;
                protocol    [ udp ];
                source-port [ 161 ];
            }
            then {
                accept;
            }
        }
        term term1 {
            from {
                source-address 192.168.1.155/32;
                protocol    [ tcp ];
                source-port [ 22 ];
            }
            then {
                accept;
            }
        }
    }
    ..
    interfaces {
        interface lo0 {
            unit 0 {
                family {
                    inet {
                        filter {
                            input {
                            filter-name fw1;
                        }
                    }
    ...

As seen in the output above, the access lists created are, by default, attached
to the loopback interface for both devices.

In the service model, a property enables specifying if a custom interface
should be used instead of `loopback0`.

Configure the property to use `FastEthernet 1/0` for the `c0` device:

    > configure
    % set services properties basic-firewall firewall-interface c0 \
      interface-name FastEthernet interface-number 1/0

Let's also enable logging for the firewall instances:

    % set services basic-firewall fw1 rule 10.1.1.0/24 161 udp log

A nice feature in NSO is the ability to review the changes that will be done to
the network devices before doing them:

    % commit dry-run
    cli  devices {
                device j0 {
                    config {
                        junos:configuration {
                            firewall {
                                filter fw1 {
                                    term term0 {
                                        then {
        +                                log;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                device c0 {
                    config {
                        ios:interface {
                            FastEthernet 1/0 {
                                ip {
                                    access-group {
        +                            direction in;
        +                            access-list fw1;
                                    }
                                }
                            }
                            Loopback 0 {
                                ip {
                                    access-group {
        -                            direction in;
        -                            access-list fw1;
                                    }
                                }
                            }
                        }
                        ios:ip {
                            access-list {
                                extended {
                                    ext-named-acl fw1 {
        -                            ext-access-list-rule "permit udp \
                                         10.1.1.0 0.0.0.255 any eq 161" {
        -                            }
        +                            # first
        +                            ext-access-list-rule "permit udp \
                                         10.1.1.0 0.0.0.255 any eq 161 log" {
        +                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }


Commit the changes:

    % commit
	% exit
	> request devices device c0 compare-config
    > exit

Cleanup
-------

Stop all daemons and clean all created files:

    ncs-netsim stop
    ncs --stop
    make clean

Further Reading
---------------

+ NSO Development Guide: Services
+ The `demo.sh` script
