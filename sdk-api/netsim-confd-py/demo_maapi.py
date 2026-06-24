#!/usr/bin/env python3
"""Run the ConfD netsim Python SDK demo using Python MAAPI calls."""

import os
import shutil
import socket
import subprocess
import sys
import termios
import time
import tty
from pathlib import Path

import _ncs
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


def run(command, check=True, stdout=None, stderr=None, cwd=None):
    subprocess.run(command, check=check, stdout=stdout, stderr=stderr,
                   cwd=cwd)


def run_text(command):
    return subprocess.check_output(command, text=True).strip()


def print_header(color, text, leading_newline=True):
    prefix = "\n" if leading_newline else ""
    print(f"{prefix}{color}##### {text}{NC}", flush=True)


def wait_for_log(path, text, timeout=30):
    end = time.time() + timeout
    log_path = Path(path)
    while time.time() < end:
        if log_path.exists() and text in log_path.read_text(errors="ignore"):
            return
        time.sleep(0.1)
    raise TimeoutError(f"Timed out waiting for {text!r} in {path}")


def print_native_dry_run(result):
    if result.get("outformat") != "native":
        return
    for data in result.get("device", {}).values():
        print(data, end="" if data.endswith("\n") else "\n")
    local = result.get("local-node")
    if local:
        print(local, end="" if local.endswith("\n") else "\n")


def maapi_save_config(trans, flags, path):
    root = ncs.maagic.get_root(trans)
    maapi = ncs.maagic.get_maapi(root)
    stream_id = _ncs.maapi.save_config(maapi.msock, trans.th, flags, path)
    stream = socket.socket(maapi.msock.family)
    peername = maapi.msock.getpeername()
    if isinstance(peername, tuple):
        address, port = peername
        _ncs.stream_connect(stream, stream_id, 0, address, port)
    else:
        _ncs.stream_connect(stream, stream_id, 0, path=peername)

    chunks = []
    while True:
        chunk = stream.recv(4096)
        if not chunk:
            stream.close()
            return b"".join(chunks).decode("utf-8")
        chunks.append(chunk)


