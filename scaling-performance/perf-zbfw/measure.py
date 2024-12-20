"""NSO transaction performance example.

Demo script

See the README file for more information
"""

import argparse
from datetime import datetime, timezone
from functools import partial
import ipaddress
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
headers_xml = {'Content-Type': 'application/yang-data+xml'}
headers_stream = {'Content-Type': 'text/event-stream'}


def setup_tracing():
    """Get a unique run ID to name the CSV file used by the NSO progress trace.
    Tip: Can be imported to your favourite spreadsheet application to get a
    detailed overview of the NSO transaction steps and timings after the test
    has finished.
    """
    run_id = secrets.token_urlsafe(4)
    fname = f'zbfw-{run_id}.csv'
    fpath = f'logs/{fname}'

    # Enable the NSO progress trace
    D_DATA = {}
    D_DATA["name"] = "zbfw-trace"
    D_DATA["destination"] = {"file": fname, "format": "csv"}
    D_DATA["enabled"] = True
    D_DATA["filter"] = {"context": "rest"}
    T_DATA = {}
    T_DATA["trace"] = [D_DATA]
    INPUT_DATA = {"tailf-progress:progress": T_DATA}

    PATH = '/data?unhide=debug'
    print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
    print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
    r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
    print(r.text)
    print(f"Status code: {r.status_code}\n")

    return fpath


def disable_tracing():
    """Disable the NSO progress trace"""
    D_DATA = {}
    D_DATA["name"] = "zbfw-trace"
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


def gen_template(nzones, tid):
    """Generate a zbfw template"""
    ipstr = ipaddress.IPv4Address(u'1.0.0.1')
    ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
    ipint += tid * 9 * nzones
    zstr = zpstr = zbpstr = ztnistr = lstr = ''
    start = tid * nzones
    for i in range(nzones):
        zstr += '''
        <zone>
          <name>third-party{0}</name>
          <vpn>
            <id>{0}</id>
          </vpn>
        </zone>'''.format(start+i)

    for i in range(nzones):
        zpstr += '''
        <zone-pair>
          <name>third-party-internet{0}</name>
          <source-zone>third-party{0}</source-zone>
          <destination-zone>internet</destination-zone>
          <zone-policy>ZBF-3RDPARTY-TO-INET{0}</zone-policy>
        </zone-pair>'''.format(start + i)

    tipint = ipint + 9 * 256 ** 3
    for i in range(nzones):
        zbpstr += '''
        <zone-based-policy>
          <name>ZBF-3RDPARTY-TO-INET{0}</name>
          <sequence>
            <seq-value>1</seq-value>
            <seq-name>zone-based{0}</seq-name>
            <match>
              <source-ip>{1}/32</source-ip>
 <destination-data-prefix-list>INT-DEST-IP-TCP{0}</destination-data-prefix-list>
              <destination-port>443</destination-port>
              <destination-port>80</destination-port>
              <protocol>6</protocol>
            </match>
            <action>
              <action-value>inspect</action-value>
            </action>
          </sequence>
          <sequence>
            <seq-value>2</seq-value>
            <seq-name>zone-based{0}</seq-name>
            <match>
              <source-ip>{2}/32</source-ip>
 <destination-data-prefix-list>INT-DEST-IP-TCP{0}</destination-data-prefix-list>
              <protocol-name>bgp</protocol-name>
              <protocol-name>aol</protocol-name>
            </match>
            <action>
              <action-value>inspect</action-value>
            </action>
          </sequence>
          <sequence>
            <seq-value>11</seq-value>
            <seq-name>zone-based{0}</seq-name>
            <match>
           <source-data-prefix-list>INT-DEST-IP-TCP{0}</source-data-prefix-list>
              <destination-ip>{3}/32</destination-ip>
              <source-port>17</source-port>
            </match>
            <action>
              <action-value>inspect</action-value>
            </action>
          </sequence>
          <default-action>drop</default-action>
        </zone-based-policy>'''.format(start+i, ipaddress.IPv4Address(tipint),
                                       ipaddress.IPv4Address(tipint+1),
                                       ipaddress.IPv4Address(tipint+2))
        tipint += 3
        ipint += 3

    ztnistr = '''
        <zone-to-nozone-internet>deny</zone-to-nozone-internet>'''

    for i in range(nzones):
        lstr += '''
        <lists>
          <data-prefix-list>
            <name>INT-DEST-IP-TCP{0}</name>
            <ip-prefix>
              <ip>{1}/32</ip>
              <ipaddress>{1}</ipaddress>
              <netmask>255.255.255.255</netmask>
            </ip-prefix>
            <ip-prefix>
              <ip>{2}/32</ip>
              <ipaddress>{2}</ipaddress>
              <netmask>255.255.255.255</netmask>
            </ip-prefix>
            <ip-prefix>
              <ip>{3}/32</ip>
              <ipaddress>{3}</ipaddress>
              <netmask>255.255.255.255</netmask>
            </ip-prefix>
            <ip-prefix>
              <ip>{4}/32</ip>
              <ipaddress>{4}</ipaddress>
              <netmask>255.255.255.255</netmask>
            </ip-prefix>
            <ip-prefix>
              <ip>{5}/32</ip>
              <ipaddress>{5}</ipaddress>
              <netmask>255.255.255.255</netmask>
            </ip-prefix>
            <ip-prefix>
              <ip>{6}/32</ip>
              <ipaddress>{6}</ipaddress>
              <netmask>255.255.255.255</netmask>
            </ip-prefix>
          </data-prefix-list>
        </lists>'''.format(start + i, ipaddress.IPv4Address(ipint),
                           ipaddress.IPv4Address(ipint+1),
                           ipaddress.IPv4Address(ipint+2),
                           ipaddress.IPv4Address(ipint+3),
                           ipaddress.IPv4Address(ipint+4),
                           ipaddress.IPv4Address(ipint+5))
        ipint += 6

    return zstr + zpstr + zbpstr + ztnistr + lstr


