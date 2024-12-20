Website Service Example
=======================

In the `examples.ncs/device-management/web-server-basic` example, we got an
introduction to the NSO device manager functionality. In this example, we look
at the service manager functionality within NSO.

We have the following network, where we manage one load balancer and three web
servers.

The following ASCII art describes the network:

                   NSO
                    |
                    |
      --------------------------
      |       |         |      |
      |       |         |      |
     lb0     www0      www1   www2   --- simulated network, ConfD netsim agents


The simulated network of managed devices, `lb0`, and `www{0,1,2}` are
represented by four ConfD netsim instances running at localhost but on
different ports. The `ncs-netsim` tool is used to simulate the network.

In this example, we implement service logic for a service we call the website
service. The role of this service is to provide a service automating the
deployment of a website onto the load balancer and web servers in our network.

The website service comprises the following extensions to the
`web-server-basic` example:

* A Service Model - Website-specific adornments of the generic NSO service
  manager.

* Service Logic
  - Service Validation Logic - Website-specific validation of service
    instantiation.
  - Service Mapping Logic - Mapping the website service instance data onto
    the device layer.

The following ASCII art describes the website model:

                ------------------------
                |                      |
            services                   |
        ---------------                |
        |             |                |
     service     properties        devices
        |             |                |
      type         web-site            |
        |                              |
    web-site                           |
                                       |
                                       |
                           --------------------------
                           |       |         |      |
                           lb0     www0      www1   www2

* `/services/web-site` - the website service model. The service comprises the
  following attributes:
  - `url` - the URL of the website.
  - `ip` - the IP address of the website.
  - `port` - the port to listen to.
  - `description` - a description of the website.
  - `lb-profile` - refers to a configuration comprising load balancers and one
    or more web servers.
* /services/properties/web-site - global settings for the website service
  model.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the packages:

    make all

Start the simulated network and NSO:

    make start

The four simulated devices (lb0,www0,www1,www4), NSO, and the
website service application are running.

Sync the configuration from the devices:

    ncs_cli --user=admin
    > request devices sync-from

Create a load balancer profile:

    > configure
    % set services properties web-site profile gold lb lb0 backend www0
    % commit dry-run
    % commit

Create a website:

    % set services web-site acme port 8080 ip 168.192.0.1 lb-profile gold url www.vip.org
    % commit dry-run
    % commit

Inspect the `lbConfig` and `wsConfig` of the managed devices:

    % show devices device www0..2 config ws:wsConfig
    % show devices device lb0 config lb:lbConfig

Start a CLI session on the `www0` device using the `ncs-netsim` tool and delete
the wsConfig. This is a rogue reconfiguration, as the operator logs in directly
on a managed device and performs a reconfiguration:

    ncs-netsim cli www0
    > configure
    % delete wsConfig
    % commit

Do a `check-sync` from the NSO CLI:

    % request devices check-sync
    ...
    sync-result {
        device www0
        result out-of-sync
    }
    ...
    % request devices device www0 compare-config

At this point, NSO and the device have different views on the actual
configuration. If we assume that the device is correct and NSO is wrong, we
sync from the device.

    % request devices device www0 sync-from

Re-deploy the service:

A rogue reconfiguration may also affect services badly. In this case, since we
synced from the device back to NSO, it may be the case that the configuration
changes performed by some of our service instances have been overwritten:

    % request services web-site acme check-sync
    % request services web-site acme check-sync outformat cli
    % request services web-site acme re-deploy
    % request services web-site acme check-sync
    % exit
    > exit

Verify that the `wsConfig` for `www0` has been updated. From the `www0` CLI:

    ncs-netsim cli www0
    > show configuration wsConfig
    listener 192.168.0.9 8008;

The re-deploy performs a forced sync of the device config relating
to the `acme` service.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Implementing Services
+ NSO Operation & Usage Guide: Service Impacting Out-of-band Changes
+ The `demo.sh` script
