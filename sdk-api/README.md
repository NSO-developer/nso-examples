SDK API Examples
================

Python, Java, and Erlang APIs and other ways to extend NSO.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### maapi-py
A trivial example that showcases the benefit of the high-level Python API. It
uses standalone Python applications to read data from CDB using low-level and
high-level MAAPI.

### maapi-java
A collection of Java examples that use the MAAPI and NAVU API.

### commit-parameters
Showcase how to detect and apply different commit parameters directly from
Python or Java packages or code, such as `dry-run` and `commit` with trace ID.

### actions-py
Illustrates how to define a YANG action, attach Python code behind the action,
and write Python code that invokes actions.

### actions-java
Illustrates how to define a YANG action, attach Java code behind the action,
and write Java code that invokes actions.

### action-abort-py
Illustrates how to implement an abortable Python action that spawns a separate
worker process using the multiprocessing library and returns the worker's
outcome via a result queue or terminates the worker if the action is aborted.

### cdb-py
Showcase a few different ways to subscribe to changes in the CDB configuration
and operational data stores from a Python application.

### cdb-java
Showcase a few different ways to subscribe to changes in the CDB configuration
and operational data stores from a Java application. Used by the NSO
Development Guide chapter Using CDB.

### cdb-twophase
Demonstrates a two-phase CDB mandatory subscriber that will iterate over the
changed configuration during the prepare phase of the transaction and abort the
transaction if the number of devices with configuration changes exceeds a
limit.

### event-notifications
Demonstrates how to use the Python `_ncs.events` low-level module for
subscribing to and processing NSO event notifications. The Python script used
with this example can also be used as a standalone application for debugging
any NSO instance.

### alarms
Illustrates how to submit alarms to NSO using the Java `AlarmSinkCentral`.

### scripting
Illustrates how to use plug-and-play scripts to add CLI commands, policy
scripts, and post-commit callbacks.

### external-logging
A Python script, intended as a development feature, demonstrates how to use an
external application to filter NED trace log data, here CLI trace data.
However, the feature works with any external trace output by reading the log
data from standard input and then processing it.

### external-db
Showcase how to incorporate data with NSO where the data is stored outside of
NSO in another database. Used by the NSO Development Guide chapter Java API
Overview under DP API.

### external-encryption-keys
Demonstrates how to use an external Python application to configure the
built-in NSO crypto types' encryption keys as an option to configure the keys
in `ncs.conf`. Used by the NSO Development Guide chapter Encryption Keys.
