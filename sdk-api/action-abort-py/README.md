Python Action and Abort Callbacks
=================================

This example illustrates how to:
- Implement an NSO Python action that spawns a separate worker process using
the multiprocessing library and returns the worker's outcome via a result
queue.
- Support aborts/timeouts by coordinating between `cb_action()` and
`cb_abort()` using a unidirectional pipe.
- `cb_action()` terminates the worker process deterministically when an abort
is signaled.
- Perform time-bounded configuration work in the worker process using MAAPI:
create the Loopback interface if missing, update its description, set and
increment an IPv4 address, and clear shutdown on a Cisco IOS-XR device.
- Demonstrate both successful completion before the timeout and forced abort
due to timeout, surfacing a human-readable outcome string in the action's
output.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the package, start NSO, and sync device(s) from the network

    make all start

Run the action and let it complete before the timeout. Invoke the action with a
generous timeout so it finishes on its own:

    ncs_cli -u admin -C
    > request action-abort-test iosxr-config timeout 10 device xr1 \
    work-duration 5
    > show configuration devices device xr1 config interface Loopback

Run the action and trigger a timeout abort. Invoke the action with a short
timeout so `cb_abort()` fires and `cb_action()` kills the worker:

    > request action-abort-test iosxr-config timeout 2 device xr1 \
    work-duration 10
    > show configuration devices device xr1 config interface Loopback

Repeat with different durations. Abort due to timeout:

    > request action-abort-test iosxr-config timeout 5 device xr1 \
    work-duration 20
    > show configuration devices device xr1 config interface Loopback

Complete before timeout:

    > request action-abort-test iosxr-config timeout 6 device xr1 \
    work-duration 3
    > show configuration devices device xr1 config interface Loopback
    > exit

Inspect the Python VM action logs for detailed messages:

    cat logs/ncs-python-vm-abort-action.log.

How It Works
------------

- YANG action and actionpoint:
  - `action-abort-test.yang` defines an action `iosxr-config` with input leafs
  `timeout`, `device`, and `work-duration`, and a string output leaf `outcome`.
  - The actionpoint is `iosxr-config`, and `action.py` registers the
  implementation under the same name.

- Control flow:
  - `cb_action()` sets the action timeout based on `input.timeout` and spawns a
  `multiprocessing.Process` running `worker_function()`.
  - `cb_action()` stores per-session control objects (Process, abort pipe
  writer, result queue) keyed by the user session ID so `cb_abort()` can signal
  the correct action instance and so `cb_action()` can clean up reliably.
  - `cb_action()` waits for either the worker to exit, `process.sentinel` or an
  abort signal from `cb_abort()` via a Pipe. On abort, `cb_action()` kills the
  worker process and composes an aborted outcome.
  - In normal completion, `cb_action()` fetches the worker's result from the
  result queue.

- Worker function responsibilities:
  - Opens a MAAPI session as `admin` user.
  - Loops for up to work-duration seconds, each iteration:
    - Starts a write transaction, ensures `Loopback0` exists, updates
    description and IPv4 address, clears shutdown, and applies the transaction.
    - Increments the IPv4 address so changes are observable across iterations.
  - Puts a human-readable string into `result_queue` summarizing the iterations
  performed. Any exception is caught and reported via `result_queue`.

- Abort behavior:
  - When NSO invokes `cb_abort()` (for example, due to timeout), `cb_abort()`
  writes a single byte on the per-session pipe.
  - `cb_action()` detects the signal, kills the worker process, and returns an
  aborted outcome string through the action's output.

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

Further Reading
---------------

- NSO Development Guide: Actions
- NSO Development Guide: Application Timeouts
- The `demo.sh` script.
- Python API reference documentation: `ncs.dp`, `ncs.application`, `ncs.maapi`,
and `ncs.maagic`.
- The package in the `./packages/actions` directory.
- More Python action examples in the NSO example set:
  - `find $NCS_DIR/examples.ncs/ -name "*.py" | xargs grep "@Action.action"`.