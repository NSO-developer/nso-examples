#!/usr/bin/env python3

"""NSO stacked nano service performance example.

RESTCONF demo script

See the README file for more information
"""

import argparse
import subprocess
import time
from datetime import datetime, timezone
import json
import requests

# Text color codes
HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'

# RESTCONF setup
AUTH = ('admin', 'admin')
BASE_URL = 'http://localhost:8080/restconf'
headers = {'Content-Type': 'application/yang-data+json'}
headers_patch = {'Content-Type': 'application/yang-patch+json'}
headers_stream = {'Content-Type': 'text/event-stream'}


def setup_tracing(session, runid):
    """Use the run ID to name the CSV file used by the NSO progress trace.
    Tip: Can be imported to your favourite spreadsheet application to get a
    detailed overview of the NSO transaction steps and timings after the test
    has finished.
    """
    fname = f't3-{runid}.csv'

    # Enable the NSO progress trace
    D_DATA = {}
    D_DATA["name"] = "t3-trace"
    D_DATA["destination"] = {"file": fname, "format": "csv"}
    D_DATA["enabled"] = True
    T_DATA = {}
    T_DATA["trace"] = [D_DATA]
    INPUT_DATA = {"tailf-progress:progress": T_DATA}

    PATH = '/data?unhide=debug'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")


def disable_tracing(session):
    """Disable the NSO progress trace"""
    D_DATA = {}
    D_DATA["name"] = "t3-trace"
    D_DATA["enabled"] = False
    T_DATA = {}
    T_DATA["trace"] = [D_DATA]
    INPUT_DATA = {"tailf-progress:progress": T_DATA}

    PATH = '/data?unhide=debug'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")


