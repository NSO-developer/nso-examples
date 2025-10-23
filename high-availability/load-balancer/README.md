High Availability Setup with a Load Balancer
============================================

An important component in many NSO HA deployments is a single Virtual IP
address (VIP) that users and other applications can use to access the
currently active primary node. This can be implemented by sharing a VIP when
NSO nodes reside on the same L2 subnet or using a routing protocol, such as
BGP, when they don't.

Alternatively, a load balancer can listen on the VIP address and route
connections to the primary node. Here, a preconfigured health check tracks
the state of all NSO nodes and allows a load balancer to know which is the
current primary.

This example uses the HAProxy software (https://www.haproxy.org/) for a load
balancer. If you wish to run the example, you must first install HAProxy.
HAProxy comes prepackaged for many Linux distributions, allowing you to use
a command similar to the following:

    sudo apt-get install haproxy

or

    brew install haproxy  # for MacOS

The configuration file expects, and was tested with, HAProxy version 2.
Of course, a different load balancer or reverse proxy system, software or
hardware one, can be used in your deployment if it supports health checks
based on HTTP status code.

Both NSO nodes in the example use a custom ha_status package to publish
information through HTTP on which node is the primary. This allows HAProxy
to proxy incoming connections to the SSH CLI (port 2024) and RESTCONF/web UI
(port 8080) to the right node. The ha_status package uses a different, custom
port 8765, in order to make the HA status accessible to load balancer without
authentication.

Running the Example
-------------------

To run the example, execute:

    make all start

The script will configure two NSO nodes locally, in an HA setup, using the
loopback interfaces 127.0.1.1 and 127.0.2.1. On some systems, such as macOS,
you might need to configure them explicitly, for example:

macOS setup:

    sudo ifconfig lo0 alias 127.0.1.1/24 up
    sudo ifconfig lo0 alias 127.0.2.1/24 up

macOS cleanup:

    sudo ifconfig lo0 -alias 127.0.1.1
    sudo ifconfig lo0 -alias 127.0.2.1

The script will then start the `haproxy` command with the provided
`haproxy.conf` configuration file, listening on the localhost (127.0.0.1)
address, allowing you to connect to the primary node:

    ssh -p 2024 admin@localhost

Once connected, you can disable the HA on the first `n1` node, simulating a
node failure:

    admin@n1> request high-availability disable

In a couple of seconds, the load balancer will pick up the change and new
connections with

    ssh -p 2024 admin@localhost

will go to the fail-over `n2` node.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Setup with an External Load Balancer
+ Basic NSO Rule-based High Availability Example
