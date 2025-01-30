Optimizing the Service Create Callback Performance
==================================================

Suppose a service creates a significant amount of configuration data for
devices. In that case, it is often significantly faster to use a single MAAPI
`load_config_cmds()` or `shared_set_values()` function instead of
using multiple `create()` and `set()` calls or configuration template `apply()`
calls.

A template is often a good enough option for wall-clock time performance. In
this example, we use a simple template that applies the template once for each
entry. A more advanced template can allow for the template to be applied only
once, and while more complex, the template can be on par performance-wise with
the `shared_set_values()` and `load_config_cmds()` implementations.

The MAAPI Python `shared_set_values()` and `load_config_cmds()`, and Java
`sharedSetValues()` and `loadConfigCmds()` can take an array of tag-values or
XML as input and set the configuration instead of using one API call and
context switch for each value.

Just as with a configuration template, the Python `create()` and `set()`
functions, and Java `sharedCreate()` and `sharedSet()` functions, the MAAPI
Python `shared_set_values()` and `load_config_cmds()`, and Java
`sharedSetValues()` and `loadConfigCmds()` will update the service meta-data
when used for creating configuration in the service create callback.

In this example, we explore the opportunities to improve the wall-clock
time performance using MAAPI `shared_set_values()` and `load_config_cmds()`
and by utilizing multiple CPU cores when writing to more than one device.

The example writes configuration to an access control list and a route list of
a Cisco ASA device. It uses either MAAPI Python with a configuration template,
`create()` and `set()` calls, Python `shared_set_values()` and
`load_config_cmds()`, or Java `sharedSetValues()` and `loadConfigCmds()` to
write the configuration in XML format.

The Python `load_config_cmds()` and Java `loadConfigCmds()` functions support
loading different configuration formats such as XML, JSON, and CLI, where XML
is the fastest and thus used by this example.

The example uses the NSO progress trace feature to get detailed timing
information for the transactions in the system. The provided code sets up an
NSO instance that exports tracing data to a `.csv` file and provisions one or
more service instances. The simple progress trace viewer Python script can then
be used to show a graph to visualize the sequences and concurrency.

Running the Example
-------------------

First, make sure no existing netsim or NSO instances are running. To run the
example using MAAPI Python with a configuration template or using `create()`
and `set()` calls to create 3000 rules and 3000 routes on one device:

    ./measure.sh -r 3000 -t py_template -n true
    ./measure.sh -r 3000 -t py_create -n true

The commit uses the `no-networking` parameter to skip pushing the configuration
to the simulated and un-proportionally slow Cisco ASA netsim device.

Next, run the example using a single MAAPI Python `load_config_cmds()` or
`shared_set_values()` call to create 3000 rules and 3000 routes on one device:

    ./measure.sh -r 3000 -t py_mload_xml -n true
    ./measure.sh -r 3000 -t py_setvals_xml -n true

Using the MAAPI `load_config_cmds()` or `shared_set_values()` functions, the
service create callback is, for this example, ~5x faster than using the MAAPI
`create()` and `set()` functions. The total wall-clock time for the transaction
is more than 2x faster. The simple configuration template is slightly slower
compared to using the MAAPI `load_config_cmds()` or `shared_set_values()`
functions.

To run the example using a single MAAPI Java `loadConfigCmds()` or
`sharedSetValues()` call to create 3000 rules and 3000 routes on one device
with a similar result as with Python:

    ./measure.sh -r 3000 -t j_mload_xml -n true
    ./measure.sh -r 3000 -t j_setvals_xml -n true

As with the `perf-trans` example, when deploying the service configuration to
several devices, this example follows the best practice design pattern of
configuring one device per service instance. Using one transaction per
service instance enables NSO to use one CPU core per service instance and run
the transactions concurrently.

To run the example and set the configuration to two devices:

    ./measure.sh -d 2 -r 3000 -t py_setvals_xml -n true

Notice how the create callback and saving the reverse diff-set for each device
run in parallel.

Change the -d <number> flag to add devices. For all available options, see the
`measure.sh` script for details:

    ./measure.sh -h
    ./measure.sh [-d <num_devs> -r <num_routes> -t <test> -q <use_cq> \
                  -n <no_networking> -h <help>]

Further Reading
---------------

+ ../perf-trans/README.md
+ ../perf-zbfw/README.md
+ NSO Development Guide: Scaling and Performance Optimization
+ NSO Development Guide: NSO Concurrency Model
+ NSO Java SDK API Reference: NavuNode sharedSetValues(String xml)
+ NSO Python SDK API Reference: _ncs.maapi load_config_cmds() and ncs.maapi
  shared_set_values()
+ ncs.conf(5) man page: /ncs-config/transaction-limits/max-transactions
