NSO Commit Parameters Showcase
==============================

This example shows how to work with NSO commit parameters from Python and Java
user code.

The example consists of Python and Java packages plus helper scripts that:

* Augment the shared `tailf-ncs-commit-params` model with a custom
  `audit-context/ticket-id` commit parameter
* Detect built-in commit parameters in Python and Java service code
* Detect the augmented `audit-context/ticket-id` parameter in Python and Java
  user code
* Apply commit parameters from user code through MAAPI
* Build and run the example from a local `nso-run` directory

The `demo.sh` walkthrough first drives the service callbacks from the CLI so
that the Python and Java service code can inspect commit parameters received
from the transaction. It then invokes the Python and Java actions, which set
`label`, `dry-run`, and the augmented `audit-context/ticket-id` parameter from
user code before applying the transaction through MAAPI.

The service instances live under the root containers
`commit-params-py/commit-params-py-instances` and
`commit-params-java/commit-params-java-instances`.

Running the Example
-------------------

    make demo

This example uses Java and Python packages to apply commit parameters to a
transaction and detect them from service code. `make all` creates a local
`nso-run` directory with the built packages and starts the simulated router
from there.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ The Python and Java packages in the `./package-repository` directory
+ NSO Development Guide: API Overview
+ NSO Development Guide: Commit Parameters
+ Python API reference documentation: `ncs.maapi` and `ncs.maagic`.
+ Java API reference documentation: `com.tailf.maapi.Maapi`.
+ `examples.ncs/northbound-interfaces/commit-parameters`
