CDB Persistence Modes
=====================

NSO supports two different CDB persistence modes: "in-memory-v1" and
"on-demand-v1." The in-memory-v1 mode makes CDB an in-memory database,
ensuring all data is readily available for fast access. The downside is
that NSO requires enough memory to fit all the data in RAM, along with
memory needed to run all the queries and code. Since the CDB size scales
linearly with the amount of data, a huge amount of memory might be needed
to run NSO.

The on-demand-v1 mode lifts this requirement by loading data into RAM on
as-needed basis. Using this mode, NSO cuts down on the startup time, at
the expense of slower data access on the first read. For a full comparison,
see the NSO documentation, section CDB Persistence, under Administration.

This example shows how to set up an NSO instance to use in-memory-v1 mode,
then switch to on-demand-v1 mode, triggering the automatic migration of
the CDB to that format.

Start the example by running:

    make demo

During the demo, inspect the `ncs.conf` configuration snippets that are
required to enable each persistence mode.

When you are done with the example, run:

    make stop

Further Reading
---------------

+ NSO Documentation (Administration Guide): CDB Persistence
