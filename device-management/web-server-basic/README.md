NSO Device Manager Example
==========================

Introduction
------------

This example showcases the NSO device manager. A local installation of NSO is
required to run the example. The `NCS_DIR` variable must be set up to point to
the installation, preferably by sourcing the `ncsrc` script, which is found in
the top dir of the installation.

For this example, We have the following network where we manage one load
balancer and three web servers:

                    NSO
                     |
                     |
     --------------------------
     |       |         |      |
     |       |         |      |
    lb0     www0      www1   www2   --- simulated network, ConfD agents

The simulated network of managed devices, `lb0` and `www{0,1,2}`, are
represented by four ConfD netsim instances running at `localhost` but on
different ports. The `ncs-netsim` tool is used to simulate the network.

We have two different types of managed devices. An NSO package defines each
device. In the directory `./packages`, there are two packages:

* `lb` - simulates a load balancer
* `webserver` - simulates a webserver

Before managing a device, we must have an NSO package for it. The package
contains, at a minimum, the YANG models for the device. When compiling a
package for a NETCONF device, defined by YANG models only, the `Makefile` in
the package must use the `ncsc` compiler command.

    ncsc --ncs-compile-bundle <path-to-yangfiles> --ncs-device-dir ./some-dir

The above command is executed automatically from the Makefiles in the packages
when you build the `all` target for this example.

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Building the Example
--------------------

To build the example:

    make all

Both types of managed devices, `lb` and `webserver`, are compiled, and an
execution environment is created for all four managed devices.

Running the Example
-------------------

Start the simulated network:

    ncs-netsim start

This starts the four simulated devices, `lb0`, `www0`, `www1` and `www2`.

Start NSO:

    ncs

NSO is initialized with managed device data configuring the `tailf-ncs`
namespace with data needed to manage the devices, such as the IP address and
port of each managed device as well as authentication data, i.e., how we
establish an SSH connection to each managed device.

For example, the configuration for `lb0`:

    ncs_cli -u admin

    > show configuration devices device lb0
    address   127.0.0.1;
    port      12022;
    authgroup default;
    state {
        admin-state unlocked;
    }

At this point, we're ready to start managing the devices. We can now run
commands to reconfigure our network of managed devices. Each device
configuration can be found under `/ncs/devices/device[name=Name]/config`. For
example, the config for `lb0` in NSO is found under
`/ncs/devices/device[name='lb0']/config`

There is no configuration from the device stored in NSO yet. The first step is
thus to sync the configuration stored in the devices to NSO. Let's go through
the different options we have at our disposal to synchronize NSO and the
devices.

First, we can execute:

    > request devices device lb0 check-sync
    result unknown

The above is what we refer to as a cheap sync check if the device supports the
tailf proprietary monitoring namespace as in:

    > show devices device lb0 capability
    ...
    capability http://tail-f.com/yang/common-monitoring
    ...

The cheap check is done by retrieving a single string from the device. The
string identifies the last committed transaction as status data at the device.
Let's go to the device inside this example and have a look at that string:

        ncs-netsim cli lb0
        > show status confd-state internal cdb datastore running transaction-id
        transaction-id 1265-621669-781403;

The above is thus a direct CLI connection into one of the devices managed by
NSO. The `ncs-netsim` tool "knows" on which port the `lb0` ConfD netsim device
listens for CLI connections.

There are three different ways we can make a device come in sync with regards
to the `transaction-id`:

- sync the config from NSO to the device
- sync the config from the device to NSO
- run a diff that results in nothing

Enter the CLI of NSO. Let's start with a diff:

    > request devices device lb0 compare-config
    diff
    devices {
        device lb0 {
            config {
    +            interface eth0 {
    +                macaddr aa:bb:cc:33:22:36;
    +            }
                lb:lbConfig {
                    system {
    +                    ntp-server 18.4.5.6;
                        resolver {
    +                        nameserver 18.4.5.6;
    +                        search acme.com;
                        }
                    }
                }
            }
        }
    }

Alternatively, with the output in XML format:

    > request devices device lb0 compare-config outformat XML
    diff
    <lbConfig xmlns="http://pound/lb">
      <system>
        <ntp-server>18.4.5.6</ntp-server>
        <resolver>
        <nameserver>18.4.5.6</nameserver>
        <search>acme.com</search>
        </resolver>
      </system>
    </lbConfig>
    <interface xmlns="http://acme.com/if">
      <name>eth0</name>
      <macaddr>aa:bb:cc:33:22:36</macaddr>
    </interface>

The above two diffs denote what we - at NSO - have to merge into NSO to be in
sync with the device. So let's do that:

    > request devices device lb0 sync-from
    result true
    > request devices device lb0 check-sync
    result in-sync
    > show configuration devices device lb0 config
    interface eth0 {
        macaddr aa:bb:cc:33:22:36;
    }
    lb:lbConfig {
        system {
            ntp-server 18.4.5.6;
            resolver {
                search     acme.com;
                nameserver 18.4.5.6;
            }
        }
    }

We're now in sync for `lb0`. Let's sync all devices:

    > request devices sync-from
    sync-result {
        device lb0
        result true
    }
    sync-result {
        device www0
        result true
    }
    sync-result {
        device www1
        result true
    }
    sync-result {
        device www2
        result true
    }

We can now start making network-wide transactions spanning multiple managed
devices. For example, to turn on the `KeepAlive` feature for all web servers:

    > configure
    Entering configuration mode private
    % set devices device www0..2 config ws:wsConfig global KeepAlive On
    % commit dry-run
    % commit
    Commit complete.

A network-wide rollback of the latest commit:

    % rollback
    % compare running brief
    devices {
        device www0 {
            config {
                ws:wsConfig {
                    global {
    -                    KeepAlive On;
                    }
                }
            }
        }
        device www1 {
            config {
                ws:wsConfig {
                    global {
    -                    KeepAlive On;
                    }
                }
            }
        }
        device www2 {
            config {
                ws:wsConfig {
                    global {
    -                    KeepAlive On;
                    }
                }
            }
        }
    }

    % commit
    Commit complete.

Let's look at how we can push network-wide transactions when one (or more)
of the involved hosts are down. This can be achieved with the commit queue
feature. By default, the commit queue is turned off.

    ncs-netsim stop lb0
    DEVICE lb0 STOPPED

From the NSO CLI:

    % set devices device lb0 config if:interface eth0 macaddr 00:66:77:22:11:22
    % set devices device lb0 config if:interface eth0 ipv4-address 2.3.4.5
    % commit
    Aborted: Failed to connect to device lb0: connection refused

    % commit commit-queue async
    commit-queue-id 11649974378
    Commit complete.

The transaction is now committed through the commit queue. Each transaction
committed becomes a queue item. Each queue item waits for something to happen.
Let's take a look at the commit queue:

    % exit
    > show devices commit-queue

A transient error occurred for `lb0` as it is down and non-operational.
Transient errors are potentially harmful since the queue might grow if new
items are added while waiting for the same device. Retries will take place in
intervals. Let's restart the managed device:

    ncs-netsim start lb0
    DEVICE lb0 OK STARTED

Now monitor the commit queue:

    > show devices commit-queue

Cleanup
-------

When you finish this example, make sure all daemons are stopped. Stop NSO and
the simulated network:

    ncs --stop
    ncs-netsim stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Operation & Usage Guide: NSO Device Manager
+ The `demo.sh` script