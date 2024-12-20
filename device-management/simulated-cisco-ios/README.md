Configuring Three Simulated Cisco IOS Routers
=============================================

This example will show how to set up a simulated network of Cisco IOS routers
and how to manage these with NSO. NSO will use Cisco IOS CLI commands to manage
the routers.

NSO interfaces the devices by using Network Element Drivers, NEDs. NSO ships
with a few example NEDs under `$NCS_DIR/packages/neds`. See the NSO
Administration Guide chapter NED Administration regarding Cisco-provided
production NEDs and the NSO Development Guide chapter Developing NEDs for
documentation on how to develop NEDs for different protocols.

NEDs are provided in packages. Packages are a structured way of adding software
to NSO that can contain NEDs, user Interface components, service
applications like VPN activation, etc.

The most important part of the NED is the YANG data model. It specifies which
parts of the device NSO can, for example, configure. For NSO to work with the
configuration commands you need in your scenario, the corresponding YANG model
must cover those parts. It is a essential first phase in any NSO deployment to
make sure that the required NEDs and corresponding YANG models cover the
configuration scenarios.

To understand the capabilities of the simulated Cisco IOS device used in this
example, you can print a tree structure of the YANG model as described by IETF
RFC 8340 using the NSO yanger tool:

    yanger -W none -f tree \
    $NCS_DIR/packages/neds/cisco-ios-cli-3.0/src/yang/tailf-ned-cisco-ios.yang

The NEDs can also be used by the NSO network simulator, netsim, to simulate
the management interface of devices.

To run the `yanger` command above and steps below in this README from a demo
shell script after you sourced the `ncsrc` file (see preparations below):

    ./demo.sh

Preparations
------------

1. Ensure you have sourced the `ncsrc` file in `$NCS_DIR` to set up paths and
   environment variables to NSO. This must be done before running NSO. Adding
   it to your profile is recommended.

2. Optionally, if you do not want to create the example files in this directory
   when, for example, several users are sharing the same NSO installation, you
   can run the example in a separate directory.

3. Create an empty directory, for instance, in your home directory. NSO and the
   simulator will create files and directories in this example. Change to this
   directory before continuing.

Setting up and Running the Simulator
------------------------------------

The package describing the device is
`$NCS_DIR/packages/neds/cisco-ios-cli-3.0`. The `ncs-netsim create-network`
command takes three parameters: the NED package, number of simulated devices,
and the name prefix.

Create the simulated network:

    ncs-netsim create-network $NCS_DIR/packages/neds/cisco-ios-cli-3.0 3 c

This creates the simulated network in a `./netsim` directory. The simulated
network consists of three devices, `c0`, `c1`, and `c2`, which can be managed
using Cisco I-style CLI commands.

Start the simulated devices:

    ncs-netsim start

Run the I-style CLI towards one of the devices:

    ncs-netsim cli-i c1
    admin connected from 127.0.0.1 using console *

    c1> enable
    c1# show running-config

     class-map m
     match mpls experimental topmost 1
     match packet length max 255
     match packet length min 2
     match qos-group 1
    !
    ...
    c1# exit

This shows that the device has some initial config. Try netsim help to list all
available commands:

    ncs-netsim -h

Setting up and Starting NSO
---------------------------

Next, set up and configure NSO with the above devices. The `ncs-setup` command
can take an existing netsim directory and create an NSO environment ready to
run toward the simulated devices. Remember, the `ncs-netsim` step above
created a `./netsim` directory.

   ncs-setup --netsim-dir ./netsim --dest .

This performs the following:
   - Create directories needed for NSO (`ncs-cdb`, `state`, `logs`, `packages`)
   - Link the `cisco-ios` NED package into the `packages` directory
   - Populates the NSO database with meta-data about the simulated devices. IP
     address, port, SSH host keys, authentication, and NED type.

Start NSO:

    ncs

Ensure that the netsim devices still exist and are running from the previous
steps:

    ncs-netsim list
    ncs-netsim is-alive