def gen_templates(nzones, tid, device):
    """Generate a zbwf template addressing a device"""
    izonestr = ''
    if tid < device:
        izonestr = '''
      <zone>
        <name>internet</name>
        <vpn>
          <id>65535</id>
        </vpn>
      </zone>'''
    str = '''
    <policy-template>
      <template-name>template{0}-ex{1}</template-name>{2}{3}
    </policy-template>'''.format(tid, device, izonestr, gen_template(nzones,
                                                                     tid))
    return str


def gen_plain_patch(nzones, tid, device):
    """Generate a RESTCONF plain patch for zbfw service to device config"""
    str = '''<data>
  <policy-templates xmlns="http://com/example/zbfw">{}
  </policy-templates>
</data>'''.format(gen_templates(nzones, tid, device))
    return str


def test(cqparam, result_q, nzones, ndevs, tid):
    """Configure a service in a transaction using RESTCONF.
    Store the result in a Python multiprocessing queue.
    """
    lsession = requests.Session()
    lsession.auth = AUTH
    device = tid % ndevs
    try:
        INPUT_DATA = gen_plain_patch(nzones, tid, device)
        PATH = f'/data{cqparam}'
        print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC} tid: {tid} device:"
              f" {device}")
        # print(f"{HEADER}" + INPUT_DATA + f"{ENDC}")
        r = None
        trycnt = 5  # max try count
        while trycnt > 0:
            try:
                r = lsession.patch(BASE_URL + PATH, data=INPUT_DATA,
                                   headers=headers_xml)
                trycnt = 0  # success
            except ConnectionError as e:
                if trycnt <= 0:
                    result_q.put(e)  # done retrying
                else:
                    print(f'Retry tid: {tid} device: {device} trycnt: '
                          f'{trycnt} exception: {e}')
                    trycnt -= 1  # retry
                    time.sleep(tid/50)  # wait then retry
        print(r.text)
        print(f"Status code: {r.status_code}\n")

        if r.status_code >= 200 and r.status_code < 300:
            result_q.put(True)
        else:
            result_q.put(r.status_code)
    except Exception as e:
        result_q.put(e)


