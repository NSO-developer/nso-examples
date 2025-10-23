Manage SNMP Devices with NSO
============================

This example shows how to set up and manage three SNMP devices with NSO. The
three devices are simulated using netsim. Each device uses one of the three
major SNMP versions, SNMPv1, SNMPv2c, and SNMPv3, with authentication and
encryption.

A local installation of NSO is required to run the example. The `NCS_DIR`
variable must be set up to point to the installation, preferably by sourcing
the `ncsrc` script, which is found in the top dir of the installation.

Files in This Example
---------------------

The NSO instance runs from the directory of this README file and contains the
following files:

* `packages/ex-snmp-ned/` - NED package for the example SNMP device. This
  package contains all MIBs for the SNMP devices.
* `packages/ex-snmp-ned/src/mibs/BASIC-CONFIG-MIB.mib` - MIB implemented by the
  device.
* `packages/ex-snmp-ned/src/mibs/BASIC-CONFIG-MIB.miba` - Annotation file for
  the device YANG data model, with directives to NSO.
* `packages/ex-snmp-ned/src/mibs/SNMPv2-MIB.mib` - MIB implemented by the
  device.
* `netsim/` - A directory with files used to simulate the SNMP devices and
  created from the `Makefile` using the `ncs-netsim` script.
* `ncs-cdb/` - NSO's database directory.
* `ncs-cdb/ncs_init.xml` - Initial configuration data for this example.
* `ncs-cdb/aaa_init.xml` - Initial authentication, authorization, and
  accounting configuration data for NSO users, copied by the `Makefile` to the
  `ncs-cdb` directory.
* `logs/` - Directory for log files.
* `state/` - Directory for NSO runtime state.

Adding Device Data Models
-------------------------

Just as with other devices, before managing an SNMP device, its YANG data
models must be compiled and imported into NSO. With SNMP, the data models are
defined by MIBs. These MIBs must be converted to YANG data models, and the YANG
data models must be compiled, just as with other devices. This is taken care of by
using the NSO YANG compiler `ncsc` command with the `--ncs-compile-mib-bundle`
option.

In this example, we have created an NSO package with the MIBs the devices
implements. The `ncs-make-package` command can be used to create a package from
a set of MIBs that a device implement.

The package we have created is used for two things:

1.  Tell NSO about the MIBs the devices implement.

2.  Use netsim to simulate these devices.

We will not go into the details of netsim here. The files for the simulated
devices are available under `packages/ex-snmp-ned/netsim`.

Adding Annotations to NSO
-------------------------

As explained in the Development Guide chapter "Annotations for MIB Objects",
MIBs often need to be annotated with additional information used by NSO to
build proper SET PDUs for the device.

Examining the MIBs the devices implement, we find the object below:

    bscActFlow OBJECT-TYPE
        SYNTAX      Integer32
        MAX-ACCESS  read-create
        STATUS      current
        DESCRIPTION
            "Can only be changed when admin state is locked."
        ::= { bscActEntry 4 }

Here, we need to instruct NSO that if this object is modified, it must first set
`bscActAdminState` to `locked`, then modify `bscActFlow`, and then reset
`bscActAdminState` to `unlocked` if it was `unlocked` when we started.

This is done in an annotation file, `BASIC-CONFIG-MIB.miba`, where we add the
following lines:

    bscActAdminState  ned-set-before-row-modification = locked
    bscActFlow        ned-modification-dependent

The first line tells NSO that the `bscActAdminState` column must be `locked`
when dependent columns are modified. The second line tells NSO that the
`bscActFlow` is the only dependent column in this table.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

We are using the default `ncs.conf` and populating a `ncs_init.xml` file with
initial configuration data for NSO, configuring NSO with the three devices and
how NSO should communicate with them.

When executing:

    make all

The following will happen:

* The package `ex-snmp-ned` is built, invoking `ncsc --ncs-compile-mib-bundle`,
  and compiles files for the device simulation.

* The `ncs-netsim` command is used to create a simulated network with three
  SNMP devices.

Start the simulated network and NSO:

    ncs-netsim start
    ncs

NSO is initialized with managed device data populating the `tailf-ncs`
namespace with managed device data such as the IP and port at which each
managed device is available and authentication configuration, i.e., how to
communicate with each managed device.

Start configuring the devices by executing:

    ncs_cli --user admin

NSO is configured to communicate with the three devices:

    > show configuration devices device r0..2
    device r0 {
      address 127.0.0.1;
      port    11022;
      device-type {
          snmp {
              version        v1;
              snmp-authgroup default;
              mib-group      [ basic ];
          }
      }
      state {
          admin-state unlocked;
      }
    }
    device r1 {
      address 127.0.0.1;
      port    11023;
      device-type {
          snmp {
              version        v2c;
              snmp-authgroup default;
          }
      }
      state {
          admin-state unlocked;
      }
    }
    device r2 {
      address 127.0.0.1;
      port    11024;
      device-type {
          snmp {
              version        v3;
              snmp-authgroup default;
              mib-group      [ basic snmp ];
          }
      }
      state {
          admin-state unlocked;
      }
    }

