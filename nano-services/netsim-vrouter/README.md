Nano Services for Staged Provisioning of a Virtual Router
=========================================================

*Note*: See the NSO Development Guide Chapter "Nano Services" for a detailed
description of the Nano Services feature and this example.

This example extends the basic-vrouter example to show how to implement a nano
service vrouter where the "virtual machine", vrouter instance, components are
represented by netsim network elements. The netsim network elements are
simulated by ConfD that here acts as a NETCONF server.

There is a single YANG model, `vrouter.yang`, for the vrouter service and the
nano service, while the network elements are represented by the router example
module that has its own package. The nano service behavior tree is tied to a
plan where `vrouter` `day 0` components implement `init`, `vrouter-requested`,
`vrouter-onboarded`, and `ready` states, while the `vrouter-deployed` `day 1`
components implement `init`, `vrouter-configured`, `vrouter-deployed`, and
`ready` states.

When creating a `vrouter` service instance, the `requested` state uses a nano
service post action to invoke a Python action to create and start a netsim
vrouter. The `onboarded` state invokes a Python callback to initialize NSO with
the netsim vrouter started in the previous state, followed by a `sync-from`
performed by a nano service post action. The `configured` state invokes a
service template to configure the netsim vrouter. The `deployed` state has a
pre-condition that waits for a NETCONF notification from the netsim vrouter.
This simulates that the vrouter interfaces are up, after which the nano service
vrouter component enters the `ready` state.

When a vrouter service instance is deleted, the nano service jumps from the
`ready` state to the `configured` state, where the configuration set by this
state is deleted. The `onboarded` state then invokes a Python callback to
delete all NSO vrouter config. Finally, the `requested` state calls a Python
action to stop and delete the netsim vrouter before moving to the `init` state.

The `requested` state Python action callbacks use a semaphore to synchronize as
multiple `vrouter` instances may run the `create` and `delete` action callbacks
concurrently.

The `delete` action callback must also check that the vrouter to `delete` has
been created by the `create` action callback. Otherwise, the `delete` callback
could run before the `create` callback if the vrouter is deleted during
startup.

Running the Example
-------------------

There is a shell script available that runs the example according to the steps
described in the Development Guide documentation. Run the script and create a
new instance of the vrouter service by typing:

    make demo

The above shell script uses the NSO CLI as the northbound interface and a
Python script variant uses the NSO RESTCONF northbound interface and
notification events. Instead of polling for nano service state changes, as
the CLI script does, it uses the `service-state-changes` stream
`plan-notifications` and `commit-queue-notifications` to check when, for
example, a nano service has reached a particular state, or a service commit
queue item has failed. Run it by typing:

    make demo-rc

The `demo.sh` shell script performs the following steps to set up, create,
and initialize the vrouter example:

1. Reset and set up the example

       make stop clean all start

2. Deploy and configure three vrouters through the vrouter nano service, but
   immediately delete the service during init so that the nano service
   backtrack:

       ncs_cli -u admin -C
       # config
       (config)# vrouter vr1 iface eth0 unit 1 vid 1
       (config)# vrouter vr2 iface eth1 unit 2 vid 2
       (config)# vrouter vr3 iface eth2 unit 3 vid 3
       (config)# top
       (config)# commit dry-run
       (config)# commit
       # Type quickly or do this part with a CLI script as the demo.sh does
       (config)# no vrouter
       (config)# commit dry-run
       (config)# commit
       (config)# do show zombies
       (config)# do show side-effect-queue

3. Deploy and configure three vrouters through the vrouter nano service:

       (config)# vrouter vr4 iface eth3 unit 4 vid 4
       (config)# vrouter vr5 iface eth4 unit 5 vid 5
       (config)# vrouter vr6 iface eth5 unit 6 vid 6
       (config)# top
       (config)# commit dry-run
       (config)# commit
       (config)# do show side-effect-queue
       # Wait for the nano service plan to reach the ready state
       (config)# exit
       (config)# show vrouter plan component
       (config)# show running-config devices device

4. Make some configuration changes to the vrouter service:

       (config)# vrouter vr5 iface eth99 unit 99 vid 99
       (config)# top
       (config)# commit dry-run
       (config)# commit
       (config)# show full-configuration devices device vr5 config

5. Delete vrouter service to backtrack. I.e., undeploy vrouters:

       (config)# do show vrouter plan component
       (config)# no vrouter
       (config)# commit dry-run
       (config)# commit
       # Type quickly or do this part with a CLI script as the demo.sh does
       (config)# do show zombies
       (config)# do show side-effect-queue

6. Deploy and configure one vrouter through the vrouter nano service, but set
   the admin state on the netsim device to `locked`:

       (config)# vrouter vr7 iface eth6 unit 7 vid 7
       (config)# commit dry-run
       (config)# commit
       (config)# exit
       # exit

   Type quickly or do this part with a script as `demo.sh` does:

       NETSIM_PORT=$(ncs-netsim get-port vr7)
       $NCS_DIR/netsim/confd/bin/confd_cmd -dd -p $NETSIM_PORT -c \
       'wait-start 1'

7. Set admin state to `locked` on the netsim device operational datastore using
   MAAPI set to make the transaction fail:

       $NCS_DIR/netsim/confd/bin/confd_cmd -dd -o -p $NETSIM_PORT -c \
       'mset "/r:sys/state/admin-state" "locked"'
       ncs_cmd -dd -o -c 'maapi_exists "/vrouter{vr7}/plan/failed"'
       yes

8. Set admin state to `unlocked` in the operational datastore on the netsim
   device using MAAPI set to make the next transaction succeed:

       $NCS_DIR/netsim/confd/bin/confd_cmd -dd -o -p $NETSIM_PORT -c \
       'mset "/r:sys/state/admin-state" "unlocked"'

9. Re-deploy:

       ncs_cli -u admin -C
       # vrouter vr7 re-deploy

   Wait for the nano service plan to reach the ready state:

       # config
       (config)# show vrouter plan component
       (config)# vrouter vr7 plan component vrouter vr7-day0 state onboarded \
                 get-modifications
       (config)# vrouter vr7 plan component vrouter-deployed vr7-day1 state \
                 configured get-modifications
       (config)# show running-config devices device vr7 config

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Nano Services
+ The demo.sh and demo_rc.py scripts
+ The tailf-ncs-plan.yang and tailf-ncs-services.yang modules
