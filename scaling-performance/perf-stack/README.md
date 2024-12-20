Optimizing Transaction Wall-Clock Time Performance with Stacked Services
========================================================================

The `perf-trans` example service use one transaction per service instance
where each service instance configures one device. This enables transactions to
run concurrently on separate CPU cores in a multi-core processor. The example
sends RESTCONF PATCH requests concurrently to start transactions that run
concurrently with the NSO transaction manager.

To achieve the same concurrency with with the NSO CLI, you need to start
multiple CLI sessions in separate processes and commit them concurrently with,
for example, a CLI script.

    CLI
    commit or                                                  config
    RESTCONF   service   validate   push config                change
    patch      create    config     ndtrans=1        netsim    subscriber
    ntrans=3   nwork=1   nwork=1    cqparam=sync     device    ddelay=1
      t0 ------> 1s -----> 1s --------------[----]---> ex0 -----> 1s
      t1 ------> 1s -----> 1s --------------[----]---> ex1 -----> 1s
      t2 ------> 1s -----> 1s --------------[----]---> ex2 -----> 1s
      wall-clock 1s        1s                                     1s=3s

However, dividing the work into multiple processes may not be practical for
some applications using the NSO northbound interfaces, e.g., CLI or RESTCONF.
Also, it would make a future migration to LSA more complex.

To simplify for the NSO manager application and user, a resource facing nano
service (RFS) can start a processes per service instance. The NSO manager
application or user can then use a single transaction, e.g., CLI or RESTCONF,
to configure multiple service instances where the NSO nano service divides the
service instances into transactions running concurrently in separate processes.

    CLI
    commit or                                                   config
    RESTCONF  RFS      validate  service  push config           change
    patch     nano     create    config   ndtrans=1     netsim  subscriber
    ntrans=3  service  nwork=1   nwork=1  cq=True       device  ddelay=1
               t0 -----> 1s -----> 1s ---------[----]---> ex0 ---> 1s
      t -----> t1 -----> 1s -----> 1s ---------[----]---> ex1 ---> 1s
               t2 -----> 1s -----> 1s ---------[----]---> ex2 ---> 1s
              wall-clock 1s        1s                              1s=3s


Stacked services, a CFS (customer-facing service) abstracting the RFS, can
further simplify, hide the need to configure multiple service instances, and
allow a possible future migration to an LSA set up to scale with the number of
devices or network elements that participate in the service deployment.

    CLI
    commit
    or                                                            config
    REST-   CFS             validate  service  push config        change
    CONF    create    Nano  config    create   ndtrans=1   netsim subscriber
    patch   ntrans=3  RFS   nwork=1   nwork=1  cq=True     device ddelay=1
                      t0 --> 1s -----> 1s -------[----]---> ex0 ---> 1s
      t -----> t ---> t1 --> 1s -----> 1s -------[----]---> ex1 ---> 1s
                      t2 --> 1s -----> 1s -------[----]---> ex2 ---> 1s
                  wall-clock 1s        1s                            1s=3s

This example showcases how a CFS on top of a simple resource-facing nano
service can be implemented with the `perf-trans` example by modifying the
existing `t3` RFS and adding a CFS. Instead of multiple RESTCONF transactions,
the example uses a single CLI CFS service commit. The commit configures
multiple service instances in a single transaction where the nano service runs
each service instance in a separate process to allow multiple cores to be used
concurrently.

As with the `perf-trans` example, this example uses the NSO progress trace to
get detailed timing information for the transactions in the system. The
provided shell script sets up an NSO instance that exports tracing data to a
`.csv` file and provisions one or more service instances. A Python script uses
the `.csv` file data to present a graph to visualize the concurrency.

Running the Example
-------------------

First, make sure no existing netsim or NSO instances are running, then run:

    make showcase

See the `showcase.sh` script or below for the default settings. To, for
example, run the showcase with ten transactions to ten devices with one
transaction per device and 3 seconds of simulated work in the service create
and validation for each transaction with a 1-second delay on each device:

    ./showcase.sh -d 10 -t 10 -w 3 -r 1 -q 'True' -y 1

