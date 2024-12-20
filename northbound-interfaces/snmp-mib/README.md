NSO SNMP Agent Example
======================

The example shows how a simple proprietary SNMP MIB, TAIL-F-TEST-MIB, is used
to access data from a YANG module called simple.yang.

The user has an existing YANG module and/or MIB and needs to provide a mapping
between the MIB objects and YANG data nodes. This is done using the YANG
module's tailf:snmp-name or tailf:snmp-oid statements.

    simple.yang
    TAIL-F-TEST-MIB.mib

The example has been made up and is not taken from the real world. Its purpose
is merely to show how MIB objects can be mapped into YANG.

Compiling the YANG module and MIB
---------------------------------

The `simple.yang` module is compiled with the `ncsc` compiler. The MIB must be
compiled to be loaded into the NSO SNMP Agent. Each SNMP object in the MIB must
be mapped into a leaf in the YANG module. The objects for the `TAIL-F-TEST-MIB`
are mapped to leafs in the `simple.yang` module. For the `ncsc` compiler to
compile the MIB, the MIB file needs to have `.mib` as the file extension.

Sometimes, some of the objects in a MIB will not be implemented in the system,
for example, when a standard MIB that cannot be modified. In this case, the
user can write a "MIB annotation file", which instructs the NSO SNMP agent to
reply with `noSuchObject` or `noSuchInstance` whenever one of these objects is
requested. In this example, we have chosen not to implement some objects in the
`TAIL-F-TEST-MIB`, and one object defined in the MIB as writable is implemented
read-only. This is defined in the file `TAIL-F-TEST-MIB.miba`.

Running the Example
-------------------

The MIB and the user YANG module are found in a `snmp-mib` package under
`packages/snmp-mib/src/`. The package has no components and no Java code. The
sole purpose of the package is to provide the `.bin`/`.fxs` files that result
from the MIB compilation.

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start clean, i.e., no old configuration data is present:

    ncs --stop
    make clean

Build the `snmp-mib` package:

    make all

Start NSO:

    ncs

Configuring the NSO SNMP Agent
------------------------------

The SNMP Agent is part of the NSO daemon and can be enabled from the
`ncs-cdb/snmp_init.xml` file or through the NSO CLI. Per default, the SNMP
agent is enabled. Configure the IP address and port of the SNMP agent and list
all the MIBs that should be loaded at startup.

All `.bin` MIB files found in the load path are loaded. This example will
compile the `TAIL-F-TEST-MIB.bin` into the `load-dir` of the package, thus
making the MIB available over the SNMP northbound interface.

To view and manipulate the SNMP configuration:

    ncs_cli -u admin
    > show configuration snmp
    agent {
        enabled;
        ip               0.0.0.0;
        udp-port         4000;
        version {
            v1;
            v2c;
            v3;
        }
        engine-id {
            enterprise-number 32473;
            from-text         testing;
        }
        max-message-size 50000;
    }
    system {
        contact  "";
        name     "";
        location "";
    }
    ...

Accessing Data From the CLI
---------------------------

    > show configuration simpleObjects
    simpleObjects {
        numberOfServers 2;
        numberOfHosts 2;
        maxNumberOfServers 10;
        maxNumberOfHosts 10;
        hosts {
            host saturn@tail-f.com {
    ...

The initial configuration data comes from the `ncs-cdb/simple_init.xml` file.

Accessing Data From an SNMP Client/Manager
------------------------------------------

Use any SNMP manager and connect it to the NSO SNMP agent. The example below
uses NetSNMP as a manager.

SNMP walk:

    > export MIBS=$(pwd)/packages/snmp-mib/src/TAIL-F-TEST-MIB.mib
    > snmpwalk -c public -v2c localhost:4000 enterprises
    TAIL-F-TEST-MIB::numberOfServers.0 = INTEGER: 2
    TAIL-F-TEST-MIB::numberOfHosts.0 = INTEGER: 2
    TAIL-F-TEST-MIB::maxNumberOfServers.0 = INTEGER: 10
    TAIL-F-TEST-MIB::maxNumberOfHosts.0 = INTEGER: 10
    TAIL-F-TEST-MIB::hostEnabled."vega@tail-f.com" = INTEGER: false(2)
    TAIL-F-TEST-MIB::hostEnabled."saturn@tail-f.com" = INTEGER: true(1)
    TAIL-F-TEST-MIB::hostNumberOfServers."vega@tail-f.com" = INTEGER: 0
    TAIL-F-TEST-MIB::hostNumberOfServers."saturn@tail-f.com" = INTEGER: 2
    ...

SNMP get:

    > snmpget -c public -v2c localhost:4000 \
    TAIL-F-TEST-MIB::maxNumberOfServers.0
    TAIL-F-TEST-MIB::maxNumberOfServers.0 = INTEGER: 10

SNMP set:

The default VACM (view-based access control model) configuration for NSO does
not allow for updates, as it does not specify any "write view" for the v1/v2c
SNMP communities or `usm` users.

We need to add a write view to allow for updates via the `public` community we
need to add a write view for it. This can be done via, for example, the CLI or
the `ncs_cmd` tool:

    > ncs_cmd -c 'mset \
    "/snmp/vacm/group{public}/access{any no-auth-no-priv}/write-view" internet'

The access control mechanism will now allow for `set` requests of objects in
the internet view tree if sent to the `public` community.

    snmpset -c public -v2c localhost:4000 \
    TAIL-F-TEST-MIB::maxNumberOfServers.0 i 43

    TAIL-F-TEST-MIB::maxNumberOfServers.0 = INTEGER: 43

SNMP table:

The `snmptable` command can used to print an entire MIB table. Since the
table's index is a string in SNMP, a length indicator is included in the
`rowindex`. The table is, therefore, sorted with short strings before longer
strings.

    snmptable -Ci -c public -v2c localhost:4000 TAIL-F-TEST-MIB::hostTable

    SNMP table: TAIL-F-TEST-MIB::hostTable
                  index hostEnabled hostNumberOfServers hostRowStatus
      "vega@tail-f.com"       false                   0        active
    "saturn@tail-f.com"        true                   2        active

SNMP getnext:

    snmpgetnext -c public -v2c localhost:4000 \
    TAIL-F-TEST-MIB::hostEnabled.\"kalle\"

    TAIL-F-TEST-MIB::hostEnabled."vega@tail-f.com" = INTEGER: false(2)

    snmpgetnext -c public -v2c localhost:4000 \
    TAIL-F-TEST-MIB::hostEnabled.\"vega@tail-f.com\"

    TAIL-F-TEST-MIB::hostEnabled."saturn@tail-f.com" = INTEGER: true(1)

Cleanup
-------

Stop NSO and clean all created files:

    ncs --stop
    make clean

Further Reading
---------------

+ NSO Development Guide: NSO SNMP Agent
+ The `demo.sh` script


