#!/usr/bin/env python3
"""Run the Python action demo using the high-level NSO MAAPI API."""

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
    """Run an external lifecycle command."""
    subprocess.run(command, check=check, stdout=stdout, stderr=stderr)


def print_header(color, text, leading_newline=True):
    prefix = "\n" if leading_newline else ""
    print(f"{prefix}{color}##### {text}{NC}", flush=True)


def reset_example():
    print_header(GREEN, "Python MAAPI action demo")
    print_header(PURPLE, "Reset", leading_newline=False)
    run(["make", "stop"], check=False, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL)
    run(["make", "clean"])

def start_example():
    print_header(GREEN, "Running the Example")
    print_header(PURPLE, "Build the package and start NSO",
                 leading_newline=False)
    run(["make", "all", "start"])


def request_action(action, params=None):
    output = action.request(params)
    return output


def run_actions():
    with ncs.maapi.Maapi() as maapi:
        with ncs.maapi.Session(maapi, "admin", "python"):
            root = ncs.maagic.get_root(maapi)

            print_header(PURPLE, "Run the 'reboot' action")
            request_action(root.action_test.system.reboot)

            print_header(PURPLE, "Run the 'restart' action")
            params = root.action_test.system.restart.get_input()
            params.mode = "xx"
            params.data.create()
            params.data.debug.create()
            output = request_action(root.action_test.system.restart, params)
            print(f"time {output.time}")

            print_header(PURPLE, "Run the 'verify' action")
            output = request_action(root.action_test.system.verify)
            print(f"consistent {str(output.consistent).lower()}")

    print_header(PURPLE,
                 "Change the 'sys-name' to have the 'verify' action return "
                 "'false'")
    with ncs.maapi.single_write_trans("admin", "python") as trans:
        root = ncs.maagic.get_root(trans)
        root.action_test.system.sys_name = "please-return-false"
        output = request_action(root.action_test.system.verify)
        print(f"consistent {str(output.consistent).lower()}")

    when = time.strftime("%H:%M:%S")
    print_header(PURPLE, "Run the 'reset' action")
    with ncs.maapi.single_write_trans("admin", "python") as trans:
        root = ncs.maagic.get_root(trans)
        server = root.action_test.server.create("test")
        params = server.reset.get_input()
        params.when = when
        output = request_action(server.reset, params)
        print(f"time {output.time}")


def show_log():
    print_header(PURPLE, "View the log output in ncs-python-vm-actions.log")
    print(Path("logs/ncs-python-vm-actions.log").read_text(), end="")


def cleanup():
    if os.environ.get("NONINTERACTIVE"):
        return

    print_header(GREEN, "Cleanup", leading_newline=False)
    pause()
    print_header(PURPLE, "Stop NSO and clean all created files",
                 leading_newline=False)
    run(["make", "stop", "clean"])


def main():
    reset_example()
    start_example()
    run_actions()
    show_log()
    cleanup()
    print_header(GREEN, "Done!")


if __name__ == "__main__":
    main()
