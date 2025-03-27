
Upgrade a Service with Non-backward Compatible Changes
======================================================

This example performs advanced service package upgrades. The first upgrade
adds a new mandatory leaf, while the second upgrade changes the YANG model,
package name, and data is migrated.

First, we build and run the `vlan` service to create a couple of service
instances. We then exchange the vlan package for a new version, where a new
mandatory leaf has been added. Continuing, we replace the `vlan` package with a
`tunnel` package. This illustrates a scenario where the YANG is changed in the
first upgrade, so user-written code is needed to update the CDB data. In the
second case, the data must be moved (modified) to a package with a different
YANG model and package name.

An upgrade component is registered in the `package-meta-data.xml` file for the
`tunnel` package to handle the CDB upgrade. The upgrade component is
a Java class with a main class that connects to NSO, reads old config data
using the CDB API, and writes the adapted config data using the Management
Agent API (MAAPI).

There are also Python variants of the packages with new versions called
`vlan_v2-py` and `tunnel-py`.

We will start with the `vlan` package and exchange it for a new version of the
same package, followed by a switch to the `tunnel` package.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Copy the `router-nc-1.1` package to the `packages` directory and build the
packages:

    make all

Copy or create a symbolic link to the `vlan` package in the `packages`
directory:

    cp -r ../package-store/vlan ./packages

Start the `ncs-netsim` network:

    ncs-netsim start
    DEVICE ex0 OK STARTED
    DEVICE ex1 OK STARTED
    DEVICE ex2 OK STARTED

Start NSO:

    ncs

As NSO starts, NSO will load the packages, load the data models defined by the
packages, load the XML init file, and start the Java application defined by
the `vlan` package.

Initial `sync-from` devices:

    ncs_cli -u admin
    > request devices sync-from

Create two service instances:

    > configure
    % set services vlan s1 description x iface ethX unit 1 vid 3
    % set services vlan s2 description x iface ethX unit 2 vid 4
    % commit dry-run
    % commit

Check the service configuration data:

    % show services vlan
    vlan s1 {
        iface       ethX;
        unit        1;
        vid         3;
        description x;
    }
    vlan s2 {
        iface       ethX;
        unit        2;
        vid         4;
        description x;
    }

To start the upgrade, stop NSO, exchange the `vlan` package for the `vlan_v2`
package, and start NSO so that the upgrade component Java program in the
`vlan_v2` package upgrades the service data:

    ncs --stop
    rm -rf packages/vlan
    cp -r ../package-store/vlan_v2 ./packages/vlan
    make vlan

Or to use the Python `vlan_v2-py` package:

    ncs --stop
    rm -rf ./packages/vlan
    cp -r ./package-store/vlan_v2-py ./packages/vlan
    make vlan

Start NSO and have NSO reload packages to perform the CDB upgrade:

    ncs --with-package-reload

Check the upgraded service data:

    ncs_cli -u admin
    > configure
    % show services vlan
    vlan s1 {
        global-id   ethX-1-3;
        iface       ethX;
        unit        1;
        vid         3;
        description x;
    }
    vlan s2 {
        global-id   ethX-2-4;
        iface       ethX;
        unit        2;
        vid         4;
        description x;
    }

