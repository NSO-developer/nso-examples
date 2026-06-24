#!/usr/bin/env python3
"""Run the two-phase CDB subscriber demo using NSO Python MAAPI."""

import os
import subprocess
import sys
import termios
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


def run(command, check=True, stdout=None, stderr=None):
    subprocess.run(command, check=check, stdout=stdout, stderr=stderr)


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


def dry_run_then_commit(trans):
    params = trans.get_params()
    params.dry_run_native()
    result = trans.apply_params(True, params)
    print_native_dry_run(result)
    trans.apply_params(True, trans.get_params())
    print("Commit complete.")


def sync_devices():
    with ncs.maapi.Maapi() as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            root = ncs.maagic.get_root(maapi)
            root.devices.sync_from.request()


def configure_blast_radius():
    sync_devices()
    with ncs.maapi.single_write_trans("admin", "python") as trans:
        root = ncs.maagic.get_root(trans)
        root.devices.blast_radius.max_devices = 2
        trans.apply()
        print("Commit complete.")


def configure_two_devices():
    with ncs.maapi.single_write_trans("admin", "python") as trans:
        root = ncs.maagic.get_root(trans)
        for device in ("ex0", "ex1"):
            iface = root.devices.device[device].config.sys.interfaces \
                .interface.create("eth42")
            iface.enabled.create()
        dry_run_then_commit(trans)


def configure_three_devices():
    try:
        with ncs.maapi.single_write_trans("admin", "python") as trans:
            root = ncs.maagic.get_root(trans)
            for device, description in (
                    ("ex0", "test1"),
                    ("ex1", "test2"),
                    ("ex2", "test3")):
                interfaces = root.devices.device[device].config.sys \
                    .interfaces.interface
                if interfaces.exists("eth42"):
                    iface = interfaces["eth42"]
                else:
                    iface = interfaces.create("eth42")
                iface.description = description
            dry_run_then_commit(trans)
    except _ncs.error.Error:
        print("Aborted")


def reset_example():
    print_header(GREEN,
                 "Two phase commit mandatory subscriber MAAPI blast radius "
                 "demo",
                 leading_newline=False)
    print_header(PURPLE, "Start clean", leading_newline=False)
    run(["make", "stop"], check=False)
    run(["make", "clean"])


def start_example():
    print_header(PURPLE, "Build the packages")
    run(["make", "all"])

    print_header(PURPLE, "Start NSO, the subscribers, and the netsim network")
    run(["make", "start"])


def cleanup():
    if os.environ.get("NONINTERACTIVE"):
        return

    print_header(GREEN, "Cleanup")
    pause()
    run(["make", "stop"])
    run(["make", "clean"])


def main():
    reset_example()
    start_example()

    print_header(PURPLE,
                 "Configure the device blast radius to maximum two devices")
    configure_blast_radius()

    print_header(PURPLE, "Configure two devices")
    configure_two_devices()

    print_header(PURPLE,
                 "Configure three devices causing the commit to be aborted "
                 "in the prepare phase")
    configure_three_devices()

    print_header(PURPLE,
                 "Show the logs/ncs-python-vm-device-blast-radius.log file "
                 "with the aborted commit")
    print(Path("logs/ncs-python-vm-device-blast-radius.log").read_text(),
          end="")

    cleanup()
    print_header(GREEN, "Done!")


if __name__ == "__main__":
    main()