Start the NSO J-style CLI:

    ncs_cli -u admin

    > show packages packages package cisco-ios
    ...
    component cisco-ios
      ned cli ned-id cisco-ios-cli-3.0
    ...

    > show configuration devices device
    device c0 {
        address   127.0.0.1;
        port      10022;
        ssh {
            host-key ssh-rsa {
                key-data "...";
            }
            host-key ssh-ed25519 {
                key-data ...;
            }
        }
        authgroup default;
        device-type {
            cli {
                ned-id cisco-ios-cli-3.0;
            }
        }
        state {
            admin-state unlocked;
        }
    }
    device c1 {
        address   127.0.0.1;
        port      12023;
    ...

This shows the populated meta-data and configuration for the simulated devices.
They all run at localhost but with different ports. NSO matches the devices
with the NED using the `ned-id`.

Using the NSO CLI, you can show the device status or change the device
configuration. At this point, NSO is unaware of whether the devices exist
or their configurations. So the first step will be to connect to
the devices and read up their configurations into NSO:

    > request devices connect
    connect-result {
        device c0
        result true
        info (admin) Connected to c0 - 127.0.0.1:10022
    }
    ...

    > request devices sync-from
    sync-result {
        device c0
        result true
    }
    ...

View the configuration of the `c0` device:

    > show configuration devices device c0 config

Show a particular piece of configuration from several devices:

    > show configuration devices device c0..2 config ios:router

Enter configuration mode to change the configuration:

    > configure
    Entering configuration mode private

Add some configuration across the devices:

    % set devices device c0..2 config ios:router bgp 64512 neighbor 1.2.3.4 \
    remote-as 2

We must pause here and explain how NSO applies configuration changes to the
network. The changes are local to NSO, and nothing has been sent to
the devices yet. Since the NSO Configuration Database, CDB, is in sync with the
network, NSO can always calculate the minimum diff to apply the changes to the
network. The command below compares the ongoing changes with the running
database:

    % compare running brief
     devices {
         device c0 {
             config {
                 ios:router {
                     bgp 64512 {
                         neighbor 1.2.3.4 {
    -                        remote-as 1;
    +                        remote-as 2;
    ...

Or

    % commit dry-run

The changes can be committed to the devices and the NSO CDB in one go. In the
`commit` command below, we pipe to `details` to see what is going on.

    % commit | details

A common misunderstanding is that you have to synchronize NSO and the devices
after any changes. This is not the case, when changes are committed they are
applied to the devices and CDB in one transaction. If any of the devices fails,
nothing happens, NSO will roll back any changes made to any of the devices and
the NSO CDB will not be updated. Also, as seen by the details output, NSO
stores a `rollback` file for every commit so the whole transaction can be
rolled back manually.

Take a look at the `rollback` file:

    % run file show logs/rollback10006
    # Created by: admin
    # Date: 2012-11-05 10:06:02
    # Via: cli
    # Type: delta
    # Label:
    # Comment:
    # No: 10006

    devices {
        device c0 {
            config {
                ios:router {
                    bgp 64512 {
                        neighbor 1.2.3.4 {
                            remote-as 1;
                        }
    ...

Load the rollback file:

    % rollback 10006

Show the diff:

    % compare running brief
     devices {
         device c0 {
             config {
                 ios:router {
                     bgp 64512 {
                         neighbor 1.2.3.4 {
    -                        remote-as 2;
    +                        remote-as 1;
                         }
    ...

Or:

    % commit dry-run

Commit the `rollback`:

    % commit

To see what is going on between NSO and the device CLI enable the trace:

    % set devices global-settings trace raw trace-dir logs

Trace settings only take effect for new connections so `disconnect`:

    % run request devices disconnect

Make a change to, for example, `c0`:

    % set devices device c0 config ios:interface FastEthernet 1 ip address \
    primary address 192.168.1.1 mask 255.255.255.0
    % commit
    % exit
    > exit


Inspect the CLI trace from the 'c0' device communication:

    cat logs/ned-cisco-ios-cli-3.0-c0.trace

The above can also be done using the NSO Web UI at http://127.0.0.1:8080.

Cleanup
-------

Stop NSO and the netsim devices:

    ncs-netsim stop
    DEVICE c0 STOPPED
    DEVICE c1 STOPPED
    DEVICE c2 STOPPED

    ncs --stop

If you want to restore the example to the initial configuration:

    ncs-netsim reset
    ncs-setup --reset

Further Reading
---------------

+ NSO Administration Guide: NED Administration
+ The `demo.sh` script
