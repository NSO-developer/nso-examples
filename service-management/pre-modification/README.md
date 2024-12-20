Service Pre-modification Callback Example
=========================================

This example illustrates using the NSO `pre-modification` callback to
alter device data outside the FASTMAP algorithm. In this example, a new DNS
server is configured from the `pre-modification` callback.

This example uses a simple VPN endpoint service package, `vpnep`, which
illustrates a service implemented by a Java application configuring an
interface as a VPN endpoint on a router. However, a prerequisite for the VPN
endpoint service is that the router has a specific DNS server set. The DNS
server should be configured independently on the router if the service instance
is configured for the router.

The DNS server is configured from the Java application implementing the
`pre-modification` callback of the VPN service, which configures it outside of
the FASTMAP algorithm, i.e., the DNS configuration will not be removed by the
FASTMAP algorithm when the VPN endpoint service is removed.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the two packages:

    make all

Start the `ncs-netsim` network:

    ncs-netsim start

Start NSO:

    ncs

This will start NSO, which will load the two packages, load the data models
defined by the two packages, and start the VPN service Java application from
the `vpnep` package.

Sync the configuration from the devices:

    ncs_cli -u admin
    > request devices sync-from

Check the initial DNS server configuration for the devices that will *not yet*
contain a server with an IP address `10.10.10.1`.

    > show configuration devices device * config r:sys dns
    server 10.2.3.4;

Next, create and delete two VPN service instances. Since we remove the
services, FASTMAP will remove the configuration created by the service. Still,
the DNS server with IP address `10.10.10.1`, configured from the
`pre-modification` callback, will not be removed as the server IP was
configured outside of the FASTMAP algorithm:

    > configure
    % set vpn-endpoints vpn-endpoint s1 router [ ex0 ex1 ex2 ] iface ethX \
      unit 1 vid 2
    % commit dry-run
    % commit

Create a second VPN service instance:

    % set vpn-endpoints vpn-endpoint s2 router [ ex0 ex1 ex2 ] iface ethX \
      unit 2 vid 3
    % commit dry-run
    % commit

Confirm the DNS server was created without FASTMAP service meta-data:

    % show devices device * config r:sys dns | display service-meta-data
    server 10.2.3.4;
    server 10.10.10.1;

Remove both service instances to restore the original state of the device:

    % delete vpn-endpoints vpn-endpoint *
    % commit dry-run
    % commit

Check that FASTMAP did not remove the DNS server `10.10.10.1` configuration,
as the Java service application `pre-modification` callback configured it:

    % show devices device * config r:sys dns
    server 10.2.3.4;
    server 10.10.10.1;

Cleanup
-------

Stop all daemons and clean all created files:

    ncs-netsim stop
    ncs --stop
    make clean

Further Reading
---------------

+ NSO Development Guide: Services Deep Dive
+ The `demo.sh` script