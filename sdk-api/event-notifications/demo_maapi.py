#!/usr/bin/env python3
"""Run the event notification demo using Python MAAPI for NSO operations."""

import os
import contextlib
import io
import select
import shutil
import socket
import subprocess
import sys
import termios
import threading
import time
import tty
from datetime import datetime, timezone
from pathlib import Path

import _ncs
import _ncs.events as events
import event_notifications
import ncs


RED = "\033[0;31m"
GREEN = "\033[0;32m"
PURPLE = "\033[0;35m"
NC = "\033[0m"


def pause(prompt=None):
    """Wait for one key press unless NONINTERACTIVE is set."""
    if os.environ.get("NONINTERACTIVE"):
        return

    if prompt is None:
        prompt = f"{RED}##### Press any key to continue" \
                 f" or ctrl-c to exit\n{NC}"

    print(prompt, end="", flush=True)
    if not sys.stdin.isatty():
        sys.stdin.read(1)
        return

    fd = sys.stdin.fileno()
    old_settings = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        sys.stdin.read(1)
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)


def run(command, check=True, stdout=None, stderr=None, stdin=None, cwd=None):
    subprocess.run(command, check=check, stdout=stdout, stderr=stderr,
                   stdin=stdin, cwd=cwd)


def print_header(color, text, leading_newline=True):
    prefix = "\n" if leading_newline else ""
    print(f"{prefix}{color}##### {text}{NC}", flush=True)


def print_native_dry_run(result):
    if result.get("outformat") != "native":
        return
    for data in result.get("device", {}).values():
        print(data, end="" if data.endswith("\n") else "\n")
    local = result.get("local-node")
    if local:
        print(local, end="" if local.endswith("\n") else "\n")


