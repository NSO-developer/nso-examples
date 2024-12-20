"""NSO transaction performance example.

Demo script

See the README file for more information
"""

import argparse
from datetime import datetime, timezone
from functools import partial
import json
from multiprocessing import Pool
from multiprocessing import Manager
import os
import secrets
import time
import requests
from requests.exceptions import ConnectionError

# Text color codes
HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKCYAN = '\033[96m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'
UNDERLINE = '\033[4m'

# RESTCONF setup
AUTH = ('admin', 'admin')
BASE_URL = 'http://localhost:8080/restconf'
session = requests.Session()
session.auth = AUTH
headers = {'Content-Type': 'application/yang-data+json'}
headers_patch = {'Content-Type': 'application/yang-patch+json'}
headers_stream = {'Content-Type': 'text/event-stream'}


def setup_tracing():
    """Get a unique run ID to name the CSV file used by the NSO progress trace.
    Tip: Can be imported to your favourite spreadsheet application to get a
    detailed overview of the NSO transaction steps and timings after the test
    has finished.
    """
    run_id = secrets.token_urlsafe(4)
    fname = f't3-{run_id}.csv'
    fpath = f'logs/{fname}'

    # Enable the NSO progress trace
    D_DATA = {}
    D_DATA["name"] = "t3-trace"
    D_DATA["destination"] = {"file": fname, "format": "csv"}
    D_DATA["enabled"] = True
    # D_DATA["filter"] = {"context": "rest"}
    T_DATA = {}
    T_DATA["trace"] = [D_DATA]
    INPUT_DATA = {"tailf-progress:progress": T_DATA}

    PATH = '/data?unhide=debug'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")

    return run_id, fpath


def disable_tracing():
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


def test(cqparam, result_q, run_id, i):
    """Configure a service in a transaction using RESTCONF.
    Store the result in a Python multiprocessing queue.
    """
    lsession = requests.Session()
    lsession.auth = AUTH
    try:
        T3_DATA = {}
        T3_DATA["id"] = f"{i}"
        T3_DATA["value"] = f"{i}-{run_id}"
        T3S_DATA = {"t3": [T3_DATA]}
        INPUT_DATA = {"t3:t3s": [T3S_DATA]}

        PATH = f'/data{cqparam}'
        print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC} id: {i}"
              f" value: {i}-{run_id}")
        # print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
        r = None
        trycnt = 5  # max try count
        while trycnt > 0:
            try:
                r = lsession.patch(BASE_URL + PATH, json=INPUT_DATA,
                                   headers=headers)
                trycnt = 0  # success
            except ConnectionError as e:
                if trycnt <= 0:
                    result_q.put(e)  # done retrying
                else:
                    print(f'Retry id: {i} value: {i}-{run_id}'
                          f' trycnt: {trycnt} exception: {e}')
                    trycnt -= 1  # retry
                    time.sleep(i/50)  # wait then retry
        print(r.text)
        print(f"Status code: {r.status_code}\n")

        if r.status_code >= 200 and r.status_code < 300:
            result_q.put(True)
        else:
            result_q.put(r.status_code)
    except Exception as e:
        result_q.put(e)