Let's review the changes made by the `vlan` service for the `ex0` device:

    % show devices device ex0 | display service-meta-data
    address   127.0.0.1;
    port      12022;
    authgroup default;
    device-type {
        netconf {
            ned-id router-nc-1.1;
        }
    }
    state {
        admin-state unlocked;
    }
    config {
        sys {
            interfaces {
                interface eth0 {
                    unit 0 {
                        enabled;
                        family {
                            inet {
                                address 192.168.1.2 {
                                    prefix-length 16;
                                }
                            }
                        }
                    }
                    unit 1 {
                        enabled;
                        family {
                            inet {
                                address 192.168.1.3 {
                                    prefix-length 16;
                                }
                            }
                        }
                    }
                    unit 2 {
                        enabled;
                        description "My Vlan";
                        vlan-id     18;
                    }
                    unit 3 {
                        enabled;
                        family {
                            inet6 {
                                address 2001:db8::8:800:100c:a {
                                    prefix-length 64;
                                }
                            }
                        }
                    }
                }
                /* Refcount: 2 */
                /* Backpointer: [ /ncs:services/vl:vlan[vl:name='s1'] /ncs:services/vl:vlan[vl:name='s2'] ] */
                interface ethX {
                    /* Refcount: 1 */
                    /* Backpointer: [ /ncs:services/vl:vlan[vl:name='s1'] ] */
                    unit 1 {
                        /* Refcount: 1 */
                        enabled;
                        /* Refcount: 1 */
                        description x;
                        /* Refcount: 1 */
                        vlan-id     3;
                    }
                    /* Refcount: 1 */
                    /* Backpointer: [ /ncs:services/vl:vlan[vl:name='s2'] ] */
                    unit 2 {
                        /* Refcount: 1 */
                        enabled;
                        /* Refcount: 1 */
                        description x;
                        /* Refcount: 1 */
                        vlan-id     4;
                    }
                }
                serial ppp0 {
                    ppp {
                        accounting acme;
                    }
                    authentication {
                        method pap;
                    }
                    authorization admin;
                }
            }
            routes {
                inet {
                    route 10.10.0.0 16 {
                        description "Route 1";
                        next-hop 192.168.10.1 {
                            metric 100;
                        }
                    }
                    route 10.20.0.0 16 {
                        description "Route 2";
                        next-hop 192.168.10.2 {
                            metric 100;
                        }
                    }
                    route 10.30.0.0 16 {
                        description "Route 3";
                        next-hop 192.168.10.3 {
                            metric 100;
                        }
                    }
                    route 10.40.0.0 16 {
                        description "Route 4";
                        next-hop 192.168.10.4 {
                            metric 100;
                        }
                    }
                    route 10.50.0.0 16 {
                        description "Route 5";
                        next-hop 192.168.10.5 {
                            metric 100;
                        }
                    }
                    route 10.60.0.0 16 {
                        description "Route 6";
                        next-hop 192.168.10.6 {
                            metric 100;
                        }
                    }
                    route 10.70.0.0 16 {
                        description "Route 7";
                        next-hop 192.168.10.7 {
                            metric 100;
                        }
                    }
                }
                inet6 {
                    route 2001:db8::8:800:200c:417a 64 {
                        description "Route 8";
                        enabled;
                        next-hop 2001:db8::8:800:201c:400a {
                            metric 100;
                        }
                    }
                }
            }
            syslog {
                server 10.3.4.5 {
                    enabled;
                    selector 8 {
                        facility [ auth authpriv local0 ];
                    }
                }
            }
            ntp {
                server 10.2.3.4 {
                    key 2;
                }
                key 2;
                controlkey 2;
            }
            dns {
                server 10.2.3.4;
            }
        }
    }

Note that the `backpointer` attribute shows which service made the change.

Undeploy the service instances with `un-deploy no-networking` to remove the
`vlan` service meta data before upgrading to the `tunnel` package without
changing the device configuration:

    % request services vlan s1..2 un-deploy no-networking
    % exit
    > exit

Stop NSO, exchange the `vlan` package with the `tunnel` package, and start NSO
to upgrade the service data using the upgrade component Java program in the
`tunnel` package:

    ncs --stop
    rm -rf ./packages/vlan
    cp -r ./package-store/tunnel ./packages/
    make tunnel

Or to use the Python `tunnel-py` package:

    ncs --stop
    rm -rf ./packages/vlan
    cp -r ./package-store/tunnel-py ./packages/tunnel
    make tunnel

Start NSO and have NSO force reload packages to perform the CDB upgrade. Force
is needed to replace the `vlan` package with the `tunnel` package:

    ncs --with-package-reload-force

Check the upgraded service data:

    ncs_cli -u admin
    > configure
    % show services tunnel
    tunnel s1 {
        gid       ethX-1-3;
        interface ethX;
        assembly  1;
        tunnel-id 3;
        descr     x;
    }
    tunnel s2 {
        gid       ethX-2-4;
        interface ethX;
        assembly  2;
        tunnel-id 4;
        descr     x;
    }

Same service instances but with new names on containers and leafs.

Re-deploy the service instances to own the device configuration again. Check
that the `re-deploy` does not change the device configuration before the actual
`re-deploy` using `no-networking`:

    % request services tunnel s1..2 re-deploy no-networking
    % request devices device ex0..2 compare-config