class EventListener:
    def __init__(self, mask, log_path, stream="whatever", start_time=None,
                 xpath_filter="/", interval=1000):
        self.mask = mask
        self.log_path = Path(log_path)
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        self.log_file = self.log_path.open("w", encoding="utf-8")
        self.received = []
        self.condition = threading.Condition()
        self.stopping = False
        self.sock = self._connect(mask, stream, start_time, xpath_filter,
                                  interval)
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _connect(self, mask, stream, start_time, xpath_filter, interval):
        with ncs.maapi.Maapi(load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD):
            pass

        noexists = _ncs.Value(init=1, type=ncs.C_NOEXISTS)
        if start_time:
            start = _ncs.Value(init=start_time, type=ncs.C_DATETIME)
        else:
            start = noexists
        data = events.NotificationsData(
            heartbeat_interval=interval,
            health_check_interval=interval,
            stream_name=stream,
            start_time=start,
            stop_time=noexists,
            xpath_filter=xpath_filter)

        port_env = os.environ.get("NCS_IPC_PORT")
        if port_env:
            sock = socket.socket(socket.AF_INET)
            events.notifications_connect2(
                sock=sock, mask=mask, ip=_ncs.ADDR, port=int(port_env),
                data=data)
        else:
            sock = socket.socket(socket.AF_UNIX)
            path = os.environ.get("NCS_IPC_PATH") or _ncs.PATH
            events.notifications_connect2(
                sock=sock, mask=mask, data=data, path=path)
        return sock

    def _run(self):
        while not self.stopping:
            readables, _, _ = select.select([self.sock], [], [], 0.2)
            if self.sock in readables:
                self._record_event()

    def _record_event(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            event_dict = event_notifications.process_event(
                None, self.sock, self.mask)
        text = output.getvalue()
        if text:
            print(text, end="", flush=True)
            self.log_file.write(text)
            self.log_file.flush()
        with self.condition:
            self.received.append((event_dict, text))
            self.condition.notify_all()

    def mark(self):
        with self.condition:
            return len(self.received)

    def wait_for(self, predicate, description, timeout=60, since=0):
        end = time.monotonic() + timeout
        with self.condition:
            while True:
                for event_dict, text in self.received[since:]:
                    if event_dict is not None and predicate(event_dict, text):
                        return event_dict
                remaining = end - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError(
                        f"Timed out waiting for {description}")
                self.condition.wait(remaining)

    def print_log(self):
        self.log_file.flush()
        print(self.log_path.read_text(errors="ignore"), end="")

    def stop(self):
        self.stopping = True
        self.thread.join(timeout=2)
        self.sock.close()
        self.log_file.close()


def all_events_mask():
    return (events.NOTIF_DAEMON |
            events.NOTIF_DEVEL |
            events.NOTIF_AUDIT |
            events.NOTIF_NETCONF |
            events.NOTIF_JSONRPC |
            events.NOTIF_WEBUI |
            events.NOTIF_TAKEOVER_SYSLOG |
            events.NOTIF_SNMPA |
            events.NOTIF_USER_SESSION |
            events.NOTIF_COMMIT_DIFF |
            events.NOTIF_COMMIT_FAILED |
            events.NOTIF_COMMIT_PROGRESS |
            events.NOTIF_PROGRESS |
            events.NOTIF_HA_INFO |
            events.NOTIF_UPGRADE_EVENT |
            events.NCS_NOTIF_PACKAGE_RELOAD |
            events.NCS_NOTIF_CQ_PROGRESS |
            events.NOTIF_REOPEN_LOGS |
            events.NCS_NOTIF_CALL_HOME_INFO |
            events.NCS_NOTIF_AUDIT_NETWORK |
            events.NOTIF_COMPACTION |
            events.NOTIF_STREAM_EVENT |
            events.NOTIF_SYSTEM_GOING_DOWN |
            events.NOTIF_AUDIT_SYNC |
            events.NCS_NOTIF_AUDIT_NETWORK_SYNC |
            events.NOTIF_HA_INFO_SYNC)


def call_home_events_mask():
    return (events.NCS_NOTIF_CALL_HOME_INFO |
            events.NOTIF_COMMIT_SIMPLE |
            events.NOTIF_HA_INFO |
            events.NOTIF_HEARTBEAT |
            events.NOTIF_STREAM_EVENT |
            events.NCS_NOTIF_CQ_PROGRESS)


def is_commit_queue_done(event_dict, _text):
    if not event_dict["type"] & events.NCS_NOTIF_CQ_PROGRESS:
        return False
    return event_dict["cq_progress"]["type"] in (
        events.NCS_CQ_ITEM_COMPLETED, events.NCS_CQ_ITEM_FAILED)


def wait_for_commit_queue_completed(listener, since=0):
    event_dict = listener.wait_for(
        is_commit_queue_done, "commit queue item completion", since=since)
    cq_type = event_dict["cq_progress"]["type"]
    if cq_type == events.NCS_CQ_ITEM_FAILED:
        raise RuntimeError("Commit queue item failed")


def reset_example():
    print_header(GREEN, "Event notification MAAPI demo",
                 leading_newline=False)
    print_header(PURPLE, "Start clean", leading_newline=False)
    run(["make", "stop", "clean"], check=False, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL)
    shutil.rmtree("nso-rundir", ignore_errors=True)


def create_dummy_example():
    print_header(PURPLE, "Create a dummy NSO service example",
                 leading_newline=False)
    run(["make", "all"])
    Path("nso-rundir/dummy.yang").write_text("""\
module dummy {
  namespace "http://com/example/dummy";
  prefix dummy;
  leaf dummy {
    type string;
  }
}
""", encoding="utf-8")
    run(["ncs-make-package", "--no-java", "--no-python", "--no-test",
         "--dest", "nso-rundir/packages/dummy-nc-1.0", "--build",
         "--netconf-ned", "./nso-rundir", "dummy-nc-1.0"])
    run(["ncs-make-package", "--no-test", "--dest",
         "nso-rundir/packages/dummy-service", "--service-skeleton",
         "template", "dummy-service"])
    Path("nso-rundir/packages/dummy-service/src/yang/"
         "dummy-service.yang").write_text("""\
module dummy-service {
  namespace "http://com/example/dummyservice";
  prefix dummy-service;
  import tailf-ncs {
    prefix ncs;
  }
  list dummy-service {
    key device;
    uses ncs:service-data;
    ncs:servicepoint "dummy-service";
    leaf device {
      type leafref {
        path "/ncs:devices/ncs:device/ncs:name";
      }
    }
    leaf dummy {
      type string;
    }
  }
}
""", encoding="utf-8")
    Path("nso-rundir/packages/dummy-service/templates/"
         "dummy-service-template.xml").write_text("""\
<config-template xmlns="http://tail-f.com/ns/config/1.0"
                 servicepoint="dummy-service">
  <devices xmlns="http://tail-f.com/ns/ncs">
    <device>
      <name>{/device}</name>
      <config>
        <dummy xmlns="http://com/example/dummy">{/dummy}</dummy>
      </config>
    </device>
  </devices>
</config-template>
""", encoding="utf-8")
    run(["make", "-C", "nso-rundir/packages/dummy-service/src", "all"])
    run(["ncs-netsim", "--dir", "nso-rundir/netsim", "create-network",
         "nso-rundir/packages/dummy-nc-1.0", "2", "d"])
    run(["ncs-setup", "--netsim-dir", "nso-rundir/netsim", "--dest",
         "nso-rundir"])

    ssh_dir = Path("nso-rundir/netsim/d/d1/ssh")
    for key in ssh_dir.glob("ssh_host_*"):
        key.unlink()
    run(["ssh-keygen", "-m", "PEM", "-t", "ed25519", "-N", "",
         "-f", "ssh_host_ed25519_key"], cwd=ssh_dir)


def start_nso_and_devices():
    print_header(PURPLE, "Start the NSO daemon and simulated devices")
    run(["ncs", "--cd", "nso-rundir", "-c", str(Path.cwd() /
         "nso-rundir/ncs.conf")])
    run(["ncs-netsim", "start", "--dir", "nso-rundir/netsim"])


def start_all_listener():
    print_header(PURPLE,
                 "Start listening to all event notifications including the "
                 "NETCONF stream ")
    pause()
    return EventListener(all_events_mask(), "nso-rundir/logs/event.log",
                         stream="NETCONF")


def sync_device(device):
    with ncs.maapi.Maapi(load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            root = ncs.maagic.get_root(maapi)
            root.devices.device[device].sync_from.request()
    print("result true")


def configure_commit_queue_notifications():
    with ncs.maapi.single_write_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        path = "/ncs:services/commit-queue-notifications/subscription" \
               "{dummy-notif}"
        trans.maapi.create(trans.th, path)
        trans.maapi.set_elem(trans.th, "/dummy-service:dummy-service",
                             f"{path}/service-type")
        trans.apply()
        print("Commit complete.")


def create_dummy_service(device, dummy, label):
    with ncs.maapi.single_write_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        path = f"/dummy-service:dummy-service{{{device}}}"
        trans.maapi.create(trans.th, path)
        trans.maapi.set_elem(trans.th, dummy, f"{path}/dummy")
        params = trans.get_params()
        params.dry_run_native()
        print_native_dry_run(trans.apply_params(True, params))
        params = trans.get_params()
        params.commit_queue_async()
        params.label(label)
        trans.apply_params(True, params)
        print("Commit complete.")


def generate_first_notifications(listener):
    print_header(PURPLE,
                 "Enable the commit queue notification stream and generate "
                 "notifications by adding some dummy configuration on d0 "
                 "through the service and commit queue asynchronously",
                 leading_newline=False)
    pause()
    sync_device("d0")
    configure_commit_queue_notifications()
    mark = listener.mark()
    create_dummy_service("d0", "hello world", "hello-world-label")
    wait_for_commit_queue_completed(listener, since=mark)


def get_dummy_service_config():
    print_header(PURPLE, "Get the dummy service config using Python MAAPI")
    pause()
    with ncs.maapi.single_read_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        services = ncs.maagic.get_node(
            trans, "/dummy-service:dummy-service")
        for service in services:
            print(f"dummy-service {service.device}")
            print(f" dummy {service.dummy}")


def ncs_conf_tool_to_tmp(args):
    conf = Path("nso-rundir/ncs.conf")
    tmp = Path("nso-rundir/ncs.conf.tmp")
    with conf.open("r", encoding="utf-8") as stdin, \
            tmp.open("w", encoding="utf-8") as stdout:
        run(["ncs_conf_tool", *args], stdin=stdin, stdout=stdout)
    tmp.replace(conf)


def enable_call_home_and_ha_in_config(listener):
    print_header(PURPLE,
                 "Enable netconf-call-home and high-availablity in "
                 "ncs.conf and reload the config")
    pause()
    ncs_conf_tool_to_tmp(
        ["-e", "true", "ncs-config", "netconf-call-home", "enabled"])
    ncs_conf_tool_to_tmp(
        ["-a", "  <ha>\n"
         "    <enabled>true</enabled>\n"
         "    <ssl><enabled>false</enabled></ssl>\n"
         "  </ha>", "ncs-config"])
    mark = listener.mark()
    run(["ncs", "--reload"])
    listener.wait_for(
        lambda event_dict, _text:
        bool(event_dict["type"] & events.NOTIF_REOPEN_LOGS),
        "reopen logs event", since=mark)
    listener.print_log()


def configure_call_home_device():
    print_header(PURPLE, "Enable call home for the d1 device")
    pause()
    key_data = Path("nso-rundir/netsim/d/d1/ssh/"
                    "ssh_host_ed25519_key.pub").read_text(
                        encoding="utf-8").strip()
    with ncs.maapi.single_write_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        trans.maapi.set_elem(trans.th, "admin",
                             "/ncs:devices/device{d1}/local-user")
        trans.maapi.set_elem(trans.th, "reject-mismatch",
                             "/ncs:devices/device{d1}/ssh/"
                             "host-key-verification")
        host_key = "/ncs:devices/device{d1}/ssh/host-key{ssh-ed25519}"
        if not trans.maapi.exists(trans.th, host_key):
            trans.maapi.create(trans.th, host_key)
        trans.maapi.set_elem(trans.th, key_data, f"{host_key}/key-data")
        trans.maapi.set_elem(trans.th, "call-home",
                             "/ncs:devices/device{d1}/state/admin-state")
        trans.apply()
        print("Commit complete.")


def start_call_home_listener(dt_string):
    print_header(PURPLE,
                 "Start listening to call-home, commit-simple, ha-info, "
                 f"stream ncs-events (replay from {dt_string}), and "
                 "heartbeat event notifications only and redirect to a log "
                 "file")
    pause()
    listener = EventListener(
        call_home_events_mask(), "nso-rundir/logs/call-home-event.log",
        stream="ncs-events", start_time=dt_string)

    print_header(PURPLE, "Wait for the first heartbeat before continuing",
                 leading_newline=False)
    listener.wait_for(
        lambda event_dict, _text:
        bool(event_dict["type"] & events.NOTIF_HEARTBEAT),
        "heartbeat event")
    return listener


def call_home_from_d1():
    print_header(PURPLE, "Have the d1 device call home", leading_newline=False)
    pause()
    with ncs.maapi.Maapi(port=5011, path=None,
                         load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as maapi:
        maapi.netconf_ssh_call_home("127.0.0.1", 4334)


def configure_d1_service(listener):
    print_header(PURPLE, "Add some configuration to the d1 device",
                 leading_newline=False)
    pause()
    sync_device("d1")
    mark = listener.mark()
    create_dummy_service("d1", "calling home", "calling-home-label")
    wait_for_commit_queue_completed(listener, since=mark)


def configure_ha():
    print_header(PURPLE, "Enable HA and change role to generate ha-info "
                 "notifications", leading_newline=False)
    pause()
    with ncs.maapi.single_write_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        trans.maapi.set_elem(trans.th, "super-secret",
                             "/ncs:high-availability/token")
        node = "/ncs:high-availability/ha-node{n1}"
        trans.maapi.create(trans.th, node)
        trans.maapi.set_elem(trans.th, "127.0.0.1", f"{node}/address")
        trans.maapi.set_elem(trans.th, "primary", f"{node}/nominal-role")
        trans.apply()
        print("Commit complete.")

    with ncs.maapi.Maapi(load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            root = ncs.maagic.get_root(maapi)
            print(f"result {root.high_availability.enable.request().result}")
            print(f"result {root.high_availability.be_none.request().result}")
            print(f"result "
                  f"{root.high_availability.be_primary.request().result}")


def cleanup():
    if os.environ.get("NONINTERACTIVE"):
        return

    print_header(GREEN, "Cleanup", leading_newline=False)
    pause()
    run(["make", "stop"])
    run(["make", "clean"])


def main():
    reset_example()
    create_dummy_example()
    start_nso_and_devices()
    first_listener = start_all_listener()
    dt_string = datetime.now(timezone.utc).isoformat()
    generate_first_notifications(first_listener)
    get_dummy_service_config()
    enable_call_home_and_ha_in_config(first_listener)

    print_header(PURPLE, "Stop listening to all event notifications")
    pause()
    first_listener.stop()

    configure_call_home_device()
    call_home_listener = start_call_home_listener(dt_string)
    mark = call_home_listener.mark()
    call_home_from_d1()

    print_header(PURPLE,
                 "Wait for the call-home device connected event "
                 "notification")
    call_home_listener.wait_for(
        lambda event_dict, _text:
        bool(event_dict["type"] & events.NCS_NOTIF_CALL_HOME_INFO) and
        event_dict["call_home"]["type"] == events.CALL_HOME_DEVICE_CONNECTED,
        "call-home device connected event", since=mark)

    mark = call_home_listener.mark()
    configure_d1_service(call_home_listener)
    print_header(PURPLE, "Wait for the commit-simple event notification")
    call_home_listener.wait_for(
        lambda event_dict, _text:
        bool(event_dict["type"] & events.NOTIF_COMMIT_SIMPLE),
        "commit-simple event", since=mark)

    mark = call_home_listener.mark()
    configure_ha()
    print_header(PURPLE,
                 "Get the received events in "
                 "nso-rundir/logs/call-home-event.log")
    pause()
    call_home_listener.wait_for(
        lambda event_dict, _text:
        bool(event_dict["type"] & events.NOTIF_HA_INFO) and
        event_dict["hnot"]["type"] == events.HA_INFO_IS_PRIMARY,
        "HA primary event", since=mark)
    call_home_listener.print_log()

    cleanup()
    call_home_listener.stop()
    print_header(GREEN, "Done!")


if __name__ == "__main__":
    main()