Should you need to start over or stop the example, run:

    make stop clean

The showcase script takes the following arguments:

-d  NDEVS
    Number of netsim (ConfD) devices (network elements) started.
    Default 4

-t  NTRANS
    Number of transactions updating the same service in parallel.
    Default: $NDEVS

-w  NWORK
    Work per transaction in the service create and validation phases. One
    second of CPU time per work item.
    Default: 3 seconds of CPU time.

-r  NDTRANS
    Number of devices the service will configure per service transaction.
    Default: 1

-c  USECQ
    Use device commit queues.
    Default: True

-y  DEV_DELAY
    Transaction delay (simulated by sleeping) on the netsim devices (seconds).
    Default: 1 second

As an example, a variant that starts one transaction with a 3-second CPU time
workload per transaction in both the service and validation code pushes the
device configuration to four devices using a commit queue, where each device
simulates taking 1 second to make the configuration changes to the devices:

    ./showcase.sh -d 4 -t 1 -w 4 -r 4 -q 'True' -y 1

A sequence diagram describing the transaction `t0` deploying service
configuration to the devices using the CLI:

                                                                  config
            CFS             validate  service  push config        change
    CLI    create    Nano  config    create   ndtrans=1   netsim subscriber
    commit ntrans=3  RFS   nwork=4   nwork=4  cq=True     device ddelay=1
      t -----> t ---> t0 --> 4s -----> 4s -------[----]---> ex0 ---> 1s
                                             \---[----]---> ex1 ---> 1s
                                              \--[----]---> ex2 ---> 1s
                                               \-[----]---> ex3 ---> 1s
                  wall-clock 4s        4s                            1s=9s

One single transaction is doing the work until the device config is pushed to
the devices in parallel using commit queues. Use a network-wide transaction
with the `-c 'False'` option. The transaction uses ~9 seconds (plus some
overhead) of wall-clock time to finish.

Now execute a second variant that starts four transactions with a 1-second CPU
time workload per transaction in both the service and validation callbacks,
each transaction pushing the device configuration to 1 device, each using
a synchronous commit queue, where each device simulates taking 1 second to make
the configuration changes to the device:

    ./showcase.sh -d 4 -t 4 -w 1 -r 1 -q 'True' -y 1

A sequence diagram describing the transactions `t0`, `t1`, and `t2` deploying
service configuration to the devices using RESTCONF patch requests:

                                                                  config
            CFS             validate  service  push config        change
    CLI     create    Nano  config    create   ndtrans=1   netsim subscriber
    commit  ntrans=1  RFS   nwork=1   nwork=1  cq=True     device ddelay=1
                      t0 --> 1s -----> 1s -------[----]---> ex0 ---> 1s
      t -----> t ---> t1 --> 1s -----> 1s -------[----]---> ex1 ---> 1s
                      t2 --> 1s -----> 1s -------[----]---> ex2 ---> 1s
                      t3 --> 1s -----> 1s -------[----]---> ex3 ---> 1s
                  wall-clock 1s        1s                            1s=3s

The four transactions run concurrently, performing the same work as in the
previous example in ~3 seconds (plus some overhead) of wall-clock time.

The above shell script uses the NSO CLI as the northbound interface, and a
Python script variant uses the NSO RESTCONF northbound interface and
notification events. Instead of polling for nano service state changes, as the
CLI script does, it uses the `service-state-changes` stream
`plan-notifications` to check when a nano service has reached a particular
state. Run it by typing:

    make showcase-rc

Or for other options:

    python3 ./showcase_rc.py -h

Further Reading
---------------
+ ./showcase.sh
+ ./showcase_rc.py
+ The CFS and RFS packages in the package-repository directory
+ ../perf-trans/README.md
+ ../perf-lsa/README.md
+ ../perf-zbfw/README.md
+ NSO Development Guide: Scaling and Performance Optimization
+ NSO Development Guide: NSO Concurrency Model
+ NSO Operation & Usage Guide: The NSO Device Manager: Commit Queue
+ ncs.conf(5) man page: /ncs-config/transaction-limits/max-transactions
