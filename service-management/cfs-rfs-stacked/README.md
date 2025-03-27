Stacked Services
================

Service stacking concepts usually come into play for bigger, more complex
services. There are a number of reasons why you might prefer stacked services
to a single monolithic one:

* Smaller, more manageable services with simpler logic.
* Separation of concerns and responsibility.
* Clearer ownership across teams for (parts of) overall service.
* Smaller services, reusable as components across the solution.
* Avoid overlapping configuration between service instances causing conflicts,
  such as using one service instance per device
* Improve transaction throughput performance minimizing time for saving
  the reverse diff-set

The standard naming convention with stacked services distinguishes between a
Resource-Facing Service (RFS), that directly configures one or more devices,
and a Customer-Facing Service (CFS), that is the top-level service, configuring
only other services, not devices.

This stacked service example features the `vlan` package from the `rfs-service`
example. Instead of having one service instance change configure multiple
devices, each RFS instance configures one, whereas the CFS service instance
configures all RFS instances/devices.

                     —————————————————————
                    |       CFS VLAN      |
                    |_____________________|
                   /           |           \
                  /            |            \
      —————————————   —————————————          —————————————
     | RFS_0 VLAN  | |  RFS_1 VLAN |  . . . |  RFS_N VLAN |
     |_____________| |_____________|        |_____________|
            |               |         . . .       |
          dev_0           dev_1                 dev_N

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the three packages:

    make all

All the code for this example resides in `./packages/vlan-cfs` and
`./packages/vlan-rfs`.

Start the NSO and the `ncs-netsim` network:

    make start

As NSO starts, NSO will load the three packages, load the data models defined
by the three packages, load the XML init file, and start the Python
applications defined by the `vlan-cfs` and `vlan-rfs` packages.

Instantiate the managed interfaces:

    ncs_cli -u admin
    > request devices sync-from

Add customer-facing service (CFS) configuration:

    ncs_cli -u admin
    > configure
    % set cfs-vlans vlan s1 description x iface ethX unit 1 vid 3
    % set cfs-vlans vlan s2 description x iface ethX unit 2 vid 4
    % commit dry-run
    % commit

Show the created CFS configuration:

    % show cfs-vlans
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

Show the created resource-facing service (RFS) configuration with the service
metadata from the CFS:

    % show rfs-vlans | display service-meta-data
    /* Refcount: 2 */
    /* Backpointer:[ /vc:cfs-vlans/vc:vlan[vc:name='s1']
        /vc:cfs-vlans/vc:vlan[vc:name='s2'] ] */
    vlan ex0 {
        /* Refcount: 2 */
        iface       ethX;
        /* Refcount: 2 */
        unit        2;
        /* Refcount: 2 */
        vid         4;
        /* Refcount: 2 */
        description x;
    }
    /* Refcount: 2 */
    /* Backpointer: [ /vc:cfs-vlans/vc:vlan[vc:name='s1']
        /vc:cfs-vlans/vc:vlan[vc:name='s2'] ] */
    vlan ex1 {
        /* Refcount: 2 */
        iface       ethX;
        /* Refcount: 2 */
        unit        2;
        /* Refcount: 2 */
        vid         4;
        /* Refcount: 2 */
        description x;
    }
    /* Refcount: 2 */
    /* Backpointer: [ /vc:cfs-vlans/vc:vlan[vc:name='s1']
        /vc:cfs-vlans/vc:vlan[vc:name='s2'] ] */
    vlan ex2 {
        /* Refcount: 2 */
        iface       ethX;
        /* Refcount: 2 */
        unit        2;
        /* Refcount: 2 */
        vid         4;
        /* Refcount: 2 */
        description x;
    }

Show the created device configuration with the service metadata from the RFS:

    % show devices device ex0..2 config r:sys interfaces interface ethX | \
      display service-meta-data
    device ex0 {
        config {
            sys {
                interfaces {
                    /* Refcount: 1 */
                    /* Backpointer:
                        [ /vr:rfs-vlans/vr:vlan[vr:device='ex0'] ] */
                    interface ethX {
                        /* Refcount: 1 */
                        /* Backpointer:
                            [ /vr:rfs-vlans/vr:vlan[vr:device='ex0'] ] */
                        unit 2 {
                            /* Refcount: 1 */
                            enabled;
                            /* Refcount: 1 */
                            description x;
                            /* Refcount: 1 */
                            vlan-id     4;
                        }
                    }
                }
            }
        }
    }
    device ex1 {
        config {
            sys {
                interfaces {
                    /* Refcount: 1 */
                    /* Backpointer:
                        [ /vr:rfs-vlans/vr:vlan[vr:device='ex1'] ] */
                    interface ethX {
                        /* Refcount: 1 */
                        /* Backpointer:
                            [ /vr:rfs-vlans/vr:vlan[vr:device='ex1'] ] */
                        unit 2 {
                            /* Refcount: 1 */
                            enabled;
                            /* Refcount: 1 */
                            description x;
                            /* Refcount: 1 */
                            vlan-id     4;
                        }
                    }
                }
            }
        }
    }
    device ex2 {
        config {
            sys {
                interfaces {
                    /* Refcount: 1 */
                    /* Backpointer:
                        [ /vr:rfs-vlans/vr:vlan[vr:device='ex2'] ] */
                    interface ethX {
                        /* Refcount: 1 */
                        /* Backpointer:
                            [ /vr:rfs-vlans/vr:vlan[vr:device='ex2'] ] */
                        unit 2 {
                            /* Refcount: 1 */
                            enabled;
                            /* Refcount: 1 */
                            description x;
                            /* Refcount: 1 */
                            vlan-id     4;
                        }
                    }
                }
            }
        }
    }

Cleanup
-------

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Stacked Services
+ The `demo.sh` script
