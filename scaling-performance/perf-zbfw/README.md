Optimizing Service Deployment Wall-Clock Time Performance
=========================================================

The `perf-trans` example shows how NSO can benefit from running as many
transactions concurrently as processor cores (or threads) are available using a
simulated application.

In this example, we implement a small subset of a zone-based firewall that uses
Java service code and service XML templates to map the service to the device
configuration. Furthermore, the example shows how to use the NSO commit queue
to minimize the wall-clock time performance impact of deploying the zone-based
firewall configuration to devices, here netsim (ConfD) NETCONF enabled nodes.
RESTCONF plain patches set the zbfw service configuration.

If using asynchronous commit queue mode, the Python measure.py application
performs the RESTCONF PATCH requests and waits for commit queue "completed"
event notifications to find out when the resulting device configuration has
been committed to the devices.

The service mapping to device configuration data is done by the zbfw package
`ZbfwRFS.java` service `create()` callback that uses `router-zbfw-policy`
service templates. The zbfw package models the service configuration in
`zbfw.yang`, and the `router` package models the device config in
`router-zbfw.yang`.

To demonstrate different scenarios, the `measure.py` script must be called
directly and takes the following arguments:

    -nz NZONES, --nzones NZONES
        Zone pairs per transaction.
        Default: 100

    -nt NTRANS, --ntrans NTRANS
        Number of transactions per device updating the service in parallel.
        "0" or less equal one single RESTCONF transaction for all devices.
        Default: 1

    -cq {async,sync,bypass,none}, --cqparam {async,sync,bypass,none}
        Commit queue behavior. Select "none" to use global or device setting.
        Default: async

The number of devices can be changed by, for example, the `NDEVS` variable in
the `Makefile`. Default is 10 devices.

The NSO Progress Trace feature extracts detailed timing information for the
transactions in the system. The code in the example sets up an NSO instance
that exports tracing data to a `.csv` file and provisions one or more service
instances. A simple progress trace viewer Python script can then be used to
show a graph to visualize the sequences, concurrency and identify bottlenecks.

Running the Example
-------------------

First, make sure no existing netsim or NSO instances are running, then execute:

    ./showcase.sh

The shell script will do a sanity check by first showcasing the service mapping
with 100 zone-pairs in 5 transactions:

    make stop clean NDEVS=1 parallel start
    python3 measure.py --ntrans 5 --nzones 10 --cqparam sync

To visualize the resulting progress trace:

    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

The `make stop clean ...` may seem unnecessary, but it ensures the test runs
start from as identical position as possible, limiting the effects a previous
run could have on the current one. It is easy to forget cleaning up all the
configuration and state from before, which then results in testing a different
thing than what you meant.

Next, map service configuration with a single transaction of 500 zone-pairs:

    make stop clean NDEVS=1 parallel start
    python3 measure.py --ntrans 0 --nzones 50 --cqparam bypass
    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

Then using the same setup as the first variant but simulating pre-6.0 behavior
with 100 zone-pairs in 5 transactions:

    make stop clean NDEVS=1 serial start
    python3 measure.py --ntrans 5 --nzones 10 --cqparam async
    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

The first variant use at most 5 cores, while the second and third will only
be able to use use one core for the service-to-device mapping. There is a ~2x
wall-clock time difference between the first variant and the two other ones,
and it will increase as we increase the load.

To add more load, deploy, for example, 10 devices using two
processes/transactions per device = 20 transactions and 500 zone-pairs per
transaction = 1000 zone-pairs per device = 10k zone-pairs:

    make stop clean NDEVS=10 parallel start
    python3 measure.py --ntrans 2 --nzones 500 --cqparam async
    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

Same as above, 1000 zone-pairs per device = 10k zone-pairs, but in one single
transaction.

    make stop clean NDEVS=10 parallel start
    python3 measure.py --ntrans 0 --nzones 1000 --cqparam bypass
    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

The difference in wall-clock time performance between the two will, on a
12-core processor, be around ~3x due to the first variant using 20 processes.
Therefore, the first variant uses all 12 CPU cores with the commit queue to
deploy the configuration to the 10 netsim devices.

Device configuration changes are pushed to the devices using the NSO commit
queue, releasing the transaction lock when the configuration is committed to
the queue instead of when the device configuration has been pushed to the
devices.

Example Scenario Walkthrough
----------------------------

Let's examine the progress trace summary for the last two variants described in
the previous section, using a MacBook Pro with a 10-core processor. First, use
a single RESTCONF PATCH request for all 10k zone-pair configurations in one
service transaction.

    make stop clean NDEVS=10 parallel start
    python3 measure.py --ntrans 0 --nzones 1000 --cqparam bypass
    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

    Results for N=1 service transactions:
    Number of available CPU cores:         10
    Number of devices configured:          10
    Number of zone pairs per transaction:  1000
    Number of zone pairs per device:       1000
    Total number of zone pairs:            10000
    Total number of transactions:          1
    Total number of device transactions:   10
    Successful requests:                   100%
    Wall-clock time:                       314.37s

The `Wall-clock time`, i.e., the total time for the changes to be applied, gave
plenty of time to get coffee. Furthermore, as all zone pairs are configured
by one RESTCONF PATCH request, only one CPU core will be used by a single
transaction performing the service mapping. If there is no transaction waiting,
there is no need to unlock the transaction and little benefit with the commit
queue vs. a network-wide transaction for one service transaction.

Next, we divide the 10k zone-pair configuration into 10 RESTCONF patch
requests. One transaction per device match one process (RESTCONF patch
requests) per CPU core with 1000 zone pairs each:

    make stop clean NDEVS=10 parallel start
    python3 measure.py --ntrans 1 --nzones 1000 --cqparam sync
    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

    Results for N=10 service transactions:
    Number of available CPU cores:         10
    Number of devices configured:          10
    Number of zone pairs per transaction:  1000
    Number of zone pairs per device:       1000
    Total number of zone pairs:            10000
    Total number of transactions:          10
    Total number of device transactions:   10
    Successful requests:                   100%
    Wall-clock time:                       80.54s

The `Wall-clock time`, i.e., the total time for the changes to be applied, took
about ~4x less time. The transactions did spend some time waiting for
`taking transaction lock` this time. This is the time each transaction used to
update NSO CDB with the configuration. Still, that time plus the overhead of
handling multiple processes is insignificant compared to the time savings
thanks to the commit queue and transactions running concurrently.

Takeaways for Optimizing Wall-Clock Time Performance
----------------------------------------------------

- Measure the performance using total wall-clock time for the service
  deployment and use the detailed NSO progress trace of the transactions to
  find bottlenecks.

- Consider enabling the commit queue or risk device communication delay
  overshadowing any potential parallelism in your code.

- Separate time-consuming transactions into chunks. Have each chunk configure
  one or a subset of devices to avoid delaying other transaction chunks in the
  commit queue while pushing the configuration.

- Avoid conflicts between transactions.

- Enable service and validation callbacks to utilize the available CPU cores
  fully. See the perf-trans example `t3.py` application and README for Python
  advice.

Further Reading
---------------

+ ../perf-trans/README.md
+ NSO Development Guide: Scaling and Performance Optimization
+ NSO Development Guide: NSO Concurrency Model
+ NSO User Guide: The NSO Device Manager: Commit Queue
+ ncs.conf(5) man page: /ncs-config/transaction-limits/max-transactions
