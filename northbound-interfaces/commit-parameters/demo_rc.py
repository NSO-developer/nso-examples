#!/usr/bin/env python3

"""RESTCONF commit parameters demo using requests."""

import base64
import json
import os
import subprocess
import sys

import requests


AUTH = ("admin", "admin")
BASE_URL = "http://localhost:8080/restconf"
SERVICE_PATH = (
    "/data/enterprise-dns:enterprise-dns"
    "/enterprise-dns-instances=branch-office"
)
SEARCH_DOMAIN_PATH = SERVICE_PATH + "/search-domain"
NONINTERACTIVE = os.environ.get("NONINTERACTIVE")

RED = "\033[0;31m"
GREEN = "\033[0;32m"
PURPLE = "\033[0;35m"
NC = "\033[0m"

session = requests.Session()
session.auth = AUTH


def pause(prompt=None):
    if prompt is None:
        prompt = f"{RED}##### Press Enter to continue or ctrl-c to exit{NC}\n"
    if not NONINTERACTIVE:
        input(prompt)


def print_step(color, message):
    print(f"\n{color}##### {message}{NC}", flush=True)


def encode_commit_params(payload):
    return base64.b64encode(json.dumps(payload).encode()).decode()


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


def print_request(method, path, *, params=None, headers=None, body=None):
    request = requests.Request(
        method,
        BASE_URL + path,
        params=params,
        headers=headers,
        data=body,
    )
    prepared = session.prepare_request(request)
    print(f"{PURPLE}{method} {prepared.url}{NC}", flush=True)
    if headers:
        for name, value in headers.items():
            print(f"{name}: {value}", flush=True)
    if body:
        print(body, flush=True)


def send_request(method, path, *, params=None, headers=None, body=None):
    print_request(method, path, params=params, headers=headers, body=body)
    response = session.request(
        method,
        BASE_URL + path,
        params=params,
        headers=headers,
        data=body,
    )
    print(response.text, flush=True)
    print(f"Status code: {response.status_code}", flush=True)
    response.raise_for_status()
    return response


def main():
    print_step(GREEN, "RESTCONF commit parameters demo")

    try:
        print_step(PURPLE, "Start clean")
        cleanup_environment()

        print_step(PURPLE, "Build and start the enterprise-dns example")
        run_make("all", "start")

        print_step(PURPLE, "Prepare the example state")
        prepare_example_state()

        patch_body = (
            "<search-domain>restconf-demo.example</search-domain>\n"
        )
        dry_run_params = encode_commit_params(
            {"label": "restconf-query-demo", "dry-run": {"outformat": "cli-c"}}
        )
        header_params = encode_commit_params(
            {
                "label": "restconf-header-demo",
                "comment": (
                    "Committed through structured RESTCONF "
                    "commit parameters"
                ),
            }
        )

        print_step(PURPLE, "Prepare a RESTCONF PATCH payload")
        print(patch_body, flush=True)

        print_step(
            GREEN,
            "Step 1: Use the params query parameter for a dry-run",
        )
        pause()
        send_request(
            "PATCH",
            SEARCH_DOMAIN_PATH,
            params={"params": dry_run_params},
            headers={
                "Accept": "application/yang-data+json",
                "Content-Type": "application/yang-data+xml",
            },
            body=patch_body,
        )

        print_step(
            GREEN,
            (
                "Step 2: Use the X-Cisco-NSO-Commit-Params "
                "header for the actual commit"
            ),
        )
        pause()
        send_request(
            "PATCH",
            SEARCH_DOMAIN_PATH,
            params={"rollback-id": "true"},
            headers={
                "Accept": "application/yang-data+json",
                "Content-Type": "application/yang-data+xml",
                "X-Cisco-NSO-Commit-Params": header_params,
            },
            body=patch_body,
        )

        print_step(
            GREEN,
            "Step 3: Verify the RESTCONF commit label and comment in the CLI",
        )
        pause()
        run_ncs_cli(["show configuration commit list | nomore"])

        print_step(PURPLE, "Verify the committed value over RESTCONF")
        send_request(
            "GET",
            SEARCH_DOMAIN_PATH,
            headers={"Accept": "application/yang-data+json"},
        )

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
    except requests.RequestException as exc:
        print(f"{RED}RESTCONF request failed: {exc}{NC}", file=sys.stderr)
        raise