There are different versions for all three devices and a shared authentication
group. The authentication groups define v1/v2c and v3c authentication
parameters. For v1/v2c, it is just the community string, and for v3, it maps
the NSO user to corresponding v3 parameters:

    > show configuration devices authgroups snmp-group default-map
    snmp-group default {
      default-map {
          community-name public;
      }
    }

    > show configuration devices authgroups snmp-group umap
    snmp-group default {
      umap admin {
          usm {
              remote-name    admin;
              security-level auth-priv;
              auth {
                  md5 {
                      remote-password $4$wIo7Yd068FRwhYYI0d4IDw==;
                  }
              }
              priv {
                  des {
                      remote-password $4$wIo7Yd068FRwhYYI0d4IDw==;
                  }
              }
          }
      }
    }

When the MIBs are compiled for NSO, read-write objects become config and
read-only objects become operational data. There is one exception: `RowStatus`
objects become operational data in NSO, and row creation is managed
automatically by NSO.

The next step is to get the config from the devices. This will populate CDB
with all read-write objects.

    > request devices sync-from
    sync-result {
        device r0
        result true
    }
    sync-result {
        device r1
        result true
    }
    sync-result {
        device r2
        result true
    }

    > show configuration devices device
    device r0 {
        address 127.0.0.1;
        port    11022;
        device-type {
            snmp {
                version        v1;
                snmp-authgroup default;
                mib-group      [ basic ];
            }
        }
        state {
            admin-state unlocked;
        }
        config {
            basic-config:BASIC-CONFIG-MIB {
                bscBaseTable {
                    bscBaseEntry 1 {
                        bscBaseStr foo;
                        bscBaseErr 0;
                    }
                    bscBaseEntry 2 {
                        bscBaseStr bar;
                        bscBaseErr 0;
                    }
                }
                bscAddrTable {
                    bscAddrEntry 1 2 {
                        bscAddrStr addr1;
                    }
                    bscAddrEntry 1 4 {
                        bscAddrStr addr2;
                    }
                }
            }
        }
    }
    device r1 {
        ...
    }
    device r2 {
    ...
    }

At this point, it is time to configure the devices using SNMP. Note that you
can automatically use the CLI across the different SNMP versions, including
v3 authentication.

Let's set the `sysContact` field on all devices that support it. To find out
which devices implement a data model, we can run:

    > show devices device module SNMPv2-MIB
    device r1 {
        module SNMPv2-MIB;
    }
    device r2 {
        module SNMPv2-MIB;
    }

Configure device `r1`:

    > configure
    Entering configuration mode private

    % set devices device r1 config SNMPv2-MIB system sys <tab>
    Possible completions:
      sysContact  sysLocation  sysName
    % set devices device r1 config SNMPv2-MIB system sysContact wallan

Now we can see what would be sent to the device before committing:

    % commit dry-run outformat native
    native {
        device {
            name r1
            data set-request
                     1.3.6.1.2.1.1.4.0=wallan
        }
    }

Commit:

    % commit
    Commit complete.
    % exit

When the NSO transaction is committed, NSO sends SET requests to the
devices. So after this commit, use, for example, the net-snmp `snmpget` command
towards the device to verify the new value:

    snmpget -v2c -c public 127.0.0.1:11023 sysContact.0

    SNMPv2-MIB::sysContact.0 = STRING: wallan

Set the `sysContact` on the 'r2' device too:

    ncs_cli -u admin
    > configure
    % set devices device r2 config SNMPv2-MIB system sysContact wallan
    % commit dry-run
    % commit
    Commit complete.

NSO will generate rollback files for all transactions to store the
inverse SNMP operation. To display the rollback files:

    % run file show logs/rollback*
    ....
    # Created by: admin
    # Date: 2011-12-15 15:40:10
    # Via: cli
    # Type: delta
    # Label:
    # Comment:
    # No: 10005
    ....

To undo the `sysContact` change:

    % rollback-files apply-rollback-file id 00005
    % commit dry-run
    % commit
    Commit complete.
    % exit

If you change values in the agent over SNMP by means other than NSO, you can
compare the NSO configuration with the agent. If you set the `sysContact` over
SNMP using net-snmp commands or a MIB browser, you can ask NSO to display the
diff:

    snmpset -v2c -c public 127.0.0.1:11023 sysContact.0 s john
    ncs_cli -u admin
    % request devices device r1 compare-config
    diff
     devices {
         device r1 {
             config {
                 snmpMIB:SNMPv2-MIB {
                     system {
    -                    sysContact wallan;
    +                    sysContact john;
                     }
                 }
             }
         }
     }

