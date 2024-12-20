Optimizing Transaction Wall-Clock Time Performance with LSA
===========================================================

The `perf-stack` example implements stacked services, a CFS
(customer-facing service) abstracting the RFS (resource-facing service), and
allows for easy migration to an LSA set up to scale with the number of devices
or network elements that participate in the service deployment.

This example builds on the `perf-stack` example and showcases an LSA setup
using two RFS NSO instances, `lower-nso-1` and `lower-nso-2`, with a CFS NSO
instance, `upper-nso`.

               ———————————
              | upper-nso |
              |___________|
                /       \
               /         \
      —————————————   —————————————
     | lower-nso-1 | | lower-nso-2 |
     |_____________| |_____________|
       | | . . . |     | | . . . |
    dev0             devX      devX+Y

You can imagine adding more RFS NSO instances, `lower-nso-3` etc., to the
existing two as the number of devices increases. For this simulated work
example, one NSO instance per multi-core processor and at least one CPU core
per device (network element) is likely the most performant setup.

As with the `perf-trans` and `perf-stack` examples, this example uses the NSO
progress trace to get detailed timing information for the transactions in the
system. The provided shell script sets up an NSO instance that exports tracing
data to a `.csv` file and provisions one or more service instances. A Python
script uses the `.csv` file data to present a graph to visualize the
concurrency.

Running the Example
-------------------

First, make sure no existing netsim or NSO instances are running, then execute:

    make showcase

See the `showcase.sh` script or below for the default settings. To, for
example, run the showcase with ten transactions to ten devices per RFS lower
NSO instance (20 devices total), with one transaction per device and 3 seconds
of simulated work in the service create and validation for each transaction
with a one second delay on each device:

    ./showcase.sh -d 10 -t 10 -w 3 -r 1 -q 'True' -y 1

Should you need to start over or stop the example, run:

    make stop clean

The showcase script takes the following arguments:

    -d  LDEVS
        Number of netsim (ConfD) devices (network elements) started per RFS NSO
        instance.
        Default 2 (4 total)

    -t  NTRANS
        Number of transactions updating the same service in parallel per RFS
        NSO instance. Here one per device.
        Default: $LDEVS ($LDEVS * 2 total)

    -w  NWORK
        Work per transaction in the service create and validation phases. One
        second of CPU time per work item.
        Default: 3 seconds of CPU time.

    -r  NDTRANS
        Number of devices the service will configure per service transaction.
        Default: 1

    -q  USECQ
        Use device commit queues.
        Default: True

    -y  DEV_DELAY
        Transaction delay (simulated by sleeping) on the netsim devices (sec).
        Default: 1 second

As an example, a variant that starts one transaction per RFS with a 4-second
CPU time workload per transaction in both the service and validation code,
pushing the device configuration to four devices using a commit queue, where
each device simulates taking 1 second to make the configuration changes to the
devices:

    ./showcase.sh -d 2 -t 1 -w 4 -r 2 -q 'True' -y 1

A sequence diagram describing the transaction `t0` deploying service
configuration to the devices using the CLI:

                                                                 config
           CFS             validate  service  push config        change
    CLI    create    Nano  config    create   ndtrans=1   netsim subscriber
    commit ntrans=1  RFS 1 nwork=4   nwork=4  cq=True     device ddelay=1
      t -----> t ---> t0 --> 4s -----> 4s -------[----]---> ex0 ---> 1s
                \                            \---[----]---> ex1 ---> 1s
                 \   RFS 2
                  --> t1 --> 4s -----> 4s -------[----]---> ex2 ---> 1s
                                             \---[----]---> ex3 ---> 1s
                  wall-clock 4s        4s                            1s=9s

One single transaction is doing the work on each RFS until the device config is
pushed to the devices in parallel using commit queues. Use a network-wide
transaction with the `-c 'False'` option. The transaction uses ~9 seconds (plus
some overhead) of wall-clock time to finish.

Now execute a second variant that starts four RFS transactions with a 2-second
CPU time workload per transaction in both the service and validation callbacks,
each RFS transaction pushing the device configuration to 1 device using
synchronous commit queues, where each device simulates taking 1 second to make
the configuration changes to the device:

    ./showcase.sh -d 2 -t 2 -w 2 -r 1 -q 'True' -y 1

A sequence diagram describing the transactions `t0`, `t1`, and `t2` deploying
service configuration to the devices using RESTCONF patch requests:

                                                                 config
           CFS             validate  service  push config        change
    CLI    create    Nano  config    create   ndtrans=1   netsim subscriber
    commit ntrans=2  RFS 1 nwork=2   nwork=2  cq=True     device ddelay=1
      t -----> t ---> t0 --> 2s -----> 2s -------[----]---> ex0 ---> 1s
                \     t1 --> 2s -----> 2s -------[----]---> ex1 ---> 1s
                 \   RFS 2
                  --> t2 --> 2s -----> 2s -------[----]---> ex2 ---> 1s
                      t3 --> 2s -----> 2s -------[----]---> ex3 ---> 1s
                  wall-clock 2s        2s                            1s=5s

The four transactions run concurrently, performing the same work as in the
previous example in ~5 seconds (plus some overhead) of wall-clock time.

The above shell script uses the NSO CLI as the northbound interface, and a
Python script variant uses the NSO RESTCONF northbound interface and poll
nano service state change notification events received by the upper CFS node
from the lower RFS nodes. Run it by typing:

    make showcase-rc

Or for other options:

    python3 ./showcase_rc.py -h

Further Reading
---------------
+ ./showcase.sh
+ ./showcase_rc.py
+ The CFS and RFS packages in the package-repository directory
+ ../perf-stack/README.md
+ ../perf-trans/README.md
+ ../perf-zbfw/README.md
+ NSO Development Guide: Scaling and Performance Optimization
+ NSO Development Guide: NSO Concurrency Model
+ NSO Operation & Usage Guide: The NSO Device Manager: Commit Queue
+ NSO Layered Service Architecture Guide
+ ncs.conf(5) man page: /ncs-config/transaction-limits/max-transactions
