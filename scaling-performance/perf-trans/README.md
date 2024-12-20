Optimizing Transaction Wall-Clock Time Performance
==================================================

How do you achieve 4x, 9x, etc. performance improvements with NSO 6 or later
compared to pre-6.0 versions of NSO?

Everything from smartphones and tablets to laptops, desktops and servers now
contain multi-core processors. To attain maximal throughput, you need to fully
utilize these powerful, multi-core systems. This way you can minimize the real
(wall-clock) time when deploying service configuration changes to the network,
which is what we usually equate with performance.

Therefore, you want to ensure that NSO can spread as much work as possible
across all available cores. The goal is to have your service deployments
maximize their utilization of the total available CPU time, in order to deploy
services faster to the users who ordered them.

That means you would ideally like to see close to full utilization of every
core when running under maximal load, such as shown by `htop`:

    htop

    0[|||||||||||||||||||||||||||||||||||||||||||||||||100.0%]
    1[|||||||||||||||||||||||||||||||||||||||||||||||||100.0%]
    2[||||||||||||||||||||||||||||||||||||||||||||||||||99.3%]
    3[||||||||||||||||||||||||||||||||||||||||||||||||||99.3%]
    4[||||||||||||||||||||||||||||||||||||||||||||||||||99.3%]
    5[||||||||||||||||||||||||||||||||||||||||||||||||||99.3%]
    6[||||||||||||||||||||||||||||||||||||||||||||||||||98.7%]
    7[||||||||||||||||||||||||||||||||||||||||||||||||||98.7%]
    8[||||||||||||||||||||||||||||||||||||||||||||||||||98.7%]
    9[||||||||||||||||||||||||||||||||||||||||||||||||||98.7%]
    ...

In this example, we explore the opportunities to improve the wall-clock time
performance and utilization and how to avoid common pitfalls. We show how NSO
can benefit from running many transactions concurrently if the service and
validation code allow concurrency.

The example uses the NSO progress trace feature to get detailed timing
information for the transactions in the system. The provided code sets up an
NSO instance that exports tracing data to a `.csv` file and provisions one or
more service instances. The simple progress trace viewer Python script can then
be used to show a graph to visualize the sequences and concurrency.

Running the Example
-------------------

First, make sure no existing netsim or NSO instances are running, then execute:

    make python

or

    make python-serial

to use a Python-based service. Alternatively, you can run the example with a
Java-based service:

    make java

or

    make java-serial

The difference between serial and non-serial is that serial service always
runs sequentially as it defines a static service conflict.

Should you need to start over or stop the example, run:

    make stop clean

The provided Python script to perform the measurements, `measure.py`, can be
conveniently run over and over without restarting NSO with the default values:

    make measure

To visualize the resulting progress trace:

    python3 ../../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

However, to demonstrate different scenarios, the `measure.py` script must be
called directly and takes the following arguments:

    -nt NTRANS, --ntrans NTRANS
        Number of transactions updating the same service in parallel. For this
        example we use NTRANS parallel RESTCONF plain patches.
        Default: 1.

    -nw NWORK, --nwork NWORK
        Work per transaction in the service create and validation phases. One
        second of CPU time per work item.
        Default: 3 seconds of CPU time.

    -nd 0..10, --ndtrans 0..10
        Number of devices the service will configure per service transaction.
        Default: 1

    -dd DDELAY, --ddelay DDELAY
        Transaction delay (simulated by sleeping) on the netsim devices (sec).
        Default: 0s

    -cq {async,sync,bypass,none}, --cqparam {async,sync,bypass,none}
        Commit queue behavior. Select "none" to use global or device setting.
        Default: none

As an example, a variant that starts one transaction with a 3-second CPU time
workload per transaction in both the service and validation callbacks, pushing
the device configuration to 3 devices without using a commit queue, where each
device simulates taking 1 second to make the configuration changes to the
devices:

    make python
    python3 measure.py --ntrans 1 --nwork 3 --ndtrans 3 \
    --cqparam bypass --ddelay 1