def test0(cqparam, result_q, nzones, ndevs):
    """Configure all devices for a service in a single transaction using
       RESTCONF."""
    str = ""
    for i in range(ndevs):
        str += '''
    <policy-template>
      <template-name>template-ex{0}</template-name>
      <zone>
        <name>internet</name>
        <vpn>
          <id>65535</id>
        </vpn>
      </zone>{1}
    </policy-template>'''.format(i, gen_template(nzones, 0))
    INPUT_DATA = '''<data>
  <policy-templates xmlns="http://com/example/zbfw">{}
  </policy-templates>
</data>'''.format(str)
    try:
        PATH = f'/data{cqparam}'
        print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
        # print(f"{HEADER}" + INPUT_DATA + f"{ENDC}")
        r = session.patch(BASE_URL + PATH, data=INPUT_DATA,
                          headers=headers_xml)
        print(r.text)
        print(f"Status code: {r.status_code}\n")

        if r.status_code >= 200 and r.status_code < 300:
            result_q.put(True)
        else:
            result_q.put(r.status_code)
    except Exception as e:
        result_q.put(e)


def run_test(nacq, cqparam, nzones, ntrans, ndevs):
    """Run the test and print the results"""
    # Initialize the NSO progress trace
    setup_tracing()

    m = Manager()
    result_q = m.Queue()
    tot_rtrans = 0  # Total number of device transactions
    tot_rtrans = 0  # Total number of RESTCONF transactions
    # For receiving only new notifications
    dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    start = time.time()  # For measuring wall-clock time
    if ntrans > 0:
        tot_rtrans = ndevs * ntrans
        tot_dtrans = tot_rtrans  # One RESTCONF service transaction per device
        # transaction
        # Use a managed Python pool to dispatch transactions with work to
        # separate processes. The number of transactions/processes running in
        # parallel will max be the number of CPU cores to best utilize the
        # processor.
        with Pool(tot_rtrans) as p:
            p.map(partial(test, cqparam, result_q, nzones, ndevs),
                  range(tot_rtrans))
            p.close()
            p.join()
    else:
        # Use just one RESTCONF request to configure all devices
        test0(cqparam, result_q, nzones, ndevs)
        tot_dtrans = ndevs  # One device transaction per device
        tot_rtrans = 1  # One single RESTCONF transaction
        ntrans = 1  # Resulting transactions per device will be 1 (not 0)

    # Get the results from the processes
    ok_requests = 0
    while result_q.qsize():
        result = result_q.get()
        if result is True:
            ok_requests += 1
        else:
            print(f"{HEADER}A RESTCONF request failed! {ENDC}", result,
                  result_q.qsize())

    # All RESTCONF requests sent.
    ok_acq = 0
    if nacq > 0:
        # Wait for the commit queue completed event notifications as we are
        # using asynchronous mode for the commit queue.
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
                            ok_acq += 1
                            n_cqtrans += 1
                            print("Commit queue item completed")
                            # print(notif_str)
                        elif status == "failed":
                            n_cqtrans += 1
                            print(f"{HEADER}A transaction in a commit queue"
                                  f" failed! {ENDC}", notif_str)
                if n_cqtrans in (nacq, ok_requests):
                    break

    total = time.time() - start  # Total wall-clock time for the test

    # Disable the NSO progress trace
    disable_tracing()

    # Present the results
    print(f'\n{UNDERLINE}Results for N={tot_rtrans} service'
          f' transactions:{ENDC}')
    print(f'{OKBLUE}Number of available CPU cores:        {ENDC}',
          os.cpu_count())
    print(f'{OKBLUE}Number of devices configured:         {ENDC}',
          ndevs)
    print(f'{OKBLUE}Number of zone pairs per transaction: {ENDC}',
          nzones)
    print(f'{OKBLUE}Number of zone pairs per device:      {ENDC}',
          nzones * ntrans)
    print(f'{OKBLUE}Total number of zone pairs:           {ENDC}',
          nzones * tot_dtrans)
    print(f'{OKBLUE}Total number of transactions:         {ENDC}',
          tot_rtrans)
    print(f'{OKBLUE}Total number of device transactions:  {ENDC}',
          tot_dtrans)
    if ok_requests == tot_rtrans:
        print(f'{OKGREEN}Successful requests:{ENDC}                   '
              f'100%')
    elif ok_requests < 1:
        print(f'{HEADER}Failed requests:{ENDC}                       '
              f'100%')
        return
    else:
        print(f'{BOLD}Failed requests:{ENDC}                       '
              f'{100-ok_requests/tot_rtrans:.0%}')
    if nacq > 0:
        if ok_acq == tot_dtrans:
            print(f'{OKGREEN}Successful async queued device trans:  '
                  f'{ENDC}100%')
        elif ok_acq < 1:
            print(f'{HEADER}Successful async queued device trans:   '
                  f'{ENDC}  0%')
            return
        else:
            print(f'{BOLD}Successful async queued device trans:   '
                  f'{ENDC}{ok_acq/tot_dtrans:.0%}')
    print(f'{OKBLUE}Wall-clock time:{ENDC}                       '
          f'{total:.2f}s\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument('-nt', '--ntrans', type=int,
                        default=5,
                        help='Number of transactions per device updating the'
                             ' service in parallel. "0" or less equal one'
                             ' single RESTCONF transaction for all devices.'
                             ' Default: 1')
    parser.add_argument('-nz', '--nzones', type=int, default=3,
                        help='Zone pairs per transaction. Default: 100')
    parser.add_argument('-cq', '--cqparam', choices=['async', 'sync', 'bypass',
                                                     'none'],
                        default='none',
                        help='Commit queue behavior. Select "none" to use'
                        ' global or device setting.'
                        ' Default: async')
    args = parser.parse_args()

    cqparam = f'?commit-queue={args.cqparam}'
    if 'none' in cqparam:
        cqparam = ''

    # Get the number of devices confgured with NSO
    PATH = "/data?fields=tailf-ncs:devices/device(name)"
    print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
    r = session.get(BASE_URL + PATH, headers=headers)
    ndevs = len(r.json()["ietf-restconf:data"]["tailf-ncs:devices"]["device"])

    # Get the number of devices with async commit queue enabled.
    # We will receive a "completed" notification when the config has been
    # pushed to those devices.
    nacq = 0
    if 'async' in cqparam:
        nacq = args.ntrans * ndevs
        print("All devices have asynchronous commit queues enabled")
    elif cqparam == '':
        PATH = '/data/tailf-ncs:devices/global-settings/commit-queue'
        print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
        r = session.get(BASE_URL + PATH, headers=headers)
        if 'enabled-by-default": true' in r.text and '"sync"' not in r.text:
            nacq = args.ntrans * ndevs
            print("All devices have asynchronous commit queues enabled")
        elif '"sync"' not in r.text:
            PATH = '/data/tailf-ncs:devices?fields=device/commit-queue' \
                   '(enabled-by-default)'
            print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
            r = session.get(BASE_URL + PATH, headers=headers)
            nacq = ndevs * r.text.count('true')
            print(f'{nacq} devices have asynchronoud commit queues enabled')
    else:
        print("No devices have asynchronous commit queues enabled")

    # Run the test
    run_test(nacq, cqparam, args.nzones, args.ntrans, ndevs)
