Event Notifications
===================

This example shows how to use the Python `_ncs.events` low-level module for
subscribing to and processing NSO event notifications. Typically, the event
notification API is used by applications that manage NSO using the SDK API
using, for example, MAAPI or for debug purposes. In addition to subscribing to
the various events, streams available over other northbound interfaces, such as
NETCONF, RESTCONF, etc., can be subscribed to.

The `event_notifications.py` Python script used with this example can also be
used as a standalone application for debugging any NSO instance, not just the
NSO instance created by the `demo.sh` shell script.

Running the Example
-------------------

To run the demo shell script:

    make demo

To run the demo shell script without being prompted to continue:

    make demo-nonstop

The `event_notifications.py` Python script can be used standalone in a
development environment to monitor NSO event notifications.

    $ python3 event_notifications.py -h
    ...

    A simple NSO event notification receiver

    options:
      -h, --help            show this help message and exit
      -d, --daemon          Daemon log events
      -D, --devel           Developer log events
      -a, --audit           Audit log events
      -N, --netconf         NETCONF log events
      -J, --jsonrpc         JSON-RPC log events
      -W, --webui           WebUI log events
      -t, --takeover-syslog
                            Stop NSO syslogging
      -S, --snmpa           SNMP agent audit log events
      -u, --user-session    User session events
      -i, --commit-diff     Config change events must be synced to continue
      -L, --commit-failed   Data provider commit callback failure events
      -P, --commit-progress
                            Commit progress events
      -g, --progress        Commit and action progress events
      -v {normal,verbose,very_verbose,debug}, --progress-verbosity \
      {normal,verbose,very_verbose,debug}
                            Verbosity for progress events
      -F, --ha-info         High availability information events
      -U, --upgrade         Upgrade events
      -R, --package-reload  Package reload complete events
      -q, --cq-progress     Commit queue item events
      -l, --reopen-logs     Close and reopen log files events
      -O, --call-home       Call home connection events
      -w, --audit-network   Audit network events
      -C, --compaction      CDB compaction events
      -A, --all             All events above
      -H, --heartbeat       Heartbeat events
      -e, --health-check    Health check events
      -c, --commit-simple   Configuration change events
      -s STREAM, --stream STREAM
                            Notification NAME stream events
      -y, --audit-sync      Audit notifications must be synced to continue
      -k, --audit-network-sync
                            Audit network notifications must be synced to \
                            continue
      -Y, --ha-info-sync    HA changes must be synced to continue
      -r, --confirm-sync    Confirm HA and Audit sync
      -T INTERVAL, --interval INTERVAL
                            heartbeat health check interval. Default 1000ms
      -B START_TIME, --start-time START_TIME
                            Notification stream start time \
                            - yang:date-and-time format
      -E STOP_TIME, --stop-time STOP_TIME
                            Notification stream stop time \
                            - yang:date-and-time format
      -x XPATH_FILTER, --xpath-filter XPATH_FILTER
                            XPath filter
      -z USER_ID, --user-id USER_ID
                            User ID
      -I ADDRESS, --address ADDRESS
                            Connect to NSO at ADDRESS. Default: 127.0.0.1
      -p PORT, --port PORT  Connect to NSO at PORT. Default: 4569
      -n, --non-interactive
                            No actions or input required from user

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

Further Reading
---------------

+ The `event_notifications.py` script
+ The `demo.sh` script
+ NSO Development Guide: Low-level Event Notifications
+ NSO SDK API Reference: NSO Python API `_ncs.events` module
