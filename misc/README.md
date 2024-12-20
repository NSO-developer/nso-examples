Miscellaneous Examples
======================

Examples that do not belong to any of the other example categories.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### locks-and-timeouts
Showcase various timeouts and locks we can encounter and how to address the
issue while running an NSO instance. Use cases include DP API data provider,
Fastmap service `create()` callback, NEDs, locked devices, locking NSOs
northbound interfaces, `commit-retry-timeout` for northbound interfaces,
service transaction timeout, and blocking CDB subscriber.

### periodic-compaction
This example showcases how CDB compaction is automated using the NSO scheduler
with a simple dedicated compaction task. See the NSO Development Guide chapter
Scheduler and NSO Administration Guide section Compaction for documentation.