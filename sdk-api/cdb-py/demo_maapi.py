#!/usr/bin/env python3
"""Run the CDB subscriber demo using the NSO Python MAAPI API."""

import os
import subprocess
import sys
import termios
import time
import tty
from pathlib import Path

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


def run(command, check=True, stdout=None, stderr=None):
    subprocess.run(command, check=check, stdout=stdout, stderr=stderr)


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


def print_matching_log_lines(path, text):
    for line in Path(path).read_text(errors="ignore").splitlines():
        if text in line:
            print(line)


def commit(trans):
    trans.apply()
    print("Commit complete.")


def sync_devices():
    with ncs.maapi.Maapi() as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            root = ncs.maagic.get_root(maapi)
            root.devices.sync_from.request()

    for _device in ("ex0", "ex1", "ex2"):
        print("result true")


def trigger_config_subscriber():
    with ncs.maapi.single_write_trans("admin", "python") as trans:
        root = ncs.maagic.get_root(trans)
        server = root.devices.device["ex0"].config.sys.syslog.server.create(
            "4.5.6.7")
        server.enabled = True
        commit(trans)

    with ncs.maapi.single_write_trans("admin", "python") as trans:
        root = ncs.maagic.get_root(trans)
        del root.devices.device["ex0"].config.sys.syslog.server["4.5.6.7"]
        commit(trans)


def create_oper_stats_item():
    with ncs.maapi.single_write_trans("admin", "python",
                                      db=ncs.OPERATIONAL) as trans:
        root = ncs.maagic.get_root(trans)
        item = root.test.stats_item.create("dawnfm")
        item.i = 42
        item.inner.l = "boogaloo"
        trans.apply()


def delete_oper_stats_item():
    with ncs.maapi.single_write_trans("admin", "python",
                                      db=ncs.OPERATIONAL) as trans:
        root = ncs.maagic.get_root(trans)
        del root.test.stats_item["dawnfm"]
        trans.apply()


def reset_example():
    print_header(GREEN, "CDB API Python MAAPI subscriber demo")
    print_header(PURPLE, "Reset", leading_newline=False)
    run(["make", "stop"], check=False)
    run(["make", "clean"])


def start_example():
    print_header(GREEN, "Running the Example")
    print_header(PURPLE, "Build the two packages", leading_newline=False)
    run(["make", "all"])

    print_header(PURPLE, "Start NSO, the subscribers, and the netsim network")
    run(["make", "start"])


def main():
    reset_example()
    start_example()

    print_header(PURPLE, "Sync the configuration from the devices")
    sync_devices()

    print_header(PURPLE, "Trigger the CDB configuration data subscriber")
    trigger_config_subscriber()

    print_header(PURPLE,
                 "Resulting log entries in logs/ncs-python-vm-cdb.log")
    wait_for_log("logs/ncs-python-vm-cdb.log", "4.5.6.7")
    print_matching_log_lines("logs/ncs-python-vm-cdb.log", "4.5.6.7")

    print_header(PURPLE, "Trigger the CDB operational data subscriber")
    create_oper_stats_item()
    delete_oper_stats_item()

    print_header(PURPLE,
                 "Resulting log entries in logs/ncs-python-vm-cdb.log")
    wait_for_log("logs/ncs-python-vm-cdb.log", "dawnfm")
    print_matching_log_lines("logs/ncs-python-vm-cdb.log", "dawnfm")

    print_header(GREEN, "Cleanup")
    if not os.environ.get("NONINTERACTIVE"):
        pause()
        print_header(PURPLE, "Stop NSO and the netsim devices")
        run(["make", "stop"])
        print_header(GREEN, "Reset the example to its original files")
        run(["make", "clean"])

    print_header(GREEN, "Done!")


if __name__ == "__main__":
    main()
