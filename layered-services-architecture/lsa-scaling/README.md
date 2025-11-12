Layered Services Architecture Scaling
=====================================

This example illustrates how to write a user-defined RFS "Resource Facing
Service" in an LSA cluster to move the device between lower NSO nodes easily.
It also illustrates how a package can be used for replicating device
configuration to some external store, in our case the device and dRFSs config
is stored in a file system. This allows the lower LSA nodes to be run in non-HA
mode.

Background
----------

There might be situations where allowing easy scaling of the device layer in
LSA is important. Moving a device from one lower node to another is complicated
and usually requires the services that use the device to un-deploy.

In this example, we show that given some restrictions on how the services are
structured on top of LSA, it is possible to facilitate easy movements of
devices between device layer NSO nodes. From that follows, it is easy to add a
new device NSO node and rebalance the lower layer, moving some devices to the
new device NSO and, conversely, shrinking the lower layer by clearing all
devices from a device NSO (dNSO).

The idea is to structure LSA such that the lowest NSO nodes only have devices
and resource-facing service instances touching a single device. We call these
nodes dNSO nodes, and the RFSs exported by these nodes `dRFS`s.

For example:

                upper-nso                   sNSO layer
            /       |        \
          /         |          \
    lower-nso-1 lower-nso-2 lower-nso-3     dNSO layer

This structure moving a device from one dNSO node to another uncomplicated.
What needs to happen is the following, assuming we are moving device `ex0` from
`lower-nso-1` to `lower-nso-2`:

1. A partial lock is acquired on the `upper-nso` for the path:

        /devices/device{lower-nso-1}/config/dRFS/device{ex0}

   to avoid any changes to the device while the device is
   being moved.