If you consider NSO to be the primary manager of the configuration:

    % request devices sync-to
    sync-result {
        device r0
        result true
    }
    sync-result {
        device r1
        result true
    }
    sync-result {
        device r2
        result true
    }

NSO manages row creation using RowStatus (RFC 2580). The CLI command below will
create a new row in the `bscActTable` without committing.

    % set devices device r0 config basic-config:BASIC-CONFIG-MIB \
    bscActTable bscActEntry 17 bscActOwner wallan bscActFlow 42 \
    bscActAdminState unlocked

    % commit dry-run
     devices {
         device r0 {
             config {
                 basic-config:BASIC-CONFIG-MIB {
                     bscActTable {
    +                    bscActEntry 17 {
    +                        bscActOwner wallan;
    +                        bscActAdminState unlocked;
    +                        bscActFlow 42;
    +                    }
                     }
                 }
             }
         }
     }

    % revert no-confirm

NSO also understands the dependencies between tables. If you have an expansion
table, you must first create a row in the base table if you are not referring
to an existing one. NSO will resolve these dependencies and create them in the
right order, as illustrated below where `bscAddrTable` expands `bscBaseTable`.

    % set devices device r0 config basic-config:BASIC-CONFIG-MIB \
    bscBaseTable bscBaseEntry 17 bscBaseStr seventeen

    % set devices device r0 config basic-config:BASIC-CONFIG-MIB \
    bscAddrTable bscAddrEntry 17 82 bscAddrStr expansion-demo

    % compare running brief
     devices {
         device r0 {
             config {
                 basic-config:BASIC-CONFIG-MIB {
                     bscBaseTable {
    +                    bscBaseEntry 17 {
    +                        bscBaseStr seventeen;
    +                    }
                     }
                     bscAddrTable {
    +                    bscAddrEntry 17 82 {
    +                        bscAddrStr expansion-demo;
    +                    }
                     }
                 }
             }
         }
     }

    % commit | details
    ...
    ncs: SNMP connect to "r0" 127.0.0.1:11022 (trying to get-next...)
    ncs: Device: r0 SNMP prepare - send diff to the managed device
    ...
    Commit complete.

NSO manages transactions across devices, even in the case of SNMP. When
performing a configuration transaction across several SNMP devices and one
fails, NSO will generate the reverse operation for the participating devices
and leave the network in the state it was in. You can force an error in this
demo by setting `bscBaseErr` to a non-zero value, as illustrated in the example
below:

    % set devices device r2 config basic-config:BASIC-CONFIG-MIB \
    bscBaseTable bscBaseEntry 42 bscBaseErr 1

    % commit
    Aborted: 'devices device r2 config basic-config:BASIC-CONFIG-MIB \
    bscBaseTable bscBaseEntry 42 bscBaseErr': inconsistent value

    % exit no-confirm

NSO can also show operational data that is directly fetched from the device:

    > show devices device r1 live-status SNMPv2-MIB
    system {
        sysDescr        "Tail-f ConfD agent - r1";
        sysObjectID     1.3.6.1.4.1.24961;
        sysUpTime       1279481;
        sysContact      wallan;
        sysName         "";
        sysLocation     "";
        sysServices     72;
        sysORLastChange 0;
    }
    snmp {
        snmpInPkts              145;
        snmpInBadVersions       0;
        snmpInBadCommunityNames 0;
        snmpInBadCommunityUses  0;
        snmpInASNParseErrs      0;
        snmpEnableAuthenTraps   disabled;
        snmpSilentDrops         0;
        snmpProxyDrops          0;
    }
    snmpSet {
        snmpSetSerialNo 3946532;
    }
    > exit

A handy way to use NSO is to mediate statistics for several SNMP nodes. Since
NSO will manage authentication and collection it is easy for a performance
monitoring system to retrieve the data and create reports.

The example below shows how to retrieve stats from all devices from NSO over
NETCONF using the `netconf-console` tool:

    netconf-console --get -x "/devices/device/live-status/SNMPv2-MIB/snmp"

    <?xml version="1.0" encoding="UTF-8"?>
    <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1">
      <data>
        <devices xmlns="http://tail-f.com/ns/ncs">
            <name>r1</name>
            <live-status>
              <SNMPv2-MIB xmlns="urn:ietf:params:xml:ns:yang:smiv2:SNMPv2-MIB">
                <snmp>
                  <snmpInPkts>59</snmpInPkts>
                  <snmpInBadVersions>0</snmpInBadVersions>
                  <snmpInBadCommunityNames>0</snmpInBadCommunityNames>
                  <snmpInBadCommunityUses>0</snmpInBadCommunityUses>
                  ...
            </live-status>
          </device>
          ...
        </devices>
      </data>
    </rpc-reply>

Cleanup
-------

When you finish this example, make sure all daemons are stopped. Stop NSO and
the simulated network:

    make stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Development Guide: The SNMP NED
+ The `demo.sh` script
