#!/usr/bin/env python3

"""NSO stacked nano service performance example.

RESTCONF demo script

See the README file for more information
"""

import argparse
import subprocess
import time
import datetime
from datetime import datetime as DT
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

    # Enable the NSO progress trace on all cluster nodes
    prog_data = {}
    prog_data["name"] = "t3-trace"
    prog_data["destination"] = {"file": fname, "format": "csv"}
    prog_data["enabled"] = True
    prog_data["verbosity"] = "normal"
    edit_data3 = {}
    edit_data3["edit-id"] = "cfs-prog"
    edit_data3["operation"] = "create"
    edit_data3["target"] = "/tailf-progress:progress/trace=t3-trace"
    edit_data3["value"] = {"tailf-progress:trace": [prog_data]}
    edit_data2 = {}
    edit_data2["edit-id"] = "rfs2-prog"
    edit_data2["operation"] = "create"
    edit_data2["target"] = "/tailf-ncs:devices/device=lower-nso-2/config" +\
                           "/tailf-progress:progress/trace=t3-trace"
    edit_data2["value"] = {"tailf-progress:trace": [prog_data]}
    edit_data1 = {}
    edit_data1["edit-id"] = "rfs1-prog"
    edit_data1["operation"] = "create"
    edit_data1["target"] = "/tailf-ncs:devices/device=lower-nso-1/config" +\
                           "/tailf-progress:progress/trace=t3-trace"
    edit_data1["value"] = {"tailf-progress:trace": [prog_data]}
    EDIT_LIST = []
    EDIT_LIST.append(edit_data1)
    EDIT_LIST.append(edit_data2)
    EDIT_LIST.append(edit_data3)
    PATCH_DATA = {}
    PATCH_DATA["patch-id"] = "plan-notifs"
    PATCH_DATA["edit"] = EDIT_LIST
    INPUT_DATA = {"ietf-yang-patch:yang-patch": PATCH_DATA}
    PATH = '/data?unhide=debug'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
    print(r.text)
    print(f'Status code: {r.status_code}\n')


def disable_tracing(session):
    """Disable the NSO progress trace"""
    prog_data = {}
    prog_data["name"] = "t3-trace"
    prog_data["enabled"] = False
    edit_data3 = {}
    edit_data3["edit-id"] = "cfs-prog"
    edit_data3["operation"] = "merge"
    edit_data3["target"] = "/tailf-progress:progress/trace=t3-trace"
    edit_data3["value"] = {"tailf-progress:trace": [prog_data]}
    edit_data2 = {}
    edit_data2["edit-id"] = "rfs2-prog"
    edit_data2["operation"] = "merge"
    edit_data2["target"] = "/tailf-ncs:devices/device=lower-nso-2/config" +\
                           "/tailf-progress:progress/trace=t3-trace"
    edit_data2["value"] = {"tailf-progress:trace": [prog_data]}
    edit_data1 = {}
    edit_data1["edit-id"] = "rfs1-prog"
    edit_data1["operation"] = "merge"
    edit_data1["target"] = "/tailf-ncs:devices/device=lower-nso-1/config" +\
                           "/tailf-progress:progress/trace=t3-trace"
    edit_data1["value"] = {"tailf-progress:trace": [prog_data]}
    EDIT_LIST = []
    EDIT_LIST.append(edit_data1)
    EDIT_LIST.append(edit_data2)
    EDIT_LIST.append(edit_data3)
    PATCH_DATA = {}
    PATCH_DATA["patch-id"] = "plan-notifs"
    PATCH_DATA["edit"] = EDIT_LIST
    INPUT_DATA = {"ietf-yang-patch:yang-patch": PATCH_DATA}
    PATH = '/data?unhide=debug'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
    print(r.text)
    print(f'Status code: {r.status_code}\n')


