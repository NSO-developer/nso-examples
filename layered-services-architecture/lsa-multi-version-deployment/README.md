Layered Services Architecture Multi NSO Version Deployment
===========================================================

This walk-through illustrates different aspects of a Layered Services
Architecture (LSA) cluster where the LSA NSO instances have different NSO
versions.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Follow the steps under 'Manual setup' for a step-by-step guide to get the LSA
service running, or go to the 'Makefile Setup' where those steps have been
in-cooperated into the Makefile.

Manual Setup
------------

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the packages:

    make manual

Start the simulated network and the NSO nodes:

    make start-manual

    DEVICE ex0 OK STARTED
    DEVICE ex1 OK STARTED
    DEVICE ex2 OK STARTED
    DEVICE ex3 OK STARTED
    DEVICE ex4 OK STARTED
    DEVICE ex5 OK STARTED
    cd upper-nso;   NCS_IPC_PORT=4569 sname=upper-nso ncs -c ncs.conf
    cd lower-nso-1; NCS_IPC_PORT=4570 sname=lower-nso-1 ncs -c ncs.conf
    cd lower-nso-2; NCS_IPC_PORT=4571 sname=lower-nso-2 ncs -c ncs.conf
    ./init-manual.sh

Configure the nodes in the cluster from the CFS NSO node (upper-nso):

    make cli-upper-nso

    NCS_IPC_PORT=4569 ncs_cli -u admin
    > configure
    % set cluster device-notifications enabled
    % set cluster remote-node lower-nso-1 authgroup default username admin
    % set cluster remote-node lower-nso-1 address 127.0.0.1 port 2023
    % set cluster remote-node lower-nso-2 authgroup default username admin
    % set cluster remote-node lower-nso-2 address 127.0.0.1 port 2024
    % set cluster commit-queue enabled
    % commit dry-run
    % commit
    % request cluster remote-node lower-nso-* ssh fetch-host-keys

This sets up two remote nodes and configures the CFS node to listen to
notifications from the lower RFS nodes, enabling the commit queue toward these
two lower RFS nodes.

No devices have been configured yet:

    % show devices device
    No entries found.
    % exit
    > exit

To handle the lower NSO node as an LSA node, the correct version of the
`cisco-nso-nc` package needs to be installed. For example, 6.4. Find the
correct version using the `ncs --version` command. Use the first two numbers.

    ln -sf ${NCS_DIR}/packages/lsa/cisco-nso-nc-6.4 upper-nso/packages
    make cli-upper-nso
    > request packages reload

    >>> System upgrade is starting.
    >>> Sessions in configure mode must exit to operational mode.
    >>> No configuration changes can be performed until upgrade has completed.
    >>> System upgrade has completed successfully.
    reload-result {
        package cisco-nso-nc-6.4
        result true
    }

Now that the `cisco-nso-nc` package is in place configure the two lower NSO
nodes:

    > configure
    % set devices device lower-nso-1 device-type netconf ned-id \
      cisco-nso-nc-6.4
    % set devices device lower-nso-1 authgroup default
    % set devices device lower-nso-1 lsa-remote-node lower-nso-1
    % set devices device lower-nso-1 state admin-state unlocked
    % set devices device lower-nso-2 device-type netconf ned-id \
      cisco-nso-nc-6.4
    % set devices device lower-nso-2 authgroup default
    % set devices device lower-nso-2 lsa-remote-node lower-nso-2
    % set devices device lower-nso-2 state admin-state unlocked
    % commit dry-run
    % commit
    % request devices fetch-ssh-host-keys
    fetch-result {
        device lower-nso-1
        result updated
        fingerprint {
            algorithm ssh-ed25519
            value 4a:c6:5d:91:6d:4a:69:7a:4e:0d:dc:4e:51:51:ee:e2
        }
    }
    fetch-result {
        device lower-nso-2
        result updated
        fingerprint {
            algorithm ssh-ed25519
            value 4a:c6:5d:91:6d:4a:69:7a:4e:0d:dc:4e:51:51:ee:e2
        }
    }
    % request devices sync-from
    sync-result {
        device lower-nso-1
        result true
    }
    sync-result {
        device lower-nso-2
        result true
    }

Now, for example, the configured devices of the lower nodes can be viewed:

    % show devices device config devices device | display xpath | \
      display-level 5
    /devices/device[name='lower-nso-1']/config/ncs:devices/device[name='ex0']
    /devices/device[name='lower-nso-1']/config/ncs:devices/device[name='ex1']
    /devices/device[name='lower-nso-1']/config/ncs:devices/device[name='ex2']
    /devices/device[name='lower-nso-2']/config/ncs:devices/device[name='ex3']
    /devices/device[name='lower-nso-2']/config/ncs:devices/device[name='ex4']
    /devices/device[name='lower-nso-2']/config/ncs:devices/device[name='ex5']

