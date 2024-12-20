#!/usr/bin/env python3

"""NSO RESTCONF notification example.
Demo script

See the README file for more information
"""

import subprocess
import os
from datetime import datetime, timezone
import json
import requests
import ncs
import _ncs
from ncs.maapi import Maapi

AUTH = ('admin', 'admin')
BASE_URL = 'http://localhost:8080/restconf'
HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'

session = requests.Session()
session.auth = AUTH
headers = {'Content-Type': 'application/yang-data+json'}
headers_patch = {'Content-Type': 'application/yang-patch+json'}
headers_stream = {'Content-Type': 'text/event-stream'}

print(f'\n{OKGREEN}##### Reset and setup the example\n{ENDC}')
subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
               encoding='utf-8')

print(f'\n{OKGREEN}##### Send a "dummy-notif" notification over the'
      ' "dummy-notifications" stream (see ncs.conf) using the NSO Data'
      ' Provider API and receive it from the NSO RESTCONF northbound'
      f' interface{ENDC}')
with Maapi():  # Load schemas
    pass

print(f'\n{HEADER}##### Send a "dummy-notif" notification using the NSO DP API'
      f'{ENDC}')
d = ncs.dp.Daemon("dummy-notifier")
nctx = _ncs.dp.register_notification_stream(d.ctx(), None,
                                            ncs.dp.take_worker_socket(
                                                            d,
                                                            'dummynotif',
                                                            'dummynotif-key'),
                                            'dummy-notifications')
d.start()
now = datetime.now()
tmnow = ncs.DateTime(year=now.year,
                     month=now.month,
                     day=now.day,
                     hour=now.hour,
                     min=now.minute,
                     sec=now.second,
                     micro=now.microsecond,
                     timezone=0,
                     timezone_minutes=0)
cs = _ncs.cs_node_cd(None, "/dummy-notif")
ns = cs.ns()
tag = cs.tag()
scs = _ncs.cs_node_cd(None, "/dummy-notif/dummy-value")
sns = scs.ns()
stag = scs.tag()
tvs = [
        ncs.TagValue(xmltag=ncs.XmlTag(ns, tag),
                     v=ncs.Value((tag, ns), ncs.C_XMLBEGIN)),
        ncs.TagValue(xmltag=ncs.XmlTag(sns, stag),
                     v=ncs.Value(f"hello world {now}", ncs.C_BUF)),
        ncs.TagValue(xmltag=ncs.XmlTag(ns, tag),
                     v=ncs.Value((tag, ns), ncs.C_XMLEND))
    ]
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
_ncs.dp.notification_send(nctx, tmnow, tvs)
d.finish()

PATH = '/streams/dummy-notifications/json?start-time=' + dt_string
print(f'\n{HEADER}##### Waiting for the dummy notification {ENDC}')
print(f'{BOLD}GET {BASE_URL} {PATH}{ENDC}')
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifs = notifs_str.split("\n\n")
        break

print(f'\n{OKGREEN}##### Sync with the device and receive the NETCONF base'
      f' notification the config changes generates over the built-in'
      f' NETCONF stream when the device config is added to NSO CDB{ENDC}')
print(f'\n{HEADER}##### Sync with the c0 device{ENDC}')
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
PATH = '/operations/tailf-ncs:devices/sync-from'
print(f'{BOLD}POST {BASE_URL} {PATH}{ENDC}')
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/streams/NETCONF/json?start-time=' + dt_string
print(f'\n{HEADER}##### Waiting for the NETCONF stream notification {ENDC}')
print(f'{BOLD}GET {BASE_URL} {PATH}{ENDC}')
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifs = notifs_str.split("\n\n")
        break

print(f'\n{OKGREEN}##### Receive notifications for the'
      f' "hardware state" stream from the "c0" device that implements'
      f' the ietf-hardware.yang model\n{ENDC}')
print(f'\n{HEADER}##### List the streams the device implements{ENDC}')
PATH = '/data/tailf-ncs:devices/device=c0/notifications/stream'
print(f'{BOLD}GET {BASE_URL} {PATH}{ENDC}')
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f'\n{HEADER}##### Subscribe to hardware_state stream notifications'
      f' sent from the device to NSO{ENDC}')
SUB_DATA = {}
SUB_DATA["name"] = "hw-state-changes"
SUB_DATA["stream"] = "hardware_state"
SUB_DATA["local-user"] = "admin"
INPUT_DATA = {}
INPUT_DATA["tailf-ncs:notifications"] = {"subscription": [SUB_DATA]}
PATH = '/data/tailf-ncs:devices/device=c0/notifications'
print(f'{BOLD}PATCH {BASE_URL} {PATH}{ENDC}')
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)
print(f"Status code: {r.status_code}\n")

print(f'\n{HEADER}##### Insert a new card into the device chassis subrack slot'
      f' to trigger a hardware_state stream notification from the device to'
      f' NSO{ENDC}')
wd = os.path.dirname(os.path.realpath(__file__)) + "/netsim/c/c0"
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
proc = subprocess.Popen(['python3',
                         'card.py',
                         '-p', '5010',
                         '-c', 'clone',
                         '-a', '87',
                         '-n', '9985',
                         '-m', 'tail-f',
                         '-r', '1',
                         '-u', '4',
                         '-l', '2',
                         '-o', '2'],
                        cwd=wd)

PATH = '/streams/device-notifications/json?start-time=' + dt_string
print(f'\n{HEADER}##### Waiting for NSO to forward the device hardware_state'
      f' stream notification to the NSO device-notifications stream'
      f'{ENDC}')
print(f'{BOLD}GET {BASE_URL} {PATH}{ENDC}')
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifs = notifs_str.split("\n\n")
        break

print(f'\n{OKGREEN}##### Configure the new card and receive the resulting'
      f' notifications on the NETCONF and device-notifications streams'
      f'\n{ENDC}')
COMP_DATA = {}
COMP_DATA["name"] = "hydrogen"
COMP_DATA["class"] = "iana-hardware:module"
COMP_DATA["parent"] = "slot-1-4-2"
COMP_DATA["parent-rel-pos"] = "1040200"
COMP_DATA["alias"] = "clone"
COMP_DATA["asset-id"] = "clone"
COMP_DATA["uri"] = "urn:clone"
INPUT_DATA = {}
INPUT_DATA["component"] = [COMP_DATA]
PATH = \
    '/data/tailf-ncs:devices/device=c0/config/ietf-hardware:hardware/component'
print(f'{BOLD}PATCH {BASE_URL} {PATH}{ENDC}')
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)
print(f"Status code: {r.status_code}\n")

PATH = '/streams/NETCONF/json?start-time=' + dt_string
print(f'\n{HEADER}##### Waiting for the NETCONF stream notification {ENDC}')
print(f'{BOLD}GET {BASE_URL} {PATH}{ENDC}')
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifs = notifs_str.split("\n\n")
        break

print(f'\n{HEADER}##### Waiting for NSO to forward the device hardware_state'
      f' stream notification to the NSO device-notifications stream'
      f'{ENDC}')
PATH = '/streams/device-notifications/json?start-time=' + dt_string
print(f'{BOLD}GET {BASE_URL} {PATH}{ENDC}')
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifs = notifs_str.split("\n\n")
        break

print(f'{OKGREEN}##### See logs/trace_*/trace.* for RESTCONF details'
      f'{ENDC}')
print(f'\n{OKGREEN}##### Done!\n{ENDC}')
