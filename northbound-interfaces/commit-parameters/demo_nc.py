#!/usr/bin/env python3

"""NETCONF commit parameters demo."""

import os
import subprocess
import sys


NONINTERACTIVE = os.environ.get("NONINTERACTIVE")

RED = "\033[0;31m"
GREEN = "\033[0;32m"
PURPLE = "\033[0;35m"
NC = "\033[0m"


def pause(prompt=None):
    if prompt is None:
        prompt = f"{RED}##### Press Enter to continue or ctrl-c to exit{NC}\n"
    if not NONINTERACTIVE:
        input(prompt)


def print_step(color, message):
    print(f"\n{color}##### {message}{NC}", flush=True)


def stop_example():
    subprocess.run(
        ["make", "stop"],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        text=True,
    )


def clean_example():
    subprocess.run(
        ["make", "clean"],
        check=True,
        text=True,
    )


def cleanup_environment():
    stop_example()
    clean_example()


def run_make(*targets):
    subprocess.run(
        ["make", *targets],
        check=True,
        text=True,
    )


def prepare_example_state():
    subprocess.run(
        ["make", "sync-from", "bootstrap"],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        text=True,
    )


def run_ncs_cli(commands):
    script = "\n".join(commands) + "\n"
    print(f"{PURPLE}ncs_cli -n -u admin -C{NC}", flush=True)
    print(script, flush=True)
    proc = subprocess.run(
        ["ncs_cli", "-n", "-u", "admin", "-C"],
        input=script,
        capture_output=True,
        check=False,
        text=True,
    )
    if proc.stdout:
        print(proc.stdout, end="", flush=True)
    if proc.stderr:
        print(proc.stderr, end="", file=sys.stderr, flush=True)
    if proc.returncode != 0:
        raise RuntimeError(f"ncs_cli exited with status {proc.returncode}")
    return proc.stdout


def print_command(args, payload=None):
    cmd = " ".join(["netconf-console", *args])
    print(f"{PURPLE}{cmd}{NC}", flush=True)
    if payload is not None:
        print(payload, flush=True)


def run_netconf_console(args, payload=None):
    print_command(args, payload=payload)
    proc = subprocess.run(
        ["netconf-console", *args],
        input=payload,
        capture_output=True,
        check=False,
        text=True,
    )
    if proc.stdout:
        print(proc.stdout, end="", flush=True)
    if proc.stderr:
        print(proc.stderr, end="", file=sys.stderr, flush=True)
    if proc.returncode != 0:
        raise RuntimeError(
            f"netconf-console exited with status {proc.returncode}"
        )
    if "<rpc-error>" in proc.stdout:
        raise RuntimeError("NETCONF request returned <rpc-error>")
    return proc.stdout


EDIT_CONFIG_RPC = (
    "<edit-config>\n"
    "  <target>\n"
    "    <running/>\n"
    "  </target>\n"
    "  <test-option>set</test-option>\n"
    "  <error-option>rollback-on-error</error-option>\n"
    '  <label xmlns="http://tail-f.com/ns/netconf/ncs">'
    "netconf-demo"
    "</label>\n"
    '  <comment xmlns="http://tail-f.com/ns/netconf/ncs">'
    "Committed through structured NETCONF commit parameters"
    "</comment>\n"
    "  <config>\n"
    '    <enterprise-dns xmlns="http://example.com/enterprise-dns">\n'
    "      <enterprise-dns-instances>\n"
    "        <name>branch-office</name>\n"
    "        <search-domain>netconf-demo.example</search-domain>\n"
    "      </enterprise-dns-instances>\n"
    "    </enterprise-dns>\n"
    "  </config>\n"
    "</edit-config>\n"
)

VERIFY_GET_CONFIG_RPC = """<get-config>
  <source>
    <running/>
  </source>
  <with-service-meta-data xmlns="http://tail-f.com/ns/netconf/ncs"/>
  <filter type="subtree">
    <enterprise-dns xmlns="http://example.com/enterprise-dns">
      <enterprise-dns-instances>
        <name>branch-office</name>
        <search-domain/>
      </enterprise-dns-instances>
    </enterprise-dns>
  </filter>
</get-config>
"""


def main():
    print_step(GREEN, "NETCONF commit parameters demo")

    try:
        print_step(PURPLE, "Start clean")
        cleanup_environment()

        print_step(PURPLE, "Build and start the enterprise-dns example")
        run_make("all", "start")

        print_step(PURPLE, "Prepare the example state")
        prepare_example_state()

        print_step(
            GREEN,
            "Step 1: Use RFC 6241 edit-config with structured params",
        )
        pause()
        run_netconf_console(["--rpc=-"], payload=EDIT_CONFIG_RPC)

        print_step(
            GREEN,
            "Step 2: Verify the NETCONF commit label and comment in the CLI",
        )
        pause()
        run_ncs_cli(["show configuration commit list | nomore"])

        print_step(PURPLE, "Verify the committed value over NETCONF")
        run_netconf_console(["--rpc=-"], payload=VERIFY_GET_CONFIG_RPC)

    finally:
        if not NONINTERACTIVE:
            print_step(GREEN, "Cleanup")
            pause()
            cleanup_environment()

    print(f"\n{GREEN}##### Done!{NC}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # pragma: no cover - demo script error path
        print(f"{RED}NETCONF demo failed: {exc}{NC}", file=sys.stderr)
        raise