2. Extract the device and dRFS configuration in one of two ways:

    - Read the configuration from `lower-nso-1` using the action:

          /device-action/extract_device name=ex0 delete=false

      on `lower-nso-1`.

    - Read the configuration from some central store, could be a DB, or, in
      our case, a file system.

   The configuration is essentially the result of exporting the configuration
   in J-style with unhide all (['*',full]).

   The configuration will look something like this:

        devices {
            device ex0 {
                address   127.0.0.1;
                port      12022;
                ssh {
                ...
                   /* Refcount: 1 */
                    /* Backpointer: [ /drfs:d...1'] ] */
                    interface eth3 {
                    ...
                    }
                ...
            }
        }
        dRFS {
            device ex0 {
                vlan v1 {
                    private {
                    ...
                    }
                }
            }
        }

3. Install the configuration on the `lower-nso-2` node. This can be done by
   running the action:

        /device-action/install-device name=ex0 config=<from above>

   This will load the configuration file and `commit` using `no-fastmap`
   `no-networking`. All backpointers, etc., will be restored.

4. Delete device and dRFS for the device on `lower-nso-1`.

5. Update mapping table, i.e., `/dispatch-map{ex0}/rfs-node`.

6. Release the partial lock for:

        /devices/device{lower-nso-1}/config/dRFS/device{ex0}

7. Read backpointers of the node:

        /devices/device{lower-nso-1}/config/dRFS/device{ex0}

   and invoke `re-deploy no-lsa no-networking` for all services.

8. Run `compare-config` for `/devices/device{lower-nso-1}` and
   `/devices/device{lower-nso-2}` to update the transaction id for
   `lower-nso-1` and `lower-nso-2`.

All of the above is packaged into an action in the `move-device` package. It
can be invoked as:

    request move-device move src-nso lower-nso-1 dest-nso \
    lower-nso-2 device-name ex0

for moving device `ex0` from `lower-nso-1` to `lower-nso-2`. Or reading from
an external DB instead of `src-nso`:

    request move-device move src-nso lower-nso-1 dest-nso \
    lower-nso-2 device-name ex0 read-from-db

Stateless
---------

Data is replicated to an external store using the package `inventory-updater`.
In our case, it stores the configurations as files in the directory `db_store`,
but the package can easily be modified to store the configurations in some
external database, such as, Cassandra.

Requirements
------------

There are some important restrictions. The lower nodes (dNSO) may only export
device RFS models northbound, i.e., RFS that apply to a single device. All such
RFS should be mounted under the path `/drfs:dRFS/device` to make it easy to
extract all service instances for a given device while only locking a limited
portion of the configuration of the dNSO.

Also, there cannot be any RFM on the lower node since event processing
complicates the matter.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

The cluster consists of three nodes: `upper-nso`, `lower-nso-1`, and
`lower-nso-2`.

The node `upper-nso` runs the customer facing service `cfs-vlan`. The service
`cfs-vlan` dispatches the relevant part of the service to the different
resource-facing nodes, `lower-nso-1` and `lower-nso-2`. The lower nodes run the
resource-facing service `rfs-vlan`.

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the packages:

    make all

Start the simulated network and the NSO nodes:

    make start

    DEVICE ex0 OK STARTED
    DEVICE ex1 OK STARTED
    DEVICE ex2 OK STARTED
    DEVICE ex3 OK STARTED
    DEVICE ex4 OK STARTED
    DEVICE ex5 OK STARTED
    cd upper-nso;   NCS_IPC_PORT=4569 sname=upper-nso ncs -c ncs.conf
    cd lower-nso-1; NCS_IPC_PORT=4570 sname=lower-nso-1 ncs -c ncs.conf
    cd lower-nso-2; NCS_IPC_PORT=4571 sname=lower-nso-2 ncs -c ncs.conf
    ./init.sh

Sync the configuration from the remote LSA-nodes:

    make cli-upper-nso
    > request devices sync-from

    sync-result {
        device lower-nso-1
        result true
    }
    sync-result {
        device lower-nso-2
        result true
    }
    sync-result {
        device lower-nso-3
        result true
    }

Note the only "devices" that are present are the remote LSA nodes.

    > configure
    % set cfs-vlan v1 a-router ex0 z-router ex5 iface eth3 unit 3 vid 77

The dispatcher code of the `cfs-vlan` service will use the dispatch-map to
dispatch service instance data to the correct lower node:

    % show dispatch-map
    dispatch-map ex0 {
        rfs-node lower-nso-1;
    }
    dispatch-map ex1 {
        rfs-node lower-nso-1;
    }
    dispatch-map ex2 {
        rfs-node lower-nso-1;
    }
    dispatch-map ex3 {
        rfs-node lower-nso-2;
    }
    dispatch-map ex4 {
        rfs-node lower-nso-2;
    }
    dispatch-map ex5 {
        rfs-node lower-nso-2;
    }

Commit the changes to the lower nodes using commit queues:

    % commit dry-run
    % commit commit-queue sync
    % exit
    > exit

The above service creation first dispatches the service instance data to the
lower nodes through the CFS VLAN template:

    cat ./upper-nso/packages/cfs-vlan/templates/cfs-vlan-template.xml

Moving a Device
---------------

To move device `ex0` from `lower-nso-1` to `lower-nso-2`, you can use the
command:

    > request move-device move src-nso lower-nso-1 dest-nso lower-nso-2 \
    device-name ex0

To see what has been modified, use the `get-modifications` action:

    > request cfs-vlan v1 get-modifications
    cli {
        local-node {
            data  devices {
                      device lower-nso-2 {
                          config {
                              drfs:dRFS {
                  +                device ex0 {
                  +                    vlan v1 {
                  +                        router ex0;
                  +                        iface eth3;
                  +                        unit 3;
                  +                        vid 77;
                  +                        description
                                               "Interface owned by CFS: v1";
                  +                    }
                  +                }
                  ...
                              }
                          }
                      }
                  }
        }
        lsa-service {
        ...
        }
        lsa-service {
        ...
        }
    }

The device can also be moved by reading the configuration from a
common store, for example, if the source `dNSO` isn't available:

    > request move-device move src-nso lower-nso-2 dest-nso lower-nso-1 \
      device-name ex0 read-from-db

To see what has been modified, use the `get-modifications` action:

    > request cfs-vlan v1 get-modifications
    cli {
        local-node {
            data  devices {
                      device lower-nso-1 {
                          config {
                              drfs:dRFS {
                  +                device ex0 {
                  +                    vlan v1 {
                  +                        router ex0;
                  +                        iface eth3;
                  +                        unit 3;
                  +                        vid 77;
                  +                        description
                                               "Interface owned by CFS: v1";
                  +                    }
                  +                }
                              }
                          }
                      }
                      ...
                  }
        }
        lsa-service {
        ...
        }
        lsa-service {
        ...
        }
    }

Re-balancing the Lower Layer
----------------------------

The `move-device` package also contains an action for re-balancing the lower
LSA layer, i.e., to move devices around such that there is an even distribution
of devices among the nodes in the lower layer. This may, for example, be useful
when adding a new lower LSA node to a cluster.

    > request move-device rebalance dry-run
    result move ex5 from lower-nso-2 to lower-nso-3
    move ex2 from lower-nso-1 to lower-nso-3

It is also possible to provide the action with a set of nodes to balance,
for example, `lower-nso-2` and `lower-nso-3`:

    > request move-device rebalance nodes [ lower-nso-2 lower-nso-3 ] dry-run
    result move ex4 from lower-nso-2 to lower-nso-3

If we perform the action:

    > request move-device rebalance
    result move ex5 from lower-nso-2 to lower-nso-3
    move ex2 from lower-nso-1 to lower-nso-3

And then inspect the `dispatch-map`:

    > show configuration dispatch-map
    dispatch-map ex0 {
        rfs-node lower-nso-1;
    }
    dispatch-map ex1 {
        rfs-node lower-nso-1;
    }
    dispatch-map ex2 {
        rfs-node lower-nso-3;
    }
    dispatch-map ex3 {
        rfs-node lower-nso-2;
    }
    dispatch-map ex4 {
        rfs-node lower-nso-2;
    }
    dispatch-map ex5 {
        rfs-node lower-nso-3;
    }

We can see that the devices are evenly distributed among the devices.

Evacuating a Lower LSA Node
---------------------------

Similarly, it may be useful to evacuate all devices from a lower LSA node to
make it possible to remove the node, for example, if the number of devices
managed by the cluster is shrinking or if a lower LSA node should be serviced
or replaced.

The action `evacuate` moves all devices on a node to the destination nodes,
trying to get an even balance of devices on the destination nodes. If no
destination nodes are given, the devices are distributed among all other
lower LSA nodes in the cluster.

The following command will move all devices on `lower-nso-1` to the other
lower LSA nodes in the cluster:

    > request move-device evacuate node lower-nso-1 dry-run
    result move ex1 from lower-nso-1 to lower-nso-2
    move ex0 from lower-nso-1 to lower-nso-3

However, moving all devices from `lower-nso-1` to `lower-nso-3` is possible
using the `dest-nodes` parameter:

    > request move-device evacuate node lower-nso-1 dest-nodes [ lower-nso-3 ]
    result move ex1 from lower-nso-1 to lower-nso-3
    move ex0 from lower-nso-1 to lower-nso-3

    > show configuration dispatch-map
    dispatch-map ex0 {
        rfs-node lower-nso-3;
    }
    dispatch-map ex1 {
        rfs-node lower-nso-3;
    }
    dispatch-map ex2 {
        rfs-node lower-nso-3;
    }
    dispatch-map ex3 {
        rfs-node lower-nso-2;
    }
    dispatch-map ex4 {
        rfs-node lower-nso-2;
    }
    dispatch-map ex5 {
        rfs-node lower-nso-3;
    }

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Layered Service Architecture
+ The `demo.sh` script