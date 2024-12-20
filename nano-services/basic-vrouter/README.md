Nano Services for Staged Provisioning of a Virtual Router
=========================================================

Note: See the NSO Development Guide Chapter "Nano Services" for a detailed
description of the Nano Services feature and this example.

Services ideally perform all the configurations at once, with all the benefits
of a transaction, such as automatic rollback and cleanup on errors. For nano
services, this is not possible in the general case.

Instead, a nano service performs as much configuration as possible at the
moment and leaves the rest for later according to a plan. When an event occurs
that allows the plan to move ahead and more work to be done, the nano service
instance resumes provisioning, using a `re-deploy` action, called
`reactive-re-deploy`. It allows the service to follow a plan to perform
additional configuration that was not possible before. The process of automatic
re-deploy, called Reactive FASTMAP, is repeated at each step of the plan until
the service is fully provisioned.

This is most evident with, for example, provisioning a virtual instance, here a
virtual router (vrouter) during orchestration of a network function implemented
in one or several containers or virtual machines.

Consider a service that deploys and configures a router in a container or VM.
When the service is first instantiated, it starts provisioning a vrouter.
However, it will likely take some time before the vrouter has booted up and is
ready to accept the new configuration. In turn, the service cannot configure
the router just yet. The service has to wait for the router to become ready.
That is the event that triggers a re-deploy and the service can finish
configuring the router.

This example shows how to implement a nano service vrouter where both the
`vrouter` service and "virtual machine", `vm-instance`, component are simply
represented by entries in YANG lists.

There is just one single YANG model, vrouter.yang, for the `vrouter` service,
the `vm-instance`, and the nano service. The nano service behavior tree is tied
to a plan with four states that each vrouter component follows: `init`,
`vm-requested`, `vm-configured`, and `ready`. Only the `vm-requested`, and
`vm-configured` states implement a behavior tied to them.

The `vm-requested` state invokes a create callback implemented by a Python
script, `main.py`, that creates the virtual router, i.e. the `vm-instance` list
entry. The `vm-configured` state invokes a service template that maps and adds
configuration to the virtual router `vm-instance` list entry.

The `vm-configured` state also has a pre-condition that checks a leaf of type
boolean that holds a `vm-up-and-running` state. When the `vm-up-and-running`
state is set to `true` the nano service plan transition to the ready state.

Running the Example
-------------------

There is a shell script available that runs the example according to the
steps described in the Development Guide documentation. Run the script and
create a new instance of the vrouter service by typing:

    make demo

The above shell script uses the NSO CLI as the northbound interface, and there
is also a Python script variant that uses the NSO RESTCONF northbound interface
and notification events. Instead of polling for nano service state changes, as
the CLI script does, it uses service plan notifications to check when, for
example, a nano service has reached a particular state. Run it by typing:

    make demo-rc

The demo.sh CLI shell script performs the following steps to setup, create,
and initialize the vrouter example:

1. Reset and setup the example

        make stop clean all start

2. Create and initialize a `vrouter` instance

        ncs_cli -u admin -C
        # config
        (config)# vrouter vr-01
        (config)# commit dry-run
        (config)# commit
        (config)# exit
        # Wait for the nano service plan to reach the ready state

3. Set the `vm-up-and-running` leaf to `true`

        # show vrouter vr-01 vm-up-and-running
        # show vrouter vr-01 plan
        # vrouter vr-01 get-modifications
        # show running-config vrouter
        # show running-config vm-instance

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Nano Services
+ The demo.sh and demo_rc.py scripts