def run_test(nacq, cqparam, ntrans, ndtrans):
    """Run the test and print the results"""
    # Initialize the NSO progress trace
    run_id, csv_file = setup_tracing()

    # Use a managed Python pool to send multiple RESTCONF requests in parallel.
    m = Manager()
    result_q = m.Queue()
    # For receiving only new notifications
    dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    start = time.time()  # For measuring wall-clock time
    with Pool(ntrans) as p:
        p.map(partial(test, cqparam, result_q, run_id), range(ntrans))
        p.close()
        p.join()

    # Get the result from the processes
    ok_requests = 0
    while result_q.qsize():
        result = result_q.get()
        if result is True:
            ok_requests += 1
        else:
            print(f"{HEADER}A RESTCONF request failed! {ENDC}", result,
                  result_q.qsize())

    # All RESTCONF requests sent.
    if nacq > 0:
        # Now waiting for the service commit queue
        # event notifications if we are using asynchronous commit to queues to
        # send the requests from processes as fast as possible when we have
        # more requests in-progress than CPU cores.
        n_cqtrans = 0
        PATH = '/streams/service-state-changes/json?start-time=' + dt_string
        print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
        with session.get(BASE_URL + PATH, headers=headers_stream,
                         stream=True) as r:
            for notifs_str in r.iter_content(chunk_size=None,
                                             decode_unicode=True):
                notifs_str = notifs_str.replace('data: ', '')
                notifs = notifs_str.split("\n\n")
                for notif_str in notifs:
                    if len(notif_str) and "tailf-ncs:service-commit-queue" \
                       + "-event" in notif_str:
                        notif = json.loads(notif_str)
                        status = notif["ietf-restconf:notification"][
                                       "tailf-ncs:service-commit-queue-event"][
                                       "status"]
                        if status == "completed":
                            n_cqtrans += 1
                            print("Commit queue item completed")
                            # print(notif_str)
                        elif status == "failed":
                            n_cqtrans += 1
                            print(f"{HEADER}A transaction in a commit queue"
                                  f" failed! {ENDC}", notif_str)
                if n_cqtrans == nacq or n_cqtrans == ok_requests:
                    break

    total = time.time() - start  # Total wall-clock time for the test

    # Disable the NSO progress trace
    disable_tracing()

    # Present the results
    print(f'\n{UNDERLINE}Results for N={ntrans} transactions:{ENDC}')
    print(f'{OKBLUE}Number of CPU cores available: {ENDC}',
          os.cpu_count())
    print(f'{OKBLUE}Number of transactions:        {ENDC}', ntrans)

    if ok_requests == ntrans:
        print(f'{OKGREEN}Successful requests:       {ENDC}     '
              f'100%')
    elif ok_requests < 1:
        print(f'{HEADER}Failed requests:           {ENDC}     '
              f'100%')
    else:
        print(f'{BOLD}Failed requests:           {ENDC}     '
              f'{100-ok_requests/ntrans:.0%}')

    print(f'{OKBLUE}Wall-clock time:       {ENDC}         '
          f'{total:.2f}s\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument('-nw', '--nwork', type=int, default="3",
                        help='Work per transaction in the service create and' +
                             ' validation phases. One second of CPU time' +
                             ' per work item. Default: 3')
    parser.add_argument('-nt', '--ntrans', type=int,
                        default=f"{os.cpu_count()}",
                        help='Number of transactions updating the same' +
                             ' service in parallel. Default: Number of CPU' +
                             ' cores')
    # Get the number of devices confgured with NSO to use as the upper limit
    PATH = "/data?fields=tailf-ncs:devices/device(name)"
    print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
    r = session.get(BASE_URL + PATH, headers=headers)
    ndevs = len(r.json()["ietf-restconf:data"]["tailf-ncs:devices"]["device"])
    parser.add_argument('-nd', '--ndtrans', choices=range(0, ndevs+1),
                        type=int, default=1,
                        help='Number of devices the service will configure'
                             ' per service transaction. Default: 1')
    parser.add_argument('-dd', '--ddelay', type=int, default=0,
                        help='Transaction delay on the devices (seconds).'
                             ' Default: 0s')
    parser.add_argument('-cq', '--cqparam', choices=['async', 'sync',
                                                     'bypass', 'none'],
                        default='none', help='Commit queue behavior. Select'
                        ' "none" to use global or device setting.'
                        ' setting. Default: none')
    args = parser.parse_args()

    cqparam = f'?commit-queue={args.cqparam}'
    if 'none' in cqparam:
        cqparam = ''

    # Get the number of devices with async commit queue enabled.
    # We will receive a "completed" notification when the config has been
    # pushed to those devices.
    nacq = 0
    if 'async' in cqparam:
        nacq = args.ntrans * args.ndtrans
        print("All devices have async commit queues enabled")
    elif cqparam == '':
        PATH = '/data/tailf-ncs:devices/global-settings/commit-queue'
        print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
        r = session.get(BASE_URL + PATH, headers=headers)
        if 'enabled-by-default": true' in r.text and '"sync"' not in r.text:
            nacq = args.ntrans * args.ndtrans
            print("All devices have async cqs enabled")
        elif '"sync"' not in r.text:
            PATH = '/data/tailf-ncs:devices?fields=device/commit-queue' \
                   '(enabled-by-default)'
            print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
            r = session.get(BASE_URL + PATH, headers=headers)
            nacq = args.ndtrans * r.text.count('true')
            print("Some devices have async cqs enabled")
    # else no devices have async cqs enabled

    # Set number of devices a transaction configures
    DEV_DATA = {"ndtrans": args.ndtrans}
    T3S_DATA = {"t3-settings": [DEV_DATA]}
    INPUT_DATA = {"t3:t3s": [T3S_DATA]}
    PATH = '/data'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")

    # Set the amount of work done per process.
    # One "work" item equals 1s of CPU time.
    NW_DATA = {"nwork": args.nwork}
    T3S_DATA = {"t3-settings": [NW_DATA]}
    INPUT_DATA = {"t3:t3s": [T3S_DATA]}
    PATH = '/data'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")

    # Set the transaction delay on the devices. Controls how long netsim
    # devices will sleep in the prepare phase of a transaction updating the
    # device configuration.
    DD_DATA = {"dev-delay": args.ddelay}
    T3S_DATA = {"dev-settings": [DD_DATA]}
    INPUT_DATA = {"t3:t3s": [T3S_DATA]}
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

    # Run the test
    run_test(nacq, cqparam, args.ntrans, args.ndtrans)