def main(args, runid):
    print(f'\n{OKBLUE}##### Reset and setup the example\n{ENDC}')
    subprocess.run(['make', 'stop', 'clean', f'LDEVS={args.ldevs}', 'all',
                    'start'], check=True, encoding='utf-8')

    session = requests.Session()
    session.auth = AUTH

    nso2_data = {}
    nso2_data["out-of-sync-commit-behaviour"] = "accept"
    nso2_data["name"] = "lower-nso-2"
    nso1_data = {}
    nso1_data["out-of-sync-commit-behaviour"] = "accept"
    nso1_data["name"] = "lower-nso-1"
    device_data = {}
    device_data["device"] = [nso1_data, nso2_data]
    edit_data8 = {}
    edit_data8["edit-id"] = "rfs-nso-sync"
    edit_data8["operation"] = "merge"
    edit_data8["target"] = "/tailf-ncs:devices"
    edit_data8["value"] = {"tailf-ncs:devices": device_data}
    lower2_data = {}
    lower2_data["trace"] = "pretty"
    lower2_data["username"] = "admin"
    lower2_data["authgroup"] = "default"
    lower2_data["port"] = "2024"
    lower2_data["address"] = "127.0.0.1"
    lower2_data["name"] = "lower-nso-2"
    lower1_data = {}
    lower1_data["trace"] = "pretty"
    lower1_data["username"] = "admin"
    lower1_data["authgroup"] = "default"
    lower1_data["port"] = "2023"
    lower1_data["address"] = "127.0.0.1"
    lower1_data["name"] = "lower-nso-1"
    cluster_data = {}
    cluster_data["remote-node"] = [lower1_data, lower2_data]
    cluster_data["commit-queue"] = {"enabled": "true"}
    cluster_data["device-notifications"] = {"enabled": "true"}
    edit_data7 = {}
    edit_data7["edit-id"] = "rfs-nso-cluster"
    edit_data7["operation"] = "merge"
    edit_data7["target"] = "/tailf-ncs:cluster"
    edit_data7["value"] = {"tailf-ncs:cluster": cluster_data}
    sub_data2 = {}
    sub_data2["name"] = "t3-nano"
    sub_data2["stream"] = "service-state-changes"
    sub_data2["local-user"] = "admin"
    edit_data6 = {}
    edit_data6["edit-id"] = "rfs-nso2-sub2"
    edit_data6["operation"] = "create"
    edit_data6["target"] = "/tailf-ncs:devices/device=lower-nso-2" +\
                           "/notifications/subscription=t3-nano"
    edit_data6["value"] = {"tailf-ncs:subscription": [sub_data2]}
    edit_data5 = {}
    edit_data5["edit-id"] = "rfs-nso1-sub2"
    edit_data5["operation"] = "create"
    edit_data5["target"] = "/tailf-ncs:devices/device=lower-nso-1" +\
                           "/notifications/subscription=t3-nano"
    edit_data5["value"] = {"tailf-ncs:subscription": [sub_data2]}
    cqsub_data = {}
    cqsub_data["name"] = "t3-nano-cq"
    cqsub_data["service-type"] = "/t3:t3s/t3:t3"
    edit_data4 = {}
    edit_data4["edit-id"] = "rfs-nso2-cqsub"
    edit_data4["operation"] = "create"
    edit_data4["target"] = "/tailf-ncs:devices/device=lower-nso-2/config" +\
                           "/tailf-ncs:services/commit-queue-notifications" +\
                           "/subscription=t3-nano-cq"
    edit_data4["value"] = {"tailf-ncs:subscription": [cqsub_data]}
    edit_data3 = {}
    edit_data3["edit-id"] = "rfs-nso1-cqsub"
    edit_data3["operation"] = "create"
    edit_data3["target"] = "/tailf-ncs:devices/device=lower-nso-1/config" +\
                           "/tailf-ncs:services/commit-queue-notifications" +\
                           "/subscription=t3-nano-cq"
    edit_data3["value"] = {"tailf-ncs:subscription": [cqsub_data]}
    psub_data = {}
    psub_data["name"] = "t3-nano-plan"
    psub_data["service-type"] = "/t3:t3s/t3:t3"
    psub_data["component-type"] = "self"
    psub_data["operation"] = "modified"
    edit_data2 = {}
    edit_data2["edit-id"] = "rfs-nso2-psub"
    edit_data2["operation"] = "create"
    edit_data2["target"] = "/tailf-ncs:devices/device=lower-nso-2/config" +\
                           "/tailf-ncs:services/plan-notifications" +\
                           "/subscription=t3-nano-plan"
    edit_data2["value"] = {"tailf-ncs:subscription": [psub_data]}
    edit_data1 = {}
    edit_data1["edit-id"] = "rfs-nso1-psub"
    edit_data1["operation"] = "create"
    edit_data1["target"] = "/tailf-ncs:devices/device=lower-nso-1/config" +\
                           "/tailf-ncs:services/plan-notifications" +\
                           "/subscription=t3-nano-plan"
    edit_data1["value"] = {"tailf-ncs:subscription": [psub_data]}

    EDIT_LIST = []
    EDIT_LIST.append(edit_data1)
    EDIT_LIST.append(edit_data2)
    EDIT_LIST.append(edit_data3)
    EDIT_LIST.append(edit_data4)
    EDIT_LIST.append(edit_data5)
    EDIT_LIST.append(edit_data6)
    EDIT_LIST.append(edit_data7)
    EDIT_LIST.append(edit_data8)
    PATCH_DATA = {}
    PATCH_DATA["patch-id"] = "plan-notifs"
    PATCH_DATA["edit"] = EDIT_LIST
    INPUT_DATA = {"ietf-yang-patch:yang-patch": PATCH_DATA}
    PATH = '/data'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
    print(r.text)
    print(f'Status code: {r.status_code}\n')

    for lower_nso in ["lower-nso-1", "lower-nso-2"]:
        PATH = f'/data/tailf-ncs:cluster/remote-node={lower_nso}/ssh' +\
               '/fetch-host-keys'
        print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
        r = session.post(BASE_URL + PATH, headers=headers)
        print(r.text)
        print(f"Status code: {r.status_code}\n")

    if args.nocq:
        print(f'\n{OKBLUE}###### No lower node commit-queues\n{ENDC}')
    else:
        print(f'\n{OKBLUE}###### Configure the default lower node commit-queue'
              f' settings\n{ENDC}')
        for lower_nso in ["lower-nso-1", "lower-nso-2"]:
            CQ_SETTINGS = {}
            CQ_SETTINGS["enabled-by-default"] = True
            CQ_SETTINGS["sync"] = ""
            INPUT_DATA = {"tailf-ncs:commit-queue": [CQ_SETTINGS]}
            PATH = f'/data/tailf-ncs:devices/device={lower_nso}/config' +\
                   '/tailf-ncs:devices/global-settings/commit-queue'
            print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
            print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
            r = session.patch(BASE_URL + PATH, json=INPUT_DATA,
                              headers=headers)
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
    print(f"Status code: {r.status_code}\n")

    # Call an action on the lower nodes to calibrate CPU time used to simulate
    # work.
    for lower_nso in ["lower-nso-1", "lower-nso-2"]:
        PATH = f'/data/tailf-ncs:devices/device={lower_nso}/live-status' +\
               '/t3:t3s/calibrate-cpu-time'
        print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
        r = session.post(BASE_URL + PATH, headers=headers)
        print(r.text)
        print(f"Status code: {r.status_code}\n")

    print(f'\n{OKBLUE}##### Enable the NSO progress trace\n{ENDC}')
    setup_tracing(session, runid)

    start = time.time()  # For measuring wall-clock time

    print(f'\n{OKBLUE}##### Run a test with {args.ntrans} transactions per'
          f' lower NSO device with {args.ndtrans} transactions per'
          f' lower NSO device\n{ENDC}')
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

    tot_ntrans = args.ntrans * 2
    tot_ndevs = args.ldevs * 2
    n_cqtrans = 0
    if args.nocq:
        n_cqtrans = tot_ndevs
        print(f'{HEADER}##### Wait for {tot_ntrans} lower nodes nano service'
              f' plan to reach ready status\n{ENDC}')
    else:
        print(f'{HEADER}##### Wait for {tot_ntrans} lower nodes nano service'
              ' plan to reach ready status and commit queue items to'
              f' complete\n{ENDC}')
    while True:
        PATH = "/data/tailf-ncs:devices?fields=device/notifications" +\
               "/received-notifications/notification/data"
        # print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
        r = session.get(BASE_URL + PATH, headers=headers)
        nready = r.text.count('"state": "ready"')
        if not args.nocq:
            n_cqtrans = r.text.count('"status": "completed"')
        if nready == tot_ntrans and n_cqtrans == tot_ndevs:
            break
        time.sleep(0.1)
        # print(f"{HEADER}##### Wait for {tot_ntrans-nready} lower nodes" +\
        #       f" nano service plan to reach ready status\n{ENDC}")

    total = time.time() - start  # Total wall-clock time for the test

    print(f'\n{OKBLUE}##### Disable the NSO progress trace\n{ENDC}')
    disable_tracing(session)

    print(f'\n{OKBLUE}##### Total wall-clock time: {total} s\n{ENDC}')

    if args.noninteractive:
        print(f'{OKBLUE}##### Skip showing graph representations of the'
              f' progress traces - noninteractive\n{ENDC}')
    else:
        print(f'\n{OKBLUE}##### Show a graph representation of the upper-nso'
              f' progress trace\n{ENDC}')
        print(f"{OKGREEN}##### Press [ENTER] to continue or ctrl-c to exit"
              f"{ENDC}")
        input()
        r = subprocess.run(['python3',
                            '../../common/simple_progress_trace_viewer.py',
                            f'upper-nso/logs/t3-{runid}.csv'], check=True,
                           encoding='utf-8')
        print(f'\n{OKBLUE}##### Note: Shows transaction to lower nodes and'
              f' polling for the completed transactions. The last transaction'
              f' disables the progress trace\n{ENDC}')
        print(f'\n{OKBLUE}##### Show a graph representation of the lower-nso-1'
              f' progress trace\n{ENDC}')
        print(f"{OKGREEN}##### Press [ENTER] to continue or ctrl-c to exit"
              f"{ENDC}")
        input()
        r = subprocess.run(['python3',
                            '../../common/simple_progress_trace_viewer.py',
                            f'lower-nso-1/logs/t3-{runid}.csv'], check=True,
                           encoding='utf-8')
        print(f'\n{OKBLUE}##### Show a graph representation of the upper-nso'
              f' progress trace\n{ENDC}')
        print(f"{OKGREEN}##### Press [ENTER] to continue or ctrl-c to exit"
              f"{ENDC}")
        input()
        r = subprocess.run(['python3',
                            '../../common/simple_progress_trace_viewer.py',
                            f'lower-nso-2/logs/t3-{runid}.csv'], check=True,
                           encoding='utf-8')

    print(f"{OKGREEN}##### Done!{ENDC}")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="An LSA nano service"
                                                 " performance showcase.")
    parser.add_argument('-d', '--ldevs', type=int, default=2,
                        help='Number of simulated devices per lower node. '
                             'Default: 2')
    parser.add_argument('-t', '--ntrans', type=int, default=2,
                        help='Number of transactions per lower node. '
                             'Default: 2')
    parser.add_argument('-w', '--nwork', type=int, default=3,
                        help='Simulated work in seconds. '
                        'Default: 3')
    parser.add_argument('-r', '--ndtrans', type=int, default=1,
                        help='RFS devices per transaction. '
                        'Default: 1')
    parser.add_argument('-q', '--nocq', action='store_true',
                        help='No commit queues on the lower devices. '
                        'Default: False')
    parser.add_argument('-y', '--dev_delay', type=int, default=1,
                        help='Simulated device work in seconds. '
                        'Default: 1')
    parser.add_argument('-i', '--noninteractive', action='store_true',
                        help='Noninteractive. '
                        'Default: False')
    args = parser.parse_args()
    runid = DT.now(datetime.timezone.utc).isoformat()
    main(args, runid)