def main(args, runid):
    print(f'\n{OKBLUE}##### Reset and setup the example\n{ENDC}')
    subprocess.run(['make', 'stop', 'clean', f'NDEVS={args.ndevs}', 'all',
                    'start'], check=True, encoding='utf-8')

    session = requests.Session()
    session.auth = AUTH

    EDIT_LIST = []
    sub_data = {}
    sub_data["name"] = "t3-nano"
    sub_data["service-type"] = "/t3:t3s/t3:t3"
    sub_data["component-type"] = "self"
    sub_data["operation"] = "modified"
    edit_data1 = {}
    edit_data1["edit-id"] = "edit0"
    edit_data1["operation"] = "create"
    edit_data1["target"] = "/tailf-ncs:services/plan-notifications" +\
                           "/subscription=t3-nano"
    edit_data1["value"] = {"tailf-ncs:subscription": [sub_data]}
    cqsub_data = {}
    cqsub_data["name"] = "t3-nano-cq"
    cqsub_data["service-type"] = "/t3:t3s/t3:t3"
    edit_data2 = {}
    edit_data2["edit-id"] = "edit1"
    edit_data2["operation"] = "create"
    edit_data2["target"] = "/tailf-ncs:services/commit-queue-notifications" +\
                           "/subscription=t3-nano-cq"
    edit_data2["value"] = {"tailf-ncs:subscription": [cqsub_data]}
    EDIT_LIST.append(edit_data1)
    EDIT_LIST.append(edit_data2)
    PATCH_DATA = {}
    PATCH_DATA["patch-id"] = "plan-and-cq-notifs"
    PATCH_DATA["edit"] = EDIT_LIST
    INPUT_DATA = {"ietf-yang-patch:yang-patch": PATCH_DATA}

    PATH = '/data'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
    print(r.text)
    print(f'Status code: {r.status_code}\n')

    if args.nocq:
        print(f'\n{OKBLUE}###### No commit-queues configured\n{ENDC}')
    else:
        print(f'\n{OKBLUE}###### Configure the default commit-queue'
              f' settings\n{ENDC}')
        CQ_SETTINGS = {}
        CQ_SETTINGS["enabled-by-default"] = True
        CQ_SETTINGS["sync"] = ""
        CQ_DATA = {"commit-queue": [CQ_SETTINGS]}
        GS_DATA = {"global-settings": [CQ_DATA]}
        INPUT_DATA = {"tailf-ncs:devices": [GS_DATA]}
        PATH = '/data'
        print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
        print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
        r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
        print(r.text)
        print(f"Status code: {r.status_code}\n")

    print(f'\n{OKBLUE}##### Configure the device delay, i.e., simulated'
          f' device work and calibrate the CPU time\n{ENDC}')
    # Set the transaction delay on the devices. Controls how long netsim
    # devices will sleep in the device commit phase of a transaction updating
    # the device configuration.
    DD_DATA = {"dev-delay": args.dev_delay}
    T3S_DATA = {"dev-settings": [DD_DATA]}
    INPUT_DATA = {"cfs-t3:cfs-t3s": [T3S_DATA]}
    PATH = '/data'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")

    # Call an action to calibrate CPU time used to simulate work.
    PATH = '/data/t3:t3s/calibrate-cpu-time'
    print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
    r = session.post(BASE_URL + PATH, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")

    print(f'\n{OKBLUE}##### Enable the NSO progress trace\n{ENDC}')
    setup_tracing(session, runid)
    # For receiving only new notifications
    dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    start = time.time()  # For measuring wall-clock time

    print(f'\n{OKBLUE}##### Run a test with {args.ntrans} transactions to'
          f' {args.ndevs} devices with {args.ndtrans} transactions per'
          f' device\n{ENDC}')
    T3_DATA = {}
    T3_DATA["ntrans"] = args.ntrans
    T3_DATA["nwork"] = args.nwork
    T3_DATA["ndtrans"] = args.ndtrans
    T3_DATA["run-id"] = runid
    T3S_DATA = {"t3-settings": [T3_DATA]}
    INPUT_DATA = {"cfs-t3:cfs-t3s": [T3S_DATA]}
    PATH = '/data'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")

    n_cqtrans = 0
    if args.nocq:
        n_cqtrans = args.ndevs
        print(f"\n{HEADER}##### Waiting for plan notifications for all created"
              f" nano service components to have reached the ready state..."
              f"{ENDC}")
    else:
        print(f"\n{HEADER}##### Waiting for plan and commit queue"
              f" notifications for all created nano service components to have"
              f" reached the ready state and commit queue items to complete"
              f"...{ENDC}")
    PATH = '/streams/service-state-changes/json?start-time=' + dt_string
    print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
    with session.get(BASE_URL + PATH,
                     headers=headers_stream, stream=True) as r:
        NREADY = 0
        for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
            notifs_str = notifs_str.replace('data: ', '')
            print(f"{HEADER}" + notifs_str + f"{ENDC}")
            notifications = notifs_str.split("\n\n")
            for notif_str in notifications:
                if len(notif_str) and "tailf-ncs:plan-state-change" in \
                   notif_str:
                    notif = json.loads(notif_str)
                    state = notif["ietf-restconf:notification"][
                                  "tailf-ncs:plan-state-change"][
                                  "state"]
                    operation = notif["ietf-restconf:notification"][
                                      "tailf-ncs:plan-state-change"][
                                      "operation"]
                    status = notif["ietf-restconf:notification"][
                                   "tailf-ncs:plan-state-change"][
                                   "status"]
                    if ("tailf-ncs:ready" in state and operation == "modified"
                       and status == "reached"):
                        NREADY += 1
                if not args.nocq and len(notif_str) and \
                   "tailf-ncs:service-commit-queue-event" in notif_str:
                    notif = json.loads(notif_str)
                    status = notif["ietf-restconf:notification"][
                                   "tailf-ncs:service-commit-queue-event"][
                                   "status"]
                    if status == "completed":
                        n_cqtrans += 1
                        print("Commit queue item completed")
                    elif status == "failed":
                        n_cqtrans += 1
                        print(f"{HEADER}A transaction in a commit queue"
                              f" failed! {ENDC}", notif_str)
            if NREADY == args.ndevs and n_cqtrans == args.ndevs:
                break

    total = time.time() - start  # Total wall-clock time for the test

    print(f'\n{OKBLUE}##### Disable the NSO progress trace\n{ENDC}')
    disable_tracing(session)

    print(f'\n{OKBLUE}##### T3 RFS service log from nso-rundir/logs/'
          f'ncs-python-vm-t3.log:\n{ENDC}')
    with open("nso-rundir/logs/ncs-python-vm-t3.log", "r",
              encoding='utf-8') as f:
        lines = [s.strip() for s in f.readlines()]
        print("\n".join(lines))
    print(f'\n{OKBLUE}##### Total wall-clock time: {total} s\n{ENDC}')

    print(f'\n{OKBLUE}##### Show a graph representation of the progress'
          f' trace\n{ENDC}')
    if args.noninteractive:
        print(f'{OKBLUE}##### Skip - noninteractive\n{ENDC}')
    else:
        print(f"{OKGREEN}##### Press [ENTER] to continue or ctrl-c to exit\n"
              f"{ENDC}")
        input()
        r = subprocess.run(['python3', '-u',
                            '../../common/simple_progress_trace_viewer.py',
                            f'nso-rundir/logs/t3-{runid}.csv'], check=True,
                           encoding='utf-8')
        print(f'\n{OKBLUE}##### Note: The last transaction disables the'
              f' progress trace\n{ENDC}')

    print(f"{OKGREEN}##### Done!{ENDC}")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="A stacked nano service"
                                                 " performance showcase.")
    parser.add_argument('-d', '--ndevs', type=int, default=4,
                        help='Number of simulated devices. Default: 4')
    parser.add_argument('-t', '--ntrans', type=int, default=4,
                        help='Number of transactions. Default: 4')
    parser.add_argument('-w', '--nwork', type=int, default=3,
                        help='Simulated work in seconds. Default: 3')
    parser.add_argument('-r', '--ndtrans', type=int, default=1,
                        help='Devices per transaction. Default: 1')
    parser.add_argument('-q', '--nocq', action='store_true',
                        help='Do not use commit queues. Default: False')
    parser.add_argument('-y', '--dev_delay', type=int, default=1,
                        help='Simulated device work in seconds. Default: 1')
    parser.add_argument('-i', '--noninteractive', action='store_true',
                        help='Interactive. Default: False')
    args = parser.parse_args()
    runid = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    main(args, runid)
