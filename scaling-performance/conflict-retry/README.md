NSO Concurrency Model Conflicts and Retries
===========================================

Note: See the NSO Development Guide Chapter "NSO Concurrency Model" for a
detailed description of the optimistic concurrency feature used in this
example.

Beginning with version 6.0, NSO uses optimistic concurrency, which improves
parallelism. With this approach, NSO avoids the need for serialization deeper
into the database transaction. It means your code, such as a service mapping or
custom validation code, can run in parallel.

Optimistic concurrency works on the premise that data conflicts are rare. NSO
helps detect conflicts by checking that there are no conflicts with other
transactions. Before each transaction is committed to the database, NSO
verifies that all the data accessed as part of the transaction is still valid
when applying changes.

A conflict can occur when data read by one transaction is changed by another
before the first transaction is committed.

This example shows how such conflicts can occur, how NSO detects them, and how
the transaction can be retried automatically by NSO or through a Python or Java
application.

Two packages, see the `package-repository` folder, make up the example, and
share the same YANG model and service templates. They only differ because one
implements its service code and actions in Python, and the other in Java.
The package for the netsim devices can be found in the
`$NCS_DIR/common/packages/router-nc-1.1` folder.

The `serverconfigRFS.py`, `serverconfigRFS.java`, `dns-config-template.xml`,
`ntp-config-template.xml`, and server YANG model implements the trivial servers
that updates the NTP and DNS server lists on three netsim devices, `ex0-ex2`.
Two YANG actions, three for the Python variant, called  `update-ntp-*`, set the
same configuration as the NTP service to the NTP server list of the device
configuration without or with retry code.

Two actions for acquiring and releasing a semaphore in the service Python and
Java code halt, cause conflicts, and trigger retries to the DNS service and NTP
config actions. The actions, just as the services do, update the configuration
after reading from a YANG-defined name to the IP address mapping list before
committing changes to the database and the devices.

The NTP service causes a conflict and triggers a retry by changing the name to
the IP mapping list configuration from the service code itself.

Running the Example
-------------------

To run the Python variant of the example:

    make showcase-py

The Java variant:

    make showcase-java

The scripts will:

1. Trigger service conflicts handled through the automatic retry mechanism.
2. Trigger a conflict that NSO handle through the CLI or JSON-RPC (WebUI)
   automatic rebase & retry mechanism as the resulting configuration is the
   same despite the conflict.
3. Trigger conflicts handled through the Python or Java API retry mechanisms.

After you are finished with the example, stop the running processes:

     make stop

Details of what the example does are printed by the scripts. The service
and action application code can be consumed in the following order:

* `Makefile`
* `showcase_py.sh` or `showcase_java.sh` - Print details for a step-by-step
  guide.
* `server-config.yang` - Identical for both packages in the
  `package-repository` folder.
* `serverconfigRFS.py` or `serverconfigRFS.java` - Implement the same service
  and action functionality that can be used as a reference.
* `dns-config-template.xml` and `ntp-config-template.xml` - Identical for both
  packages.
* `router.yang`, `router-dns.yang`, `router-ntp.yang` - The YANG models for the
  netsim router devices that make up the configuration mapped to by the
  example.
* The `ncs-python-vm-server-config-python.log`, `ncs-java-vm.log`, and
  `devel.log` logs that the showcase shell scripts show highlights from.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: NSO Concurrency Model
+ The Python and Java API reference documentation covering the API calls in the
  `serverconfigRFS.py` and `serverconfigRFS.java` example implementations.
