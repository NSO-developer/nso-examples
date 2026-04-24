Example NED Limitations
=======================

The NED packages under this directory are lightweight example and netsim NEDs.
They are intended for local example labs and service package walkthroughs, not
as complete production-quality NED implementations.

Known limitations include:

  - `outformat native` dry-runs may be incomplete or unsupported
  - CLI rendering and parser behavior are only implemented to the extent needed
    by the shipped examples and netsim topologies

Example authors should prefer plain NSO dry-runs unless native output is known
to work for the specific example NED and workflow being demonstrated.