def reset_example():
    print_header(GREEN,
                 "NSO ConfD netsim Python SDK application MAAPI demo")
    print_header(PURPLE, "Reset", leading_newline=False)
    run(["ncs", "--stop"], check=False, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL)
    run(["ncs-netsim", "--dir", "nso-rundir/netsim", "stop"],
        check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    shutil.rmtree("nso-rundir", ignore_errors=True)


def setup_netsim():
    print_header(GREEN, "Setting up and running netsim")
    pause()

    print_header(PURPLE,
                 "Set up NSO and create a single device simulated network",
                 leading_newline=False)
    run(["ncs-setup", "--package", "package-repository/dummy", "--use-copy",
         "--dest", "nso-rundir"])
    run(["make", "-C", "nso-rundir/packages/dummy/src/"])
    run(["ncs-netsim", "--dir", "nso-rundir/netsim", "create-network",
         "nso-rundir/packages/dummy", "1", "d"])
    with open("nso-rundir/ncs-cdb/device-init.xml", "w",
              encoding="utf-8") as init_xml:
        subprocess.run(["ncs-netsim", "--dir", "nso-rundir/netsim",
                        "ncs-xml-init", "d0"],
                       check=True, stdout=init_xml)


def start_nso_and_device():
    print_header(PURPLE, "Start NSO and the simulated device")
    run(["ncs-netsim", "--dir", "nso-rundir/netsim", "start"])
    run(["ncs", "--cd", "./nso-rundir"])


def sync_device():
    print_header(PURPLE,
                 "Sync the configuration from the netsim device to NSO")
    with ncs.maapi.single_read_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        root = ncs.maagic.get_root(trans)
        for device in root.devices.device:
            print(f"device {device.name}")
        root.devices.device["d0"].sync_from.request()
    print("result true")


def configure_dummy_name():
    with ncs.maapi.single_write_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        trans.maapi.set_elem(
            trans.th, "test-device",
            "/ncs:devices/device{d0}/config/dummy:dummy/name")
        params = trans.get_params()
        params.dry_run_native()
        print_native_dry_run(trans.apply_params(True, params))
        trans.apply_params(True, trans.get_params())
        print("Commit complete.")


def get_netsim_ipc_port():
    return int(run_text(["ncs-netsim", "--dir", "nso-rundir/netsim",
                         "get-port", "d0", "ipc"]))


def set_device_oper_state():
    port = get_netsim_ipc_port()
    with ncs.maapi.single_write_trans(
            "admin", "python", db=ncs.OPERATIONAL, port=port, path=None,
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        trans.maapi.set_elem(trans.th, "new-state", "/dummy:dummy/state")
        trans.apply()


def configure_notification_subscription():
    with ncs.maapi.single_write_trans(
            "admin", "python",
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        path = "/ncs:devices/device{d0}/notifications/subscription" \
               "{something-done-notif}"
        trans.maapi.create(trans.th, path)
        trans.maapi.set_elem(trans.th, "something_done_notifications",
                             f"{path}/stream")
        trans.maapi.set_elem(trans.th, "admin", f"{path}/local-user")
        trans.apply()
        print("Commit complete.")


def request_do_something():
    with ncs.maapi.Maapi(load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            tvs = maapi.request_action(
                [], 0,
                "/ncs:devices/device{d0}/live-status/"
                "dummy:dummy/do-something")
    for tv in tvs:
        if _ncs.hash2str(tv.tag) == "result":
            print(f"result {tv.v}")


def show_received_notifications():
    with ncs.maapi.single_read_trans(
            "admin", "python", db=ncs.OPERATIONAL,
            load_schemas=ncs.maapi.LOAD_SCHEMAS_RELOAD) as trans:
        flags = (_ncs.maapi.CONFIG_XML_PRETTY +
                 _ncs.maapi.CONFIG_XPATH +
                 _ncs.maapi.CONFIG_OPER_ONLY)
        data = maapi_save_config(
            trans, flags,
            "/devices/device[name='d0']/notifications/"
            "received-notifications")
        print(data)


def cleanup():
    if os.environ.get("NONINTERACTIVE"):
        return

    print_header(GREEN, "Cleanup")
    pause()
    print_header(PURPLE, "Stop NSO and the simulated device",
                 leading_newline=False)
    run(["ncs", "--stop"])
    run(["ncs-netsim", "--dir", "nso-rundir/netsim", "stop"])

    print_header(GREEN, "Reset the example to its original files")
    pause()
    shutil.rmtree("nso-rundir", ignore_errors=True)


def main():
    reset_example()
    setup_netsim()
    start_nso_and_device()
    sync_device()

    print_header(GREEN, "Run a netsim ConfD application Demo")
    pause()

    print_header(PURPLE,
                 "Change the device configuration to trigger the config "
                 "subscriber")
    configure_dummy_name()

    print_header(PURPLE,
                 "Check the netsim device log for the configuration "
                 "subscriber application being notified of changes")
    wait_for_log("nso-rundir/netsim/d/d0/logs/dummy.log",
                 "newv=test-device")
    print(Path("nso-rundir/netsim/d/d0/logs/dummy.log").read_text(), end="")

    print_header(PURPLE,
                 "Make operational data changes over MAAPI using Python "
                 "setting /dummy:dummy/state to \"new-state\"")
    set_device_oper_state()

    print_header(PURPLE,
                 "Check the netsim device log for the operational data "
                 "subscriber application being notified of changes")
    wait_for_log("nso-rundir/netsim/d/d0/logs/dummy.log",
                 "newv=new-state")
    print(Path("nso-rundir/netsim/d/d0/logs/dummy.log").read_text(), end="")

    print_header(PURPLE,
                 "Configure NSO to subscribe to NETCONF notifications for "
                 "the something_done_notifications stream")
    configure_notification_subscription()

    print_header(PURPLE,
                 "Call the action on the d0 simulated device using Python "
                 "MAAPI")
    request_do_something()

    print_header(PURPLE,
                 "Check the netsim device log for the action application "
                 "being invoked")
    wait_for_log("nso-rundir/netsim/d/d0/logs/dummy.log", "do-something")
    print(Path("nso-rundir/netsim/d/d0/logs/dummy.log").read_text(), end="")

    print_header(PURPLE,
                 "Show received device something-done notification that was "
                 "sent from the action application")
    show_received_notifications()

    cleanup()
    print_header(GREEN, "Done!")


if __name__ == "__main__":
    main()