We can check that the services are still in sync with the device configuration:

    % request services tunnel s1 check-sync
    in-sync true
    % request services tunnel s2 check-sync
    in-sync true

No diff and in sync. Re-deploy:

    % request services tunnel s1..2 re-deploy dry-run
    % request services tunnel s1..2 re-deploy

Review the device configuration data:

    % show devices device ex0 | display service-meta-data
    address   127.0.0.1;
    port      12022;
    authgroup default;
    device-type {
        netconf {
            ned-id router-nc-1.1;
        }
    }
    state {
        admin-state unlocked;
    }
    config {
        sys {
            interfaces {
                interface eth0 {
                    unit 0 {
                        enabled;
                        family {
                            inet {
                                address 192.168.1.2 {
                                    prefix-length 16;
                                }
                            }
                        }
                    }
                    unit 1 {
                        enabled;
                        family {
                            inet {
                                address 192.168.1.3 {
                                    prefix-length 16;
                                }
                            }
                        }
                    }
                    unit 2 {
                        enabled;
                        description "My Vlan";
                        vlan-id     18;
                    }
                    unit 3 {
                        enabled;
                        family {
                            inet6 {
                                address 2001:db8::8:800:100c:a {
                                    prefix-length 64;
                                }
                            }
                        }
                    }
                }
                /* Refcount: 2 */
                /* Backpointer: [ /ncs:services/tl:tunnel[tl:tunnel-name='s1'] /ncs:services/tl:tunnel[tl:tunnel-name='s2'] ] */
                interface ethX {
                    /* Refcount: 1 */
                    /* Backpointer: [ /ncs:services/tl:tunnel[tl:tunnel-name='s1'] ] */
                    unit 1 {
                        /* Refcount: 1 */
                        enabled;
                        /* Refcount: 1 */
                        description x;
                        /* Refcount: 1 */
                        vlan-id     3;
                    }
                    /* Refcount: 1 */
                    /* Backpointer: [ /ncs:services/tl:tunnel[tl:tunnel-name='s2'] ] */
                    unit 2 {
                        /* Refcount: 1 */
                        enabled;
                        /* Refcount: 1 */
                        description x;
                        /* Refcount: 1 */
                        vlan-id     4;
                    }
                }
                serial ppp0 {
                    ppp {
                        accounting acme;
                    }
                    authentication {
                        method pap;
                    }
                    authorization admin;
                }
            }
            routes {
                inet {
                    route 10.10.0.0 16 {
                        description "Route 1";
                        next-hop 192.168.10.1 {
                            metric 100;
                        }
                    }
                    route 10.20.0.0 16 {
                        description "Route 2";
                        next-hop 192.168.10.2 {
                            metric 100;
                        }
                    }
                    route 10.30.0.0 16 {
                        description "Route 3";
                        next-hop 192.168.10.3 {
                            metric 100;
                        }
                    }
                    route 10.40.0.0 16 {
                        description "Route 4";
                        next-hop 192.168.10.4 {
                            metric 100;
                        }
                    }
                    route 10.50.0.0 16 {
                        description "Route 5";
                        next-hop 192.168.10.5 {
                            metric 100;
                        }
                    }
                    route 10.60.0.0 16 {
                        description "Route 6";
                        next-hop 192.168.10.6 {
                            metric 100;
                        }
                    }
                    route 10.70.0.0 16 {
                        description "Route 7";
                        next-hop 192.168.10.7 {
                            metric 100;
                        }
                    }
                }
                inet6 {
                    route 2001:db8::8:800:200c:417a 64 {
                        description "Route 8";
                        enabled;
                        next-hop 2001:db8::8:800:201c:400a {
                            metric 100;
                        }
                    }
                }
            }
            syslog {
                server 10.3.4.5 {
                    enabled;
                    selector 8 {
                        facility [ auth authpriv local0 ];
                    }
                }
            }
            ntp {
                server 10.2.3.4 {
                    key 2;
                }
                key 2;
                controlkey 2;
            }
            dns {
                server 10.2.3.4;
            }
        }
    }

Note that the backpointer attribute now points to the `tunnel` service.

Cleanup
-------

When you finish this example, make sure all daemons are stopped. Stop NSO and
the simulated network:

    ncs --stop
    ncs-netsim stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Development Guide: Writing an Upgrade Package Component
+ The `demo.sh` script