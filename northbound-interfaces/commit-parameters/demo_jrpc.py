#!/usr/bin/env python3

"""JSON-RPC commit parameters demo using requests."""

import json
import os
import subprocess
import sys

import requests


BASE_URL = "http://localhost:8080/jsonrpc"
SERVICE_PATH = (
    "/enterprise-dns:enterprise-dns"
    "/enterprise-dns-instances{branch-office}"
)
SEARCH_PATH = SERVICE_PATH + "/search-domain"
NONINTERACTIVE = os.environ.get("NONINTERACTIVE")

RED = "\033[0;31m"
GREEN = "\033[0;32m"
PURPLE = "\033[0;35m"
NC = "\033[0m"

session = requests.Session()
request_id = 0


def pause(prompt=None):
    if prompt is None:
        prompt = f"{RED}##### Press Enter to continue or ctrl-c to exit{NC}\n"
    if not NONINTERACTIVE:
        input(prompt)


def print_step(color, message):
    print(f"\n{color}##### {message}{NC}", flush=True)


def print_json(value):
    print(json.dumps(value, indent=2, sort_keys=True), flush=True)


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


def print_request(payload):
    print(f"{PURPLE}POST {BASE_URL}{NC}", flush=True)
    print_json(payload)


def jsonrpc_call(method, params=None):
    global request_id
    request_id += 1
    payload = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": method,
    }
    if params is not None:
        payload["params"] = params

    print_request(payload)
    response = session.post(BASE_URL, json=payload)
    print(response.text, flush=True)
    print(f"Status code: {response.status_code}", flush=True)
    response.raise_for_status()
    result = response.json()
    if "error" in result:
        raise RuntimeError(
            json.dumps(result["error"], indent=2, sort_keys=True)
        )
    return result.get("result", {})


def new_trans(mode="read_write"):
    result = jsonrpc_call(
        "new_trans",
        {"db": "running", "mode": mode, "conf_mode": "private"},
    )
    return result["th"]


def delete_trans_if_present(th):
    trans = jsonrpc_call("get_trans").get("trans", [])
    if any(item["th"] == th for item in trans):
        jsonrpc_call("delete_trans", {"th": th})


def logout_if_needed():
    if session.cookies.get("sessionid") is not None:
        jsonrpc_call("logout")


def main():
    active_txs = set()
    logged_in = False
    print_step(GREEN, "JSON-RPC commit parameters demo")

    try:
        print_step(PURPLE, "Start clean")
        cleanup_environment()

        print_step(PURPLE, "Build and start the enterprise-dns example")
        run_make("all", "start")

        print_step(PURPLE, "Prepare the example state")
        prepare_example_state()

        print_step(PURPLE, "Login to the JSON-RPC API")
        jsonrpc_call("login", {"user": "admin", "passwd": "admin"})
        logged_in = True

        dry_run_params = {
            "label": "jsonrpc-dry-run-demo",
            "dry-run": {"outformat": "cli-c"},
        }
        actual_params = {
            "label": "jsonrpc-demo",
            "comment": (
                "Committed through structured JSON-RPC "
                "commit parameters"
            ),
        }

        print_step(
            GREEN,
            "Step 1: Use validate_commit and commit for a dry-run preview",
        )
        pause()
        dry_run_th = new_trans()
        active_txs.add(dry_run_th)
        jsonrpc_call(
            "set_value",
            {"th": dry_run_th, "path": SEARCH_PATH,
             "value": "jsonrpc-preview.example"},
        )
        jsonrpc_call(
            "validate_commit",
            {"th": dry_run_th, "params": dry_run_params},
        )
        dry_run_result = jsonrpc_call(
            "commit",
            {"th": dry_run_th, "params": dry_run_params},
        )
        print_step(PURPLE, "Dry-run result")
        print_json(dry_run_result)
        delete_trans_if_present(dry_run_th)
        active_txs.discard(dry_run_th)

        print_step(
            GREEN,
            "Step 2: Commit with structured label and comment parameters",
        )
        pause()
        commit_th = new_trans()
        active_txs.add(commit_th)
        jsonrpc_call(
            "set_value",
            {"th": commit_th, "path": SEARCH_PATH,
             "value": "jsonrpc-demo.example"},
        )
        jsonrpc_call(
            "validate_commit",
            {"th": commit_th, "params": actual_params},
        )
        commit_result = jsonrpc_call(
            "commit",
            {
                "th": commit_th,
                "params": actual_params,
                "rollback-id": True,
            },
        )
        active_txs.discard(commit_th)
        print_step(PURPLE, "Commit result")
        print_json(commit_result)

        print_step(
            GREEN,
            "Step 3: Verify the JSON-RPC commit label and comment in the CLI",
        )
        pause()
        run_ncs_cli(["show configuration commit list | nomore"])

        print_step(PURPLE, "Verify the committed value over JSON-RPC")
        read_th = new_trans(mode="read")
        active_txs.add(read_th)
        value = jsonrpc_call(
            "get_value",
            {"th": read_th, "path": SEARCH_PATH},
        )
        print_json(value)
        delete_trans_if_present(read_th)
        active_txs.discard(read_th)

    finally:
        for th in list(active_txs):
            try:
                delete_trans_if_present(th)
            except Exception:
                pass
        if logged_in:
            try:
                logout_if_needed()
            except Exception:
                pass
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
        print(f"{RED}JSON-RPC request failed: {exc}{NC}", file=sys.stderr)
        raise
