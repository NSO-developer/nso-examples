#!/usr/bin/env python3

"""NSO device YANG model upgrade example.

Demo script

See the README file for more information
"""
import subprocess
import json
import requests

AUTH = ('admin', 'admin')            # tuple of username, password
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

print(f"\n{OKGREEN}##### Reset and setup the example\n{ENDC}")
subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
               encoding='utf-8')

print(f"{OKBLUE}##### Create and initialize a VPN link service\n{ENDC}")
PATH = '/operations/tailf-ncs:devices/sync-from'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)
EP_DATA = {}
EP_DATA["a-device"] = "ex0"
EP_DATA["a-interface"] = "eth0"
EP_DATA["b-device"] = "ex1"
EP_DATA["b-interface"] = "eth0"
LINK_DATA = {}
LINK_DATA["name"] = "t2"
LINK_DATA["unit"] = "17"
LINK_DATA["vlan-id"] = "1"
LINK_DATA["endpoints"] = [EP_DATA]
INPUT_DATA = {"link:link": [LINK_DATA]}

PATH = '/data?dry-run=cli'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.json()["dry-run-result"]["cli"]["local-node"]["data"])

PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/operations/link:link=t2/get-modifications'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.json()["link:output"]["cli"]["local-node"]["data"])

print(f"{OKBLUE}##### Stop NSO and netsim devices. Backup both NSO and the "
      f"netsim devices before upgrading\n##### Since we are using a local "
      f"NSO install, we backup the runtime directory for potential disaster "
      f"recovery{ENDC}")
subprocess.run(['make', 'stop', 'backup'], check=True,
               encoding='utf-8')

print(f"{OKBLUE}##### Upgrade ex0 using revision-merge. Non-backward "
      f"compatible changes are not allowed\n{ENDC}")
subprocess.run(['make', 'revmerge', 'start'], check=True,
               encoding='utf-8')

PATH = '/data/tailf-ncs:packages'
print(f"\n{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

INPUT_DATA = {"input": [{"dry-run": [{"outformat": "cli"}]}]}
PATH = '/operations/link:link=t2/re-deploy'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.json()["link:output"]["cli"]["local-node"]["data"])

PATH = '/operations/link:link=t2/re-deploy'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/operations/tailf-ncs:devices/device=ex0/sync-to'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.json()["tailf-ncs:output"]["cli"])

PATH = '/operations/tailf-ncs:devices/device=ex0/sync-to'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/link:link'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices/device=ex0/config/router:sys/interfaces'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/interfaces'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f"{BOLD}ncs-netsim netconf-console ex0 /sys/interfaces\n{ENDC}")
subprocess.run(['ncs-netsim', 'netconf-console', 'ex0', '/sys/interfaces'],
               check=True, encoding='utf-8')

print(f"\n{OKBLUE}##### Revision merge will silently drop data that is not"
      " supported by the device\n##### with an older revision of the YANG"
      f" mode, unless we use the no-revision-drop parameter\n{ENDC}")

IF_DATA = {}
IF_DATA["name"] = "eth2"
IF_DATA["type"] = "router:ethernetCsmacd"
IFS_DATA = {}
IFS_DATA["interface"] = [IF_DATA]
INPUT_DATA = {"interfaces": IFS_DATA}

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/' + \
        'interfaces?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/' + \
        'interfaces?no-revision-drop'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/' + \
        'interfaces'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/interfaces'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f"{BOLD}ncs-netsim netconf-console ex0 /sys/interfaces\n{ENDC}")
subprocess.run(['ncs-netsim', 'netconf-console', 'ex0', '/sys/interfaces'],
               check=True, encoding='utf-8')

print(f"{OKBLUE}##### Upgrade ex0 using CDM.\n##### I.e. split the NED into a "
      f"version 1.0 for ex1\n"
      f"##### and migrate to a 1.1 NED for ex0 to allow non-backward "
      f"compatible YANG model changes\n{ENDC}")
subprocess.run(['make', 'stop', 'cdm', 'start'], check=True, encoding='utf-8')

PATH = '/data/tailf-ncs:packages'
print(f"\n{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

MIGRATE_DATA = {}
MIGRATE_DATA["new-ned-id"] = "router-nc-1.1"
MIGRATE_DATA["dry-run"] = ""
MIGRATE_DATA["verbose"] = ""
INPUT_DATA = {"tailf-ncs:input": [MIGRATE_DATA]}

PATH = '/operations/tailf-ncs:devices/device=ex0/migrate'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

MIGRATE_DATA = {}
MIGRATE_DATA["new-ned-id"] = "router-nc-1.1"
MIGRATE_DATA["verbose"] = ""
INPUT_DATA = {"tailf-ncs:input": [MIGRATE_DATA]}

PATH = '/operations/tailf-ncs:devices/device=ex0/migrate'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

INPUT_DATA = {"input": [{"dry-run": [{"outformat": "cli"}]}]}
PATH = '/operations/link:link=t2/re-deploy'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.json()["link:output"]["cli"]["local-node"]["data"])

PATH = '/operations/link:link=t2/re-deploy'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/link:link'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices/device=ex0/config/router:sys/interfaces'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/interfaces'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f"{BOLD}ncs-netsim netconf-console ex0 /sys/interfaces\n{ENDC}")
subprocess.run(['ncs-netsim', 'netconf-console', 'ex0', '/sys/interfaces'],
               check=True, encoding='utf-8')

print(f"{OKGREEN}##### Done!{ENDC}")
