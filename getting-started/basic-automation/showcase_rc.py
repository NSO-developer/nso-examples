#!/usr/bin/env python3

"""NSO Basic services example.

Showcase script

See the README file for more information
"""
import subprocess
import os
import sys
import itertools
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

session = requests.Session()
session.auth = AUTH
headers = {'Content-Type': 'application/yang-data+json'}

print(f"\n{OKGREEN}##### Setup the demo\n{ENDC}")

print(f"{OKBLUE}##### Make sure no nso or netsim processes are running\n"
      f"{ENDC}")
subprocess.run(['make', 'stop'], stdout=subprocess.PIPE,
               stderr=subprocess.PIPE, check=True, encoding='utf-8')

print(f"{OKBLUE}##### Create an NSO local install with a fresh runtime"
      f" directory\n{ENDC}")
subprocess.run(['make', 'clean', 'all'], check=True, encoding='utf-8')
print(f"{OKBLUE}##### Have the environment variable NSO_RUNDIR point"
      f" to the runtime directory\n{ENDC}")
os.environ['NSO_RUNDIR'] = "{}/nso-lab-rundir".format(os.getcwd())
os.chdir(os.getenv('NSO_RUNDIR'))

print(f"{OKGREEN}##### Showcase: Configuring DNS with Python"
      f"\n{ENDC}")

print(f"{OKBLUE}##### Step 1: Start the routers{ENDC}")
subprocess.run(['make', 'stop', 'clean', 'all'],  check=True, encoding='utf-8')
subprocess.run(['ncs-netsim', '-a', 'start'],  check=True, encoding='utf-8')
subprocess.run(['ncs'],  check=True, encoding='utf-8')

print(f"{OKBLUE}##### Step 2: Inspect the device data model"
      f"{ENDC}")
PATH = '/data?fields=tailf-ncs:devices/device(name;address)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/operations/tailf-ncs:devices/sync-from'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

SERVER_DATA = {"server": [{"address": "198.51.100.1"}]}
SYS_DATA = {"router:sys": {"dns": SERVER_DATA}}
DEV_DATA = {"device": [{"name": "ex1", "config": SYS_DATA}]}
INPUT_DATA = {"data": {"tailf-ncs:devices": DEV_DATA}}
PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/data/tailf-rollback:rollback-files'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

INPUT_DATA = {"input": {"id": "0"}}
PATH = '/data/tailf-rollback:rollback-files/apply-rollback-file'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

with open("packages/router-nc-1.1/src/yang/router-dns.yang", "r",
          encoding='utf-8') as f:
    for line in f:
        if 'list server' in line:
            sys.stdout.write(line)
            sys.stdout.writelines(itertools.islice(f, 7))
            break

print(f"\n{OKBLUE}##### Step 3: Create the script{ENDC}")
print(f"{OKBLUE}##### Step 4: Run and verify the script{ENDC}")

with ncs.maapi.single_write_trans('admin', 'python') as t:
    root = ncs.maagic.get_root(t)
    dns_server_list = root.devices.device['ex1'].config.sys.dns.server
    dns_server_list.create('192.0.2.1')
    params = t.get_params()
    params.dry_run_native()
    result = t.apply_params(True, params)
    print(result['device']['ex1'])
    t.apply_params(True, t.get_params())
    print('Done!')

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/dns/server'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

INPUT_DATA = {"router:server": [{"address": "192.0.2.2"}]}
PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/dns'\
       '/server?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(json.dumps(r.json(), indent=2))

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/dns/server'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/dns/server'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

print(f"{OKGREEN}##### Done{ENDC}")
