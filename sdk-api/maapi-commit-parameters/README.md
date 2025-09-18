NSO Commit Parameters Showcase
==============================

This example features examples of detecting and applying different commit
parameters, such as dry run and commit with trace ID, directly from either
Python or Java packages or code.

You need NSO, Python, and Java installed, and the `ncsrc` file sourced
to run this example.

The example is a shell script that runs both examples using the `make` command.
The Java and Python examples feature a service that tries to modify interface
configuration on the `ex0` device, then adds the `dry-run` and `trace-id`
commit parameters and applies the transaction.

NSO automatically generates a trace ID if the `trace-id` parameter is not
provided. The `trace-id` parameter is included by this example for
demonstration purposes.

The examples show how to detect arbitrary and specific commit parameters in
service code and apply commit parameters from code either as an action or from
a script. It also contains a list of all currently existing commit parameters
available to you.

Be aware, though, that some combinations of commit parameters will not work
simultaneously in the same commit, such as `no-lsa` and `use-lsa` or
`dry_run_cli` and `dry_run_native`.

Running the Example
-------------------

    make demo

This example uses Java and Python packages to apply commit parameters to a
transaction and detect them from service code.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: API Overview
+ Python API reference documentation: `ncs.maapi` and `ncs.maagic`.
+ Java API reference documentation: `com.tailf.maapi.Maapi`.
+ The Python and Java packages in the `./package-directory` directory
