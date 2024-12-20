#!/usr/bin/env python3

"""NSO applications example.

Showcase script

See the README file for more information
"""
import subprocess
import os
import json
import requests

AUTH = ('admin', 'admin')
BASE_URL = 'http://localhost:8080/restconf'
EXAMPLE_DIR = os.getcwd()
HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'

session = requests.Session()
session.auth = AUTH
headers = {'Content-Type': 'application/yang-data+json'}

print(f"\n{OKGREEN}##### Setup the demo\n{ENDC}")

print(f"{OKBLUE}##### Make sure no previous netsim processes are"
      f" running\n{ENDC}")
subprocess.run(['make', 'stop'], stdout=subprocess.PIPE,
               stderr=subprocess.PIPE, check=True, encoding='utf-8')

print(f"{OKBLUE}##### Create an NSO local install with a fresh runtime"
      f" directory\n{ENDC}")
subprocess.run(['make', 'clean', 'all'], stdout=subprocess.PIPE,
               stderr=subprocess.PIPE, check=True, encoding='utf-8')

print(f"{OKBLUE}##### Have the environment variable NSO_RUNDIR point"
      f" to the runtime directory\n{ENDC}")
os.environ['NSO_RUNDIR'] = "{}/nso-lab-rundir".format(os.getcwd())

print(f"{OKGREEN}##### Showcase: Implementing Device Count Action\n{ENDC}")

print(f"{OKBLUE}##### Step 1: Create a new Python"
      f" package{ENDC}")
os.chdir("{}/packages".format(os.getenv('NSO_RUNDIR')))
subprocess.run(['ncs-make-package', '--service-skeleton', 'python',
               '--action-example', 'count-devices'], check=True,
               encoding='utf-8')

print(f"\n{OKBLUE}##### Step 2: Define a new action in"
      f" YANG{ENDC}")
YANG_STR = ""
with open("count-devices/src/yang/count-devices.yang", "r",
          encoding='utf-8') as f:
    YANG_STR = f.read()
YANG_STR = YANG_STR.replace('module count-devices {', 'module count-devices'
                            ' {\n  yang-version 1.1;')
ACTION_YANG = """  container custom-actions {
    action count-devices {
      tailf:actionpoint count-devices-action;
      input {
        leaf in-subnet {
          type inet:ipv4-prefix;
        }
      }
      output {
        leaf result {
          type uint16;
        }
      }
    }
  }
}
"""
with open("count-devices/src/yang/count-devices.yang", "w",
          encoding='utf-8') as f:
    for line in YANG_STR.splitlines():
        if line == '  description':
            f.write(ACTION_YANG)
            print(f"{HEADER}" + ACTION_YANG + f"{ENDC}")
            break
        f.write(line + "\n")
        print(f"{HEADER}" + line + f"{ENDC}")

print(f"{OKBLUE}##### Step 3: Implement the action logic{ENDC}")
ACTION_PY = """from ipaddress import IPv4Address, IPv4Network
import socket
import ncs
from ncs.dp import Action

class CountDevicesAction(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        count = 0
        root = ncs.maagic.get_root(trans)
        for device in root.devices.device:
            address = socket.gethostbyname(device.address)
            if IPv4Address(address) in IPv4Network(input.in_subnet):
                count = count + 1
        output.result = count
"""
with open("count-devices/python/count_devices/count_devices_action.py",
          "w", encoding='utf-8') as f:
    f.write(ACTION_PY)
print(f"{HEADER}" + ACTION_PY + f"{ENDC}")

print(f"{OKBLUE}##### Step 4: Register callback{ENDC}")
MAIN_PY = """import ncs
from .count_devices_action import CountDevicesAction

class Main(ncs.application.Application):
    def setup(self):
        self.register_action('count-devices-action', CountDevicesAction)
"""
with open("count-devices/python/count_devices/main.py", "w",
          encoding='utf-8') as f:
    f.write(MAIN_PY)
print(f"{HEADER}" + MAIN_PY + f"{ENDC}")

print(f"{OKBLUE}##### Step 5: And... action!{ENDC}")
os.chdir(os.getenv('NSO_RUNDIR'))
subprocess.run(['make', '-C', 'packages/router/src'], check=True,
               encoding='utf-8')
subprocess.run(['make', '-C', 'packages/count-devices/src'], check=True,
               encoding='utf-8')
subprocess.run(['ncs', '--with-package-reload'], check=True, encoding='utf-8')

INPUT_DATA = {"input": {"in-subnet": "127.0.0.0/16"}}
PATH = '/operations/count-devices:custom-actions/count-devices'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

PATH = '/data?fields=tailf-ncs:devices/device(name;address)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

ADDR_DATA = {"address": "localhost"}
PATH = '/data/tailf-ncs:devices/device=ex0/address'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(ADDR_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=ADDR_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/data?fields=tailf-ncs:devices/device(name;address)'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/operations/count-devices:custom-actions/count-devices'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.post(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

print(f"{OKGREEN}##### Done{ENDC}")