or alarms inspected:

    % run show devices device lower-nso-1 live-status alarms summary
    live-status alarms summary indeterminates 0
    live-status alarms summary criticals 0
    live-status alarms summary majors 0
    live-status alarms summary minors 0
    live-status alarms summary warnings 0
    % exit
    > exit

Create an LSA NETCONF NED package named `rfs-vlan-nc-6.4` for the upper CFS
node `packages` directory, which can be used towards the `rfs-vlan` service on
the lower RFS nodes:

    ncs-make-package --no-netsim --no-java --no-python              \
    --lsa-netconf-ned package-store/rfs-vlan/src/yang               \
    --lsa-lower-nso cisco-nso-nc-6.4                                \
    --package-version 6.4 --dest upper-nso/packages/rfs-vlan-nc-6.4 \
    --build rfs-vlan-nc-6.4

No Java or Python code is needed as the `cfs-vlan` service running on the upper
CFS NSO is a pure template service.

The created NED is a `lsa-netconf` NED based on the YANG files of the
`rfs-vlan` service. The version of the NED reflects the version of the NSO on
the lower node. See `--package-version 6.4` above.

The package will be generated in the `packages` directory of the upper NSO
node. See `--dest upper-nso/packages/rfs-vlan-nc-6.4` above.

Install the `cfs-vlan` service:

    ln -sf ../../package-store/cfs-vlan upper-nso/packages
    make cli-upper-nso
    > request packages reload
    > configure

Continue to the section 'Verify the CFS-VLAN Service' section below.

Makefile Setup
--------------

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the packages:

    make all

Start and initialize the simulated network and the NSO nodes:

    make start-all

    ...
    Initialize NSO nodes:
    On lower-nso-1: fetch ssh keys from devices
    On lower-nso-1: perform sync-from
    On lower-nso-2: fetch ssh keys from devices
    On lower-nso-2: perform sync-from
    On upper-nso: fetch ssh keys from devices
    On upper-nso: perform sync-from
    On upper-nso: configure cluster remote nodes: lower-nso-1 and lower-nso-2
    On upper-nso: enable cluster device-notifications and cluster commit-queue
    On upper-nso: fetch ssh keys from cluster remote nodes

Start the CFS node CLI and enter configuration mode:

    make cli-upper-nso
    > configure

Continue to the 'Verify the CFS-VLAN service' section below.

Verify the CFS-VLAN Service
---------------------------

All packages are in place, and we can configure the `cfs-vlan` service:

    % set cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77

The dispatcher code of the `cfs-vlan` service will use the device's location to
dispatch service instance data to the correct RFS node.

Review the configuration changes before committing:

    % commit dry-run
    ...
        local-node {
            data  devices {
                    device lower-nso-1 {
                        config {
                            services {
                +                vlan v1 {
                +                    router ex0;
                +                    iface eth3;
                +                    unit 3;
                +                    vid 77;
                +                    description "Interface owned by CFS: v1";
                +                }
                            }
                        }
                    }
                    device lower-nso-2 {
                        config {
                            services {
                +                vlan v1 {
                +                    router ex5;
                +                    iface eth3;
                +                    unit 3;
                +                    vid 77;
                +                    description "Interface owned by CFS: v1";
                +                }
                            }
                        }
                    }
                }
    ...

For example, as `ex0` resides on `lower-nso-1`, that part of the configuration
goes there, and the `ex5` part goes to `lower-nso-2`.

The `dry-run` output also shows the device configuration changes calculated for
the RFS nodes:

    lsa-node {
        name lower-nso-1
        data  devices {
                  device ex0 {
                      config {
                          sys {
                              interfaces {
            +                    interface eth3 {
            +                        enabled;
            +                        unit 3 {
            +                            enabled;
            +                            description
                                             "Interface owned by CFS: v1";
            +                            vlan-id 77;
            +                        }
            +                    }
                              }
                          }
                      }
                  }
              }
    ...

Commit the changes to the RFS nodes using commit queues:

    % commit commit-queue sync
    % exit
    > exit

The above service creation first dispatches the service instance data to the
RFS nodes using the template VLAN service package template on the CFS node:

    cat ./upper-nso/packages/cfs-vlan/templates/cfs-vlan-template.xml

The template implements some useful techniques in the dispatching process that
are worth looking into.

On the RFS nodes, the service is created using the VLAN service packages
template on the RFS node:

    cat ./lower-nso-1/packages/rfs-vlan/templates/rfs-vlan-template.xml

To review the configuration changes, use the `get-modifications` action:

    make cli-upper-nso
    > request cfs-vlan v1 get-modifications

    ...
    local-node {
    ...
      data devices {
                device ex0 {
                    config {
                        sys {
                            interfaces {
            +                    interface eth3 {
            +                        enabled;
            +                        unit 3 {
            +                            enabled;
            +                            description
                                             "Interface owned by CFS: v1";
            +                            vlan-id 77;
            +                        }
            +                    }
                            }
                        }
                    }
                }
            }
    ...

All configuration data changes are displayed on the local CFS node and the
remote RFS nodes.

    > exit

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Layered Service Architecture
+ The `demo.sh` script
