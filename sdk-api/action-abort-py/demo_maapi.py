#!/usr/bin/env python3
"""Run the abort action demo using the high-level NSO MAAPI API."""

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


def reset_example():
    print_header(GREEN, "Python MAAPI abort action demo")
    print_header(PURPLE, "Reset", leading_newline=False)
    run(["make", "stop"], check=False, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL)
    run(["make", "clean"])


def start_example():
    print_header(GREEN, "Running the Example")
    print_header(PURPLE, "Build the package and start NSO",
                 leading_newline=False)
    run(["make", "all", "start"])


def sync_devices():
    with ncs.maapi.Maapi() as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            root = ncs.maagic.get_root(maapi)
            root.devices.sync_from.request()


def run_iosxr_config(timeout, work_duration):
    with ncs.maapi.Maapi() as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            root = ncs.maagic.get_root(maapi)
            action = root.action_abort_test.iosxr_config
            params = action.get_input()
            params.timeout = timeout
            params.device = "xr1"
            params.work_duration = work_duration
            try:
                output = action.request(params)
            except _ncs.error.Error as exc:
                if "application timeout" in str(exc):
                    print("Error: application timeout")
                    return
                raise
            outcome = str(output.outcome).split(":", 1)[0]
            print(f"outcome {outcome}")


def show_loopback():
    with ncs.maapi.single_read_trans("admin", "python") as trans:
        root = ncs.maagic.get_root(trans)
        interface = root.devices.device["xr1"].config.cisco_ios_xr__interface
        if not interface.Loopback.exists(0):
            return
        loopback = interface.Loopback[0]
        print("interface Loopback0")
        description = loopback.description
        if description:
            print(f" description {description}")
        address = loopback.ipv4.address.ip
        if address:
            print(f" ipv4 address {address}")


def run_action_case(title, timeout, work_duration):
    print_header(PURPLE, title)
    pause()
    run_iosxr_config(timeout, work_duration)
    show_loopback()


def show_log():
    print_header(PURPLE, "View the log output in ncs-python-vm-actions.log")
    pause()
    print(Path("logs/ncs-python-vm-abort-action.log").read_text(), end="")


def cleanup():
    if os.environ.get("NONINTERACTIVE"):
        return

    print_header(GREEN, "Cleanup")
    pause()
    print_header(PURPLE, "Stop NSO and clean all created files")
    run(["make", "stop", "clean"])


def main():
    reset_example()
    start_example()
    sync_devices()

    run_action_case(
        "Run the 'iosxr-config' action completing it before a timeout",
        timeout=10,
        work_duration=5)
    run_action_case(
        "Run the 'iosxr-config' action with an abort due to timeout",
        timeout=2,
        work_duration=10)
    run_action_case(
        "Again abort the 'iosxr-config' action due to a timeout",
        timeout=5,
        work_duration=20)
    run_action_case(
        "Again complete the 'iosxr-config' action before a timeout",
        timeout=6,
        work_duration=3)

    show_log()
    cleanup()
    print_header(GREEN, "Done!")


if __name__ == "__main__":
    main()
