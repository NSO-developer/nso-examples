Scaling and Performance Examples
================================

Optimize NSO for scaling and performance.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### cdb-on-demand
The `on-demand-v1` CDB persistence mode loads data into RAM on an as-needed
basis. Using this mode, NSO cuts the startup time at the expense of slower data
access on the first read. This example shows how to set up an NSO instance to
use `in-memory-v1` mode and then switch to `on-demand-v1` mode, triggering the
automatic migration of the CDB to that format. See the NSO Administration
Guide, section CDB Persistence, under Administration.

### conflict-retry
The NSO-enabled system can take better advantage of available resources,
especially the additional CPU cores, making it much more performant with NSO's
concurrency model. This example showcases how to handle application transaction
conflicts before the NSO transaction lock is taken, how NSO detects them, and
how the transaction can be retried automatically by NSO or through a Python or
Java application. Used by the NSO Development Guide chapter NSO Concurrency
Model to describe the optimistic concurrency feature.

### perf-trans
The NSO-enabled system can take better advantage of available resources,
especially the additional CPU cores, making it much more performant with NSO's
concurrency model. This example showcases how performance can be optimized by
dividing work into several transactions running in parallel with service and
validation callback Python and Java applications adapted to handle concurrency.
Used by the NSO Development Guide chapter Scaling and Performance
Optimization.

### perf-setvals
If a service creates a significant amount of configuration data for devices, it
is often significantly faster to use a single MAAPI `shared_set_values()` call
instead of using multiple `create()` and `set()` calls or a service template.
Wall-clock time performance is improved using MAAPI `shared_set_values()` and
multiple CPU cores when writing to more than one device. Used by the NSO
Development Guide chapter Scaling and Performance Optimization.

### perf-stack
To simplify for the NSO manager application and user, a resource facing nano
service (RFS) can start a processes per service instance. The NSO manager
application or user can then use a single transaction, e.g., CLI or RESTCONF,
to configure multiple service instances where the NSO nano service divides the
service instances into transactions running concurrently in separate processes.
Stacked services, such as a CFS (customer-facing service) abstracting the RFS,
can further simplify, hide the need to configure multiple service instances,
and allow a possible future migration to an LSA se up. Used by the NSO
Development Guide chapter Scaling and Performance Optimization.

### perf-lsa
This example implements stacked services, a CFS (customer-facing service)
abstracting the RFS (resource-facing service). It allows for easy migration to
an LSA set up to scale with the number of devices or network elements
participating in the service deployment. Builds on the `perf-stack` example and
showcases an LSA setup using two RFS NSO instances with a CFS NSO instance.
Used by the NSO Development Guide chapter Scaling and Performance Optimization.

### perf-zbfw
An example that implements a simplified zone-based firewall configuration and
uses it to show a use-case for the principles introduced by the simulated
`perf-trans` example. In addition to showcasing how performance can be
optimized by dividing work into several transactions, the example implements
Java and template service mapping code, YANG must statement XPath expression
validation work, and show how parallel transactions can interact with NSO
commit queues to improve performance. Used by the NSO Development Guide chapter
Scaling and Performance Optimization.