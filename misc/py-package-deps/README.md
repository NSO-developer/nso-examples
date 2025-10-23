Python Action Callback Application With Python Package Dependencies
===================================================================

This example demonstrates how to:

- Have the NSO Python VM instance for a `cowlog` package either load the
`cowsay` Python package dependencies (recommended) or have the Python package
activate a Python virtual environment (alternative), `venv`, when it starts.
- Include required Python dependencies in the NSO package `python` directory
(recommended) or a use a Python virtual environment (alternative). In this
case, the `cowsay` Python package dependency by an action that logs a message
using Python code.
- It is recommended to include Python dependencies in the NSO package to make
it self-contained. This is, for example, beneficial in high-availability setups
where packages are copied from primary to secondary nodes.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The steps below reproduce what the demo script does, using the J-style CLI
instead of the C-style CLI.

Build the package, add Python dependencies, and start NSO:

    make all cowlog-deps start

Run the `cowlog` action:

    ncs_cli -u admin
    > request cowlog-action cowlog

Check the log output in `logs/ncs-python-vm.log`:

    cat logs/ncs-python-vm.log

View the `logs/ncs-python-vm-cowlog.log` output:

    cat logs/ncs-python-vm-cowlog.log

Reset:

    make stop clean

Build the package, create the Python virtual environment, add Python
dependencies, and start NSO:

    make all pyvenv start

Run the `cowlog` action:

    ncs_cli -u admin
    > request cowlog-action cowlog

Check the log output in `logs/ncs-python-vm.log` to confirm that the virtual
environment was activated for the NSO Python VM instance of the package:

    cat logs/ncs-python-vm.log

View the `logs/ncs-python-vm-cowlog.log` output:

    cat logs/ncs-python-vm-cowlog.log

- See the `Makefile` for the Python virtual environment `venv` setup created
under the `pyvenv` directory.
- See the `packages/cowlog/python/use_venv` file created by the `Makefile` that
tells the NSO Python VM for the package which virtual environment to use.
- The `packages/cowlog/python/requirements.txt` file lists Python dependencies
for the package.

Cleanup
-------

Stop NSO and remove all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: NSO Python VM
* The `Makefile` Python `venv` setup
* The `packages/cowlog/python/use_venv` file
+ The `packages/cowlog/python/requirements.txt` file
+ The `demo.sh` script
