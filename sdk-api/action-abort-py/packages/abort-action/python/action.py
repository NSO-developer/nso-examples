"""NSO Action Package example.

Implements an package with an action that runs a worker process that
can be aborted.

See the README file for more information
"""
import ipaddress
import time
import ncs
from ncs.dp import Action
import _ncs
import multiprocessing
from multiprocessing import connection as mp_connection
from queue import Empty

loopback_id = 0  # Loopback interface ID to create/update


# Worker function executed in a separate process.
def worker_function(result_queue, device_name, duration_secs):
    start = time.time()
    ipstr = ipaddress.IPv4Address(u'1.0.0.1')
    iterations = 0

    m = None
    try:
        # Establish a MAAPI connection and start a user session
        m = ncs.maapi.Maapi()
        m.start_user_session("admin", "system")

        while time.time() - start < duration_secs:
            # Start a read-write transaction against the running datastore.
            with m.start_write_trans() as t:
                root = ncs.maagic.get_root(t)
                dev = root.devices.device[device_name]
                interface = dev.config.cisco_ios_xr__interface
                # IOS-XR config example:
                lo = None
                if not interface.Loopback.exists(loopback_id):
                    lo = interface.Loopback.create(loopback_id)
                else:
                    lo = interface.Loopback[loopback_id]
                lo.description = (f"NSO demo iteration {iterations} at "
                                  f"{time.strftime('%H:%M:%S')}")
                lo.ipv4.address.ip = ipstr
                if lo.shutdown.exists():
                    lo.shutdown.delete()
                t.apply()

            iterations += 1
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
            ipint += 1
            ipstr = ipaddress.IPv4Address(ipint)

        # Completed within allotted duration.
        result_queue.put(
            f"completed: set Loopback{loopback_id} description {iterations} "
            f"times in {duration_secs}s on device '{device_name}' with final "
            f"IP {ipstr}"
        )
    except Exception as e:
        # Any exception is reported back to the caller.
        result_queue.put(f"error: {type(e).__name__}: {e}")


class IOSXRConfigAction(Action):
    """This class implements the dp.Action class."""
    # Per-session state: usid -> {'process': Process,
    #                             'abort_writer': Connection,
    #                             'result_queue': Queue}
    usid_dict = {}

    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        """Called when the actionpoint is invoked."""
        self.log.info(f"action(uinfo={uinfo.usid}, name={name}, kp={kp})")

        # Allow up to input.timeout seconds before NSO invokes cb_abort on
        # timeout.
        _ncs.dp.action_set_timeout(uinfo, input.timeout)

        # Abort pipe: cb_action waits on abort_reader; cb_abort writes to
        # abort_writer.
        abort_reader, abort_writer = multiprocessing.Pipe(duplex=False)

        # Create a result queue for the worker to send its outcome.
        result_queue = multiprocessing.Queue()

        # Start the worker process; it does not handle abort itself.
        process = multiprocessing.Process(target=worker_function,
                                          args=(result_queue,
                                                input.device,
                                                input.work_duration))
        process.start()

        # Store per-session control objects.
        IOSXRConfigAction.usid_dict[uinfo.usid] = {
            'process': process,
            'abort_writer': abort_writer,
            'result_queue': result_queue,
        }

        self.log.info(f"Started worker PID={process.pid}; "
                      f"waiting for completion or abort signal.")
        outcome = "error"  # Default outcome if something goes wrong.

        try:
            # Wait until either the worker exits (process.sentinel) or
            # cb_abort signals via the pipe.
            ready = mp_connection.wait([process.sentinel, abort_reader])

            if abort_reader in ready:
                self.log.info("Abort signaled; terminating worker process "
                              "from cb_action.")
                process.kill()

                # Since we killed the worker, it might not have put a message;
                # define an abort outcome.
                iterations = 0
                ipstr = "unknown"
                with ncs.maapi.single_read_trans('admin', 'system') as t:
                    root = ncs.maagic.get_root(t)
                    dev = root.devices.device[input.device]
                    interface = dev.config.cisco_ios_xr__interface
                    if interface.Loopback.exists(loopback_id):
                        lo = interface.Loopback[loopback_id]
                        iterations = lo.description.split()[3]
                        ipstr = lo.ipv4.address.ip

                outcome = (f"aborted: set Loopback{loopback_id} description "
                           f"{iterations} times on device "
                           f"{input.device} with last IP {ipstr}")

            elif process.sentinel in ready:
                # Worker finished on its own.
                process.join()
                # Retrieve the worker's outcome message.
                try:
                    outcome = result_queue.get_nowait()
                except Empty:
                    outcome = "completed"

            self.log.info(f"Worker process PID={process.pid} outcome: "
                          f"{outcome}")
        finally:
            # Cleanup: close pipe ends, remove session entry, and close the
            # queue.
            try:
                abort_reader.close()
            except Exception:
                pass

            entry = IOSXRConfigAction.usid_dict.pop(uinfo.usid, None)
            if entry:
                try:
                    entry['abort_writer'].close()
                except Exception:
                    pass
                try:
                    entry['result_queue'].close()
                except Exception:
                    pass

        # Populate NSO action output with the outcome string.
        output.outcome = outcome

        # Return status code based on outcome.
        if "aborted" in outcome:
            return ncs.ERR_TIMEOUT
        return ncs.CONFD_OK

    def cb_abort(self, uinfo):
        """Called when the action is aborted, e.g., due to timeout or user
           cancel."""
        self.log.info('cb_abort invoked')

        entry = IOSXRConfigAction.usid_dict.get(uinfo.usid)
        if not entry:
            self.log.warning(f"No active worker found for "
                             f"uinfo.usid={uinfo.usid}.")
            return ncs.CONFD_OK

        # Signal cb_action via the abort pipe; cb_action will terminate the
        # worker.
        try:
            entry['abort_writer'].send_bytes(b'1')
            self.log.info(f"Abort signal sent for uinfo.usid={uinfo.usid}.")
        except (BrokenPipeError, OSError) as e:
            self.log.warning(f"Failed to send abort signal (possibly already"
                             f" handled): {e}")

        return ncs.CONFD_OK


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO.
# ---------------------------------------------
class Action(ncs.application.Application):
    """This class is referred to from the package-meta-data.xml."""
    def setup(self):
        self.log.info('Action RUNNING')
        self.register_action('iosxr-config', IOSXRConfigAction)

    def teardown(self):
        self.log.info('Action FINISHED')
