Creating a Resource Facing Service
==================================

This example illustrates how to write a user-defined Resource Facing Service
(RFS). The example uses three NSO packages. The `router-nc-1.1` NED package
and:

* A package called `syslog` implements a simple template service whereby we can
  push out identical syslog settings to all devices in the network.

* A package called `vlan` illustrates how to create structures on managed
  devices shared between multiple service instances and uses a Java application
  to map between the service and device YANG models.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the three packages:

    make all

Start the `ncs-netsim` network:

    ncs-netsim start
    DEVICE ex0 OK STARTED
    DEVICE ex1 OK STARTED
    DEVICE ex2 OK STARTED

All the code for this example resides in `./packages/syslog` and
`./packages/vlan`.

Start NSO:

    ncs

The XML init file containing the device configuration:

    cat ./ncs-cdb/ncs_init.xml

As NSO starts, NSO will load the three packages, load the data models defined
by the three packages, load the XML init file, and start the Java application
defined by the `vlan` package.

Instantiate the managed interfaces:

    ncs_cli -u admin
    > request devices sync-from
    > configure
    % set services syslog server 5.6.7.8
    % edit services syslog server 5.6.7.8
    % set enabled selector 7 facility authpriv
    % commit dry-run
    % commit

The above service creation invokes the `create()` method in our RFS class in
`./packages/syslog/src/java/src/com/example/syslog/SyslogServiceRFS.java`. The
code copies the `syslog` settings to all managed devices in the network. We can
inspect the result of the `create()` method:

    % request services syslog get-modifications
    cli  devices {
            device ex0 {
                config {
                    r:sys {
                        syslog {
        +                    server 5.6.7.8 {
        +                        enabled;
        +                        selector 7 {
        +                            facility [ authpriv ];
        +                        }
        +                    }
        -                    server 10.3.4.5 {
        -                        enabled;
        -                        selector 8 {
        -                            facility [ auth authpriv local0 ];
        -                        }
        -                    }
                        }
                    }
                }
            }
            device ex1 {
                config {
                    r:sys {
                        syslog {
        +                    server 5.6.7.8 {
        +                        enabled;
        +                        selector 7 {
        +                            facility [ authpriv ];
        +                        }
        +                    }
        -                    server 10.3.4.5 {
        -                        enabled;
        -                        selector 8 {
        -                            facility [ auth authpriv local0 ];
        -                        }
        -                    }
                        }
                    }
                }
            }
            device ex2 {
                config {
                    r:sys {
                        syslog {
        +                    server 5.6.7.8 {
        +                        enabled;
        +                        selector 7 {
        +                            facility [ authpriv ];
        +                        }
        +                    }
        -                    server 10.3.4.5 {
        -                        enabled;
        -                        selector 8 {
        -                            facility [ auth authpriv local0 ];
        -                        }
        -                    }
                        }
                    }
                }
            }
        }

Another interesting thing to do with services is to dry-run them. Create or
modify an existing service and then ask NSO what it would do if it were to
commit:

    % set selector 8 facility local7
    % exit
    % commit dry-run
    cli  devices {
            device ex2 {
                config {
                    r:sys {
                        syslog {
                            server 5.6.7.8 {
        +                        selector 8 {
        +                            facility [ local7 ];
        +                        }
                            }
                        }
                    }
                }
            }
            device ex1 {
                config {
                    r:sys {
                        syslog {
                            server 5.6.7.8 {
        +                        selector 8 {
        +                            facility [ local7 ];
        +                        }
                            }
                        }
                    }
                }
            }
            device ex0 {
                config {
                    r:sys {
                        syslog {
                            server 5.6.7.8 {
        +                        selector 8 {
        +                            facility [ local7 ];
        +                        }
                            }
                        }
                    }
                }
            }
        }
    % commit
    % exit
    > exit

To study this example, first, look at the file
`packages/syslog/package-meta-data.xml`. It defines one component:

    <component>
      <name>syslog</name>
      <callback>
        <java-class-name>com.example.syslog.SyslogServiceRFS</java-class-name>
      </callback>
    </component>

This means that the class `SyslogServiceRFS` implements one of the service
interfaces.

The code itself illustrates several interesting points.

