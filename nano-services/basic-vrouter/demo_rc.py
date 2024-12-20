#!/usr/bin/env python3

"""NSO nano service example.

Demo script

See the README file for more information
"""
import subprocess
from datetime import datetime, timezone
import json
import requests
import ncs

AUTH = ('admin', 'admin')
BASE_URL = 'http://localhost:8080/restconf'
HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'


def state_reached(state, name, notifications):
    """Check if a state has been reached"""
    for notif_str in notifications:
        if len(notif_str):
            notif = json.loads(notif_str)
            if (
               notif["ietf-restconf:notification"]
               ["tailf-ncs:plan-state-change"]
               ["status"] == "reached" and
               "/vrouter:vrouter[name=\'{}\']".format(name) in
               notif["ietf-restconf:notification"]
               ["tailf-ncs:plan-state-change"]
               ["service"] and state in
               notif["ietf-restconf:notification"]
               ["tailf-ncs:plan-state-change"]
               ["state"]):
                return True
    return False


session = requests.Session()
session.auth = AUTH
headers = {'Content-Type': 'application/yang-data+json'}

print(f"\n{OKGREEN}##### Reset and setup the example\n{ENDC}")
subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
               encoding='utf-8')

print(f"{OKBLUE}##### Create and initialize a vrouter instance\n{ENDC}")
INPUT_DATA = {"vrouter": [{"name": "vr-01"}]}
PATH = '/data/vrouter:vrouter?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()

PATH = '/data/vrouter:vrouter'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

headers_stream = {'Content-Type': 'text/event-stream'}
PATH = '/streams/service-state-changes/json?start-time=' + dt_string
print(f"\n{HEADER}#### Waiting for a plan notification for the nano service"
      f" to reach the vr:vm-requested state...{ENDC}")
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(notifs_str)
        notifs = notifs_str.split("\n\n")
        if state_reached("vrouter:vm-requested", "vr-01", notifs):
            break

PATH = '/data/vrouter:vrouter=vr-01/plan'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/operations/vrouter:vrouter=vr-01/get-modifications'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.json()["vrouter:output"]["cli"]["local-node"]["data"])

dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()

print(f"{OKBLUE}##### Set the vm-up-and-running leaf to \"true\"\n{ENDC}")
with ncs.maapi.single_write_trans('admin', 'python', db=ncs.OPERATIONAL) as t:
    root = ncs.maagic.get_root(t)
    root.vrouter['vr-01'].vm_up_and_running = 'true'
    t.apply()

headers_stream = {'Content-Type': 'text/event-stream'}
PATH = '/streams/service-state-changes/json?start-time=' + dt_string
print(f"{HEADER}#### Waiting for a plan notification for the nano service"
      f" to reach the ncs:ready state...{ENDC}")
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifs = notifs_str.split("\n\n")
        if state_reached("ncs:ready", "vr-01", notifs):
            break

PATH = '/data/vrouter:vrouter=vr-01/vm-up-and-running'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/vrouter:vrouter=vr-01/plan'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/operations/vrouter:vrouter=vr-01/get-modifications'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.json()["vrouter:output"]["cli"]["local-node"]["data"])

PATH = '/data/vrouter:vrouter?content=config'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/vrouter:vm-instance'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f"{OKGREEN}##### Done!{ENDC}")
