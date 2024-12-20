Service Discovery
=================

This example includes a service, iface, that deploys configuration to an
interface. But network devices already contain existing configuration,
such as provisioned by an older automation system, requiring service
reconciliation.

Start the example by running:

    make demo

The provided import.py Python script reads the list of existing service
instances from the services.txt file, importing them into NSO. However,
the import uses no-deploy commit option to avoid accidentally overwriting
existing configuration.

Once imported, you should invoke the services re-deploy reconcile action,
so the service takes control of the existing configuration.

When you are done with the example, run:

    make stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Development Guide: Services Deep Dive