- How to define a service.
- Using Navu to walk down into the tree.
- To start the emulated network, do `ncs-netsim start | help`.
- To start NSO, do `ncs  [--verbose --foreground]`.
- To stop NSO, do `ncs --stop`.
- To get a Juniper CLI into NSO, do `ncs_cli -u admin`.
- To restart NSO from scratch, `ncs-setup --reset`. This will ensure NSO is
  re-initialized from the `ncs-cdb/*.xml` files when restarted.

The `vlan` Package - Shared Data Among Services
-----------------------------------------------

The Java code in
`./packages/vlan/src/java/src/com/example/vlan/VlanServiceRFS.java` is
interesting to study. It uses `NavuList.sharedCreate()` instead of
`NavuList.create()`. This is because the service creates data in
`/devices/device/config/r:sys/interfaces/interface` that is shared between
multiple service instances, for example:

    ncs_cli -u admin
    > configure
    % set services vlan s1 description x iface ethX unit 1 vid 3
    % set services vlan s2 description x iface ethX unit 2 vid 4
    % commit dry-run
    % commit

Both services "share" the interface called `ethX`, and the service Java code
"creates" the interface called `ethX`. FASTMAP works by deleting the results of
a service instance and then invoking `create()` again.

If a normal `NavuList.create()` had been used, and if we were to go back later
and modify one of the services:

    % set services vlan s1 vid 5
    % commit dry-run
    % commit

That would then automatically also remove all the things done by service s2.
The solution is always to use `NavuList.sharedCreate()` and
`NavuContainer.sharedCreate()` whenever we create structures to be
shared between service instances.

Show the created device configuration with the service metadata:

    % show devices device ex0..2 config r:sys | display service-meta-data
    ...
    interfaces {
        /* Refcount: 2 */
        /* Backpointer: [ /ncs:services/vl:vlan[vl:name='s1']
           /ncs:services/vl:vlan[vl:name='s2'] ] */
        interface ethX {
            /* Refcount: 2 */
            /* Backpointer: [ /ncs:services/vl:vlan[vl:name='s1']
               /ncs:services/vl:vlan[vl:name='s2'] ] */
            enabled;
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
    }
    ...

We see reference counters (as attributes) on the list entries. We have two
services using the `/interfaces/interface[name="ethX"]` list entry and one each
for the units. Also in, for example, the output from the NSO `ncs_load` tool
which uses the NSO Management Agent Interface (MAAPI), we can see the same
reference counters:

    ncs_load -M -Fp -P /devices/device/config/sys/interfaces/interface

    <config xmlns="http://tail-f.com/ns/config/1.0">
      <devices xmlns="http://tail-f.com/ns/ncs">
        <device>
          <name>ex0</name>
          <config>
          <sys xmlns="http://example.com/router">
            <interfaces>
            ...
              <interface refcounter="2" backpointer="[ \
               /ncs:services/vl:vlan[vl:name='s1'] \
               /ncs:services/vl:vlan[vl:name='s2'] ]">
                <name>ethX</name>
                <enabled refcounter="2" backpointer="[ \
                 /ncs:services/vl:vlan[vl:name='s1'] \
                 /ncs:services/vl:vlan[vl:name='s2'] ]"/>
                <unit refcounter="1" backpointer="[ \
                 /ncs:services/vl:vlan[vl:name='s1'] ]">
                  <name>1</name>
                  <enabled refcounter="1">true</enabled>
                  <description refcounter="1">x</description>
                  <vlan-id refcounter="1">3</vlan-id>
                </unit>
                <unit refcounter="1" backpointer="[ \
                 /ncs:services/vl:vlan[vl:name='s2'] ]">
                  <name>2</name>
                  <enabled refcounter="1">true</enabled>
                  <description refcounter="1">x</description>
                  <vlan-id refcounter="1">4</vlan-id>
                </unit>
              </interface>
    ...

Cleanup
-------

To have NSO re-initialized from the ncs-cdb/*.xml files when restarted:

    ncs --stop
    ncs-setup --reset
    ncs

To reset and restart the netsim network:

    ncs-netsim stop
    ncs-netsim reset
    ncs-netsim start

Or:

    ncs-netsim restart

To clean all created files after stopping NSO and the simulated devices:

    make clean

Further Reading
---------------

+ NSO Development Guide: Services
+ NSO Development Guide: NSO Java VM
+ The `demo.sh` script
