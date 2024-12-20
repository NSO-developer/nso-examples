#!/usr/bin/env python3

"""NSO Configuration database YANG example.

Showcase script

See the README file for more information
"""
import subprocess
import os
import json
import requests

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

print(f"{OKGREEN}##### Done{ENDC}")

print(f"{OKGREEN}##### Showcase: Extending the CDB with"
      f" Packages{ENDC}")

print(f"\n{OKBLUE}##### Step 1: Create a package{ENDC}")
subprocess.run(['ncs-make-package', '--service-skeleton', 'python', '--build',
                '--dest', 'packages/my-data-entries', 'my-data-entries'],
               check=True, encoding='utf-8')

print(f"\n{OKBLUE}##### Step 2: Add package to NSO{ENDC}")
subprocess.run(['ncs'], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
               check=True, encoding='utf-8')

PATH = '/operations/tailf-ncs:packages/reload'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/my-data-entries:my-data-entries'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print("Status code: {}\n".format(r.status_code))

print(f"{OKBLUE}##### Step 3: Set data{ENDC}")
MY_DATA = {"name": "ex1", "dummy": "0.0.0.0"}
ENTRIES_DATA = {"my-data-entries:my-data-entries": [MY_DATA]}
INPUT_DATA = {"data": ENTRIES_DATA}
PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

print(f"{OKBLUE}##### Step 4: Inspect the YANG module{ENDC}")
# with open('packages/my-data-entries/src/yang/my-data-entries.yang') as f:
#     print(f"{HEADER}" + f.read() + f"{ENDC}")

PATH = '/data/tailf-ncs-monitoring:ncs-state/loaded-data-models/'\
       'data-model=my-data-entries'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text + "\n")

PATH = '/data/ietf-yang-library:modules-state/'\
       'module=my-data-entries,2016-01-01/schema'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text + "\n")

PATH = '/tailf/modules/my-data-entries/2016-01-01'
headers_yang = {'Content-Type': 'application/yang'}
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers_yang)
print(f"{HEADER}" + r.text + f"{ENDC}")

print(f"{OKGREEN}##### Showcase: Building and Testing a Model\n"
      f"{ENDC}")

print(f"{OKBLUE}##### Step 1: Create a model skeleton{ENDC}")
print(f"{OKBLUE}##### Note: Created the service skeleton package in"
      f" step 1 of the previous showcase{ENDC}")
os.chdir(os.getenv('NSO_RUNDIR') + "/packages/my-data-entries")
YANGSTR = """module my-test-model {
  namespace \"http://example.tail-f.com/my-test-model\";
  prefix \"t\";
"""
print(f"{HEADER}" + YANGSTR + f"{ENDC}")
with open("src/yang/my-test-model.yang", "w", encoding='utf-8') as f:
    f.write(YANGSTR)

print(f"{OKBLUE}##### Step 2: Fill out the model{ENDC}")
YANGSTR = """container host {
    leaf host-name {
      type string;
      description \"Hostname for this system\";
    }
    leaf-list domains {
      type string;
      description \"My favourite internet domains\";
    }
    container server-admin {
      description \"Administrator contact for this system\";
      leaf name {
        type string;
      }
    }
    list user-info {
      description \"Information about team members\";
      key \"name\";
      leaf name {
        type string;
      }
      leaf expertise {
        type string;
      }
    }
  }
}
"""
print(f"{HEADER}" + YANGSTR + f"{ENDC}")
with open("src/yang/my-test-model.yang", "a", encoding='utf-8') as f:
    f.write(YANGSTR)

print(f"{OKBLUE}##### Step 3: Compile and load the model{ENDC}")
subprocess.run(['make', '-C', 'src/'], check=True, encoding='utf-8')
os.chdir(os.getenv('NSO_RUNDIR'))
print(f"{BOLD}POST " + BASE_URL +
      '/operations/tailf-ncs:packages/reload' + f"{ENDC}")
r = session.post(BASE_URL + '/operations/tailf-ncs:packages/reload',
                 headers=headers)
print("\n" + r.text)

print(f"\n{OKBLUE}##### Step 4: Test the model\n{ENDC}")
USER1_DATA = {}
USER1_DATA["name"] = "Greta"
USER1_DATA["expertise"] = "sustainability"
USER2_DATA = {}
USER2_DATA["name"] = "Gunvald"
USER2_DATA["expertise"] = "security"
UINFO_DATA = [USER1_DATA, USER2_DATA]
HOST_DATA = {}
HOST_DATA["host-name"] = "my-host"
HOST_DATA["domains"] = ["tail-f.com", "tail-f.se"]
HOST_DATA["server-admin"] = {"name": "Ingrid"}
HOST_DATA["user-info"] = UINFO_DATA
INPUT_DATA = {"data": {"my-test-model:host": HOST_DATA}}
print(f"{BOLD}PATCH " + BASE_URL + '/data' + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + '/data', json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/data/my-test-model:host'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
RSTR = r.text + "\n"

with open("cdb-init.json", "w", encoding='utf-8') as f:
    f.write(RSTR)
print(f"{HEADER}" + RSTR + f"{ENDC}")

PATH = '/data/my-test-model:host'
print(f"{BOLD}DELETE " + BASE_URL + PATH + f"{ENDC}")
r = session.delete(BASE_URL + PATH, headers=headers)
print("Status code: {}".format(r.status_code))

PATH = '/data/my-test-model:host'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print("Status code: {}".format(r.status_code))

PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
with open("cdb-init.json", "r", encoding='utf-8') as f:
    rstr = f.read()
r = session.patch(BASE_URL + PATH, data=rstr, headers=headers)
print("Status code: {}".format(r.status_code))

headers_xml = {'Content-Type': 'application/yang-data+xml'}
PATH = '/data/my-test-model:host'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers_xml)
print(f"{HEADER}" + r.text + f"{ENDC}")

print(f"{OKGREEN}##### Done{ENDC}")
