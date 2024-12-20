#!/usr/bin/env python3

"""NSO nano service example.

RESTCONF demo script

See the README file for more information
"""
import subprocess
import os
import time
import sys
from datetime import datetime, timezone
import json
import requests

AUTH = ('admin', 'admin')
BASE_URL = 'http://localhost:8080/restconf'
HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'

NNES = int(sys.argv[1])

session = requests.Session()
session.auth = AUTH
headers = {'Content-Type': 'application/yang-data+json'}
headers_patch = {'Content-Type': 'application/yang-patch+json'}
headers_stream = {'Content-Type': 'text/event-stream'}

print(f'\n{OKGREEN}##### Reset and setup the example\n{ENDC}')
subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
               encoding='utf-8')

PATH = '/operations/tailf-ncs:devices/sync-from'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

print(f'\n{OKBLUE}##### Generate keys, distribute the public key and'
      f' configure NSO for public key authentication with {NNES} network'
      f' elements\n{ENDC}')
EDIT_LIST = []
for i in range(0, NNES):
    dk_data = {}
    dk_data["ne-name"] = f"ex{i}"
    dk_data["local-user"] = "admin"
    dk_data["remote-name"] = "admin"
    dk_data["authgroup-name"] = f"ex{i}-admin"
    dk_data["passphrase"] = "GThunberg18!"
    edit_data = {}
    edit_data["edit-id"] = f"edit{i}"
    edit_data["operation"] = "create"
    edit_data["target"] = f"/distkey:pubkey-dist/key-auth=ex{i},admin"
    edit_data["value"] = {"distkey:key-auth": [dk_data]}
    EDIT_LIST.append(edit_data)
PATCH_DATA = {}
PATCH_DATA["patch-id"] = "add-pubkey"
PATCH_DATA["edit"] = EDIT_LIST
INPUT_DATA = {"ietf-yang-patch:yang-patch": PATCH_DATA}

PATH = '/data?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
print(json.dumps(r.json(), indent=2))

dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()

PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
print(f'Status code: {r.status_code}\n')

print(f"\n{HEADER}##### Waiting for plan notifications for all created nano"
      f" service components to have reached the ready state...{ENDC}")
PATH = '/streams/service-state-changes/json?start-time=' + dt_string
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    NREADY = 0
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifications = notifs_str.split("\n\n")
        for notif_str in notifications:
            if len(notif_str):
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
                if "tailf-ncs:ready" in state and operation == "modified" and \
                   status == "reached":
                    NREADY += 1
        if NREADY == NNES:
            break

print(f'\n{OKBLUE}###### Show the plan status\n{ENDC}')
PATH = '/data/distkey:pubkey-dist?fields=key-auth/plan/' +\
       'component(type;name;state(name;status;post-action-status))'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f'\n{OKBLUE}###### Show the configuration added to NSO and network'
      f' elements\n{ENDC}')
PATH = '/data/tailf-ncs:devices/authgroups?fields=group(name;umap)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices?fields=device(authgroup)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices?fields=device/config/tailf-aaa:aaa/' +\
       'authentication/users/user(name;ssh-authkey:authkey)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f'\n{OKBLUE}###### The generated private and public keys\n{ENDC}')
for f_name in os.listdir('.'):
    if "ed25519" in f_name:
        print(f_name)

print(f'\n{OKBLUE}###### Delete the nano service (ctrl-c to abort) to go back'
      f' from public key to password base network element'
      f' authentication{ENDC}')
i = 5
while i > 0:
    try:
        print(f'{OKBLUE}{i}{ENDC}')
        time.sleep(1)
        i -= 1
    except KeyboardInterrupt:
        sys.exit()

EDIT_LIST = []
edit_data = {}
edit_data["edit-id"] = "edit1"
edit_data["operation"] = "delete"
edit_data["target"] = "/distkey:pubkey-dist"
EDIT_LIST.append(edit_data)
PATCH_DATA = {}
PATCH_DATA["patch-id"] = "delete-pubkey"
PATCH_DATA["edit"] = EDIT_LIST
INPUT_DATA = {"ietf-yang-patch:yang-patch": PATCH_DATA}

PATH = '/data?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
print(json.dumps(r.json(), indent=2))

dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()

PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers_patch)
print(f'Status code: {r.status_code}\n')

print(f"\n{HEADER}##### Waiting for plan notifications for all deleted nano"
      f" service components to have reached the init state...{ENDC}")
PATH = '/streams/service-state-changes/json?start-time=' + dt_string
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
with session.get(BASE_URL + PATH, headers=headers_stream, stream=True) as r:
    NREADY = 0
    for notifs_str in r.iter_content(chunk_size=None, decode_unicode=True):
        notifs_str = notifs_str.replace('data: ', '')
        print(f"{HEADER}" + notifs_str + f"{ENDC}")
        notifications = notifs_str.split("\n\n")
        for notif_str in notifications:
            if len(notif_str):
                notif = json.loads(notif_str)
                state = notif["ietf-restconf:notification"][
                              "tailf-ncs:plan-state-change"][
                              "state"]
                operation = notif["ietf-restconf:notification"][
                                  "tailf-ncs:plan-state-change"][
                                  "operation"]
                if ("tailf-ncs:init" in state and operation == "deleted"):
                    NREADY += 1
        if NREADY == NNES:
            break

print(f'\n{OKBLUE}###### Show the restored configuration for password'
      f' authentication\n{ENDC}')
PATH = '/data/tailf-ncs:devices/authgroups?fields=group(name;umap)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices?fields=device(authgroup)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices?fields=device/config/tailf-aaa:aaa/' +\
       'authentication/users/user(name;ssh-authkey:authkey)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f"{OKGREEN}##### Done!{ENDC}")