A sequence diagram describing the transaction `t0` deploying service
configuration to the devices using a RESTCONF patch request:

    RESTCONF   service   validate   push config
    patch      create    config     ndtrans=3        netsim
    ntrans=1   nwork=3   nwork=3    cqparam=bypass   device    ddelay=1
      t0 ------> 3s -----> 3s -----------------------> ex0 -----> 1s
                                        \------------> ex1 -----> 1s
                                         \-----------> ex2 -----> 1s
      wall-clock 3s        3s                                     1s = 7s

One single transaction is doing the work until the device config is pushed to
the devices in parallel in a network-wide transaction. The transaction use ~7
seconds (plus some overhead) of wall-clock time to finish.

Now execute a second variant that starts three transactions with a 1-second CPU
time workload per transaction in both the service and validation callbacks,
each transaction pushing the device configuration to 1 device, each using
synchronous commit queue, where each device simulates taking 1 second to make
the configuration changes to the device:

    make python
    python3 measure.py --ntrans 3 --nwork 1 --ndtrans 1 \
    --cqparam sync --ddelay 1

A sequence diagram describing the transactions `t0`, `t1`, and `t2` deploying
service configuration to the devices using RESTCONF patch requests:

    RESTCONF   service   validate   push config
    patch      create    config     ndtrans=1        netsim
    ntrans=3   nwork=1   nwork=1    cqparam=sync     device    ddelay=1
      t0 ------> 1s -----> 1s --------------[----]---> ex0 -----> 1s
      t1 ------> 1s -----> 1s --------------[----]---> ex1 -----> 1s
      t2 ------> 1s -----> 1s --------------[----]---> ex2 -----> 1s
      wall-clock 1s        1s                                     1s = 3s

The three transactions run concurrently, performing the same work as in the
previous example in ~3 seconds (plus some overhead) of wall-clock time.

Concurrent vs. Serial Execution
-------------------------------

Before NSO 6, NSO ran each individual transaction inside a transaction lock.
This allowed for a simple model where transactions could not interfere with
each other. However, this approach forces transactions to run sequentially.

To emulate this behavior in newer NSO versions, you can configure a static
conflict for a service with itself. This configuration disallows NSO from
running multiple instances of service code concurrently and achieves behavior
very similar to versions before 6.0.

Start by running the following commands:

    make python-serial measure
    python3 ../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

The command starts an NSO instance and several netsim devices, configures a
few service instances, producing output similar to this:

    Results for N=10 transactions:
    Number of CPU cores available: 10
    Number of transactions:        10
    Successful requests:           100%
    Wall-clock time:               62.60s


The `Wall-clock time` is the total time for the changes to be applied,
that is, for the whole set of N service instances.

Inspecting the progress trace visualization , you might have noticed that
majority of the time for a single transaction (around 30 seconds) is spent
waiting for the service lock. The reason is that the test service uses a static
conflict, so only a single service instance can be provisioned at a time.
The other instances mostly just sit there waiting their turn.

Now run the following commands to measure the same service but without the
conflict, allowing NSO to take full advantage of parallel execution:

    make python measure
    python3 ../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

The command produces output similar to:

    Results for N=10 transactions:
    Number of CPU cores:     10
    Number of transactions:  10
    Successful requests:     100%
    Wall-clock time:         8.31s

Notice how the wall-clock time drops ~7x compared to before, which is a
significant improvement. Services now run concurrently without the static
conflict, so taking the service lock takes 0 seconds or close to it, as shown
in the progress trace visualization.

This setup consumes 7x less wall-clock time, and the more time-consuming work
you add to the service and validation callbacks, the 7x can become 9x, 12x,
etc.

Device Changes and Commit Queue
-------------------------------

Inspecting the `make python measure` progress trace output further reveals
majority of time is spent running services and validation. However, this is
the "work" our service needs to do (that we are simulating), so it is possible
there is not much you can do to make this part faster. In any case, if the
service is well-designed, NSO should be able to parallelize it and fully
utilize all the processor cores.

But there is one other thing the above test ignores: the device response time.
In practice, many real-world network devices can't respond as fast as the
netsim devices used in the test. It is not unusual for a device to take
a few seconds to process the requested configuration changes.

Let's try to simulate a couple of seconds of delay for devices and see how
the system behaves. Run the test scripts directly as follows:

    make python; python3 measure.py --ddelay 2
    python3 ../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

Interestingly, the results are much different:

    Results for N=10 transactions:
    Number of CPU cores:     10
    Number of transactions:  10
    Successful requests:     100%
    Wall-clock time:         28.81s

The service code ("work") still takes the same amount of time -- this has
obviously not changed. However, the (global) transaction lock has now become
the bottleneck: transactions spend a significant amount of time holding it,
which then negatively affects other transactions that have to wait for the
lock longer, queued. See `taking transaction lock` in the progress trace
visualization. This reflects in the much longer start-to-finish transaction
time, similar to when the service was provisioned serially. What is causing
this behavior?

The concept of a network-wide transaction requires NSO to wait for the managed
devices to process the change completely. In the meantime, other transactions
have to wait their turn to access the devices. Since devices implement a small
delay, much like communication with real-life devices would take, this limits
the minimal transaction time and the concurrency you can achieve. The test
shows that updating devices takes almost 100% of the time spent inside a
transaction lock.

You can use the commit queue feature to avoid the wait and increase the
throughput. In this example, you add a commit parameter `commit-queue=sync`
to the RESTCONF plain-patch requests:

    make python; python3 measure.py --cqparam sync --ddelay 2
    python3 ../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

You can use `async` instead of `sync` and find out when the queue item is
completed through a RESTCONF notification. Also, you can enable using the
commit queue by default instead of adding a parameter to each RESTCONF request:

    make python enable-cq; python3 measure.py --ddelay 2
    python3 ../common/simple_progress_trace_viewer.py $(ls logs/*.csv)

Observe how the transaction time drops significantly. This is because each
transaction now takes roughly the time it takes individual service and
validation code invocation to do its "work", plus the device delay when pushing
the configuration and a little overhead, which is again close to
the optimal you can achieve.

    Results for N=10 transactions:
    Number of CPU cores:     10
    Number of transactions:  10
    Successful requests:     100%
    Wall-clock time:         10.04s

Takeaways for Optimizing Wall-Clock Time Performance
----------------------------------------------------

- Measure the performance using total wall-clock time for the service
  deployment and use the detailed NSO progress trace of the transactions to
  find bottlenecks.

- Consider enabling a commit queue or device communication delay that might
  overshadow any potential concurrency in your code.

- Separate time-consuming transactions into chunks. Have each chunk, i.e.
  service instance, configure one or a subset of devices to avoid delaying
  other transaction chunks in the commit queues while pushing the
  configuration.

- Avoid conflicts between transactions. For example, have one service instance
  target one device to avoid conflicts and to enable one transaction per device
  where transactions to different devices run concurrently.

- Enable service and validation callbacks to fully utilize the available
  CPU cores. In particular, CPU-intensive Python applications, similar to this
  example often require starting a new process because of the Python GIL, as
  shown by the `package-repository/t3-service-python/python/t3/t3.py` callback
  implementations.

Further Reading
---------------

+ ../perf-stack/README.md
+ ../perf-lsa/README.md
+ ../perf-setvals/README.md
+ ../perf-zbfw/README.md
+ NSO Development Guide: Scaling and Performance Optimization
+ NSO Development Guide: NSO Concurrency Model
+ NSO Operation & Usage Guide: The NSO Device Manager: Commit Queue
+ ncs.conf(5) man page: /ncs-config/transaction-limits/max-transactions
