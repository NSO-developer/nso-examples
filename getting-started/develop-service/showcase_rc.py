#!/usr/bin/env python3

"""NSO developing a simple service example.

Showcase script

See the README file for more information
"""
import subprocess
import os
import sys
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
subprocess.run(['make', 'clean', 'all'], check=True, encoding='utf-8')

print(f"{OKBLUE}##### Have the environment variable NSO_RUNDIR point"
      f" to the runtime directory\n{ENDC}")
os.environ['NSO_RUNDIR'] = "{}/nso-lab-rundir".format(os.getcwd())
os.chdir(os.getenv('NSO_RUNDIR'))

print(f"{OKGREEN}##### Showcase: A Simple DNS Configuration"
      f" Service\n{ENDC}")

print(f"{OKBLUE}##### Step 1: Prepare simulated routers{ENDC}")
subprocess.run(['make', 'showcase-clean-start'], check=True, encoding='utf-8')

print(f"\n{OKBLUE}##### Step 2: Create a service package\n"
      f"{ENDC}")
os.chdir("{}/packages".format(os.getenv('NSO_RUNDIR')))
subprocess.run(['ncs-make-package', '--service-skeleton', 'python',
                'dns-config'], check=True, encoding='utf-8')

print(f"{OKBLUE}##### Step 3: Add the DNS server parameter"
      f"{ENDC}")
YANG_STR = ""
with open("dns-config/src/yang/dns-config.yang", "r", encoding='utf-8') as f:
    YANG_STR = f.read()
YANG_STR = YANG_STR.replace('leaf dummy {', 'leaf dns-server {')
with open("dns-config/src/yang/dns-config.yang", "w", encoding='utf-8') as f:
    f.write(YANG_STR)
print(f"{HEADER}" + YANG_STR + f"{ENDC}")
subprocess.run(['make', '-C', 'dns-config/src'], check=True, encoding='utf-8')

print(f"\n{OKBLUE}##### Step 4: Add Python code{ENDC}")
INPUT_STR = """self.log.info(\'Service create(service=\', service._path, \')\')
        dns_ip = service.dns_server
        ex1_device = root.devices.device["ex1"]
        ex1_config = ex1_device.config
        dns_server_list = ex1_config.sys.dns.server
        dns_server_list.create(dns_ip)"""
PY_STR = ""
with open("dns-config/python/dns_config/main.py", "r", encoding='utf-8') as f:
    PY_STR = f.read()
PY_STR = PY_STR.replace(
            "self.log.info(\'Service create(service=\', service._path, \')\')",
            INPUT_STR)
with open("dns-config/python/dns_config/main.py", "w", encoding='utf-8') as f:
    f.write(PY_STR)
print(f"{HEADER}" + PY_STR + f"{ENDC}")

print(f"{OKBLUE}##### Step 5: Deploy the service{ENDC}")
os.chdir(os.getenv('NSO_RUNDIR'))
subprocess.run(['ncs'], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
               check=True, encoding='utf-8')

PATH = '/operations/tailf-ncs:packages/reload'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH,
                 headers=headers)
print(r.text)

PATH = '/operations/tailf-ncs:devices/sync-from'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

DNS_DATA = {}
DNS_DATA["name"] = "test"
DNS_DATA["dns-server"] = "192.0.2.1"
INPUT_DATA = {"dns-config:dns-config": [DNS_DATA]}

PATH = '/data?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

INPUT_DATA = {}
INPUT_DATA["dns-server"] = "192.0.2.8"

PATH = '/data/dns-config:dns-config=test/dns-server?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

PATH = '/data/dns-config:dns-config=test/dns-server'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/data/tailf-ncs:devices/device=ex1/config/router:sys/dns/server'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text + "\n")

print(f"{OKGREEN}##### Done{ENDC}")
print(f"{OKGREEN}##### [Press ENTER to continue to the next showcase]"
      f"{ENDC}")
input("")

print(f"\n{OKGREEN}##### Setup the demo\n{ENDC}")
os.chdir(EXAMPLE_DIR)

print(f"{OKBLUE}##### Make sure no previous netsim processes are"
      f" running\n{ENDC}")
subprocess.run(['make', 'stop'], stdout=subprocess.PIPE,
               stderr=subprocess.PIPE, check=True, encoding='utf-8')

print(f"{OKBLUE}##### Create an NSO local install with a fresh runtime"
      f" directory\n{ENDC}")
subprocess.run(['make', 'clean', 'all'], check=True, encoding='utf-8')

print(f"{OKGREEN}##### Showcase: DNS Configuration"
      f" Service with Templates\n{ENDC}")

print(f"\n{OKBLUE}##### Step 1: Prepare simulated routers"
      f"{ENDC}")
os.chdir(os.getenv('NSO_RUNDIR'))
subprocess.run(['make', 'showcase-clean-start'], check=True, encoding='utf-8')

print(f"{OKBLUE}##### Step 2: Create a service{ENDC}")
os.chdir(os.getenv('NSO_RUNDIR') + "/packages")
subprocess.run(['ncs-make-package', '--build', '--service-skeleton', 'python',
               'dns-config'], check=True, encoding='utf-8')
INPUT_STR = """self.log.info(\'Service create(service=\', service._path, \')\')
        template_vars = ncs.template.Variables()
        template_vars.add("DNS_IP", "192.0.2.1")
        template = ncs.template.Template(service)
        template.apply('dns-config-tpl', template_vars)"""
PY_STR = ""
with open("dns-config/python/dns_config/main.py", "r", encoding='utf-8') as f:
    PY_STR = f.read()
PY_STR = PY_STR.replace(
            "self.log.info(\'Service create(service=\', service._path, \')\')",
            INPUT_STR)
with open("dns-config/python/dns_config/main.py", "w", encoding='utf-8') as f:
    f.write(PY_STR)
print(f"{HEADER}" + PY_STR + f"{ENDC}")

print(f"{OKBLUE}##### Step 3: Create a template{ENDC}")
os.chdir(os.getenv('NSO_RUNDIR'))
subprocess.run(['ncs', '--with-package-reload'], check=True, encoding='utf-8')

PATH = '/operations/tailf-ncs:devices/sync-from'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

PATH = '/data/tailf-ncs:devices/device=ex1/config/?fields=router:sys/dns/'\
       'server/address'
headers_xml = {'Content-Type': 'application/yang-data+xml'}
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers_xml)
print(r.text)

sys_xml = r.text.replace('<address>10.2.3.4</address>',
                         '<address>{$DNS_IP}</address>')
DNS_TPL = """<config xmlns="http://tail-f.com/ns/config/1.0">
  <devices xmlns="http://tail-f.com/ns/ncs">
    <device>
      <name>{{/device}}</name>{}
    </device>
  </devices>
</config>""".format(sys_xml)

# Requires Python 3.9+
if sys.version_info >= (3, 9):
    import xml.etree.ElementTree as ET
    e = ET.XML(DNS_TPL)
    ET.indent(e)
    DNS_TPL = ET.tostring(e, encoding='unicode')

with open("packages/dns-config/templates/dns-config-tpl.xml", "w",
          encoding='utf-8') as f:
    f.write(DNS_TPL)
print(f"{HEADER}" + DNS_TPL + f"{ENDC}\n")

print(f"{OKBLUE}##### Step 4: Test the service{ENDC}")
PATH = '/operations/tailf-ncs:packages/package=dns-config/redeploy'
print(f"{BOLD}POST " + BASE_URL + PATH + f"{ENDC}")
r = session.post(BASE_URL + PATH, headers=headers)
print(r.text)

DNS_DATA = {}
DNS_DATA["name"] = "dns-for-ex2"
DNS_DATA["device"] = "ex2"
INPUT_DATA = {"dns-config:dns-config": [DNS_DATA]}

PATH = '/data?dry-run'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print(r.text)

PATH = '/data'
print(f"{BOLD}PATCH " + BASE_URL + PATH + f"{ENDC}")
print(f"{HEADER}" + json.dumps(INPUT_DATA, indent=2) + f"{ENDC}")
r = session.patch(BASE_URL + PATH, json=INPUT_DATA, headers=headers)
print("Status code: {}\n".format(r.status_code))

PATH = '/data/tailf-ncs:devices/device=ex2/config/router:sys/dns/server'
print(f"{BOLD}GET " + BASE_URL + PATH + f"{ENDC}")
r = session.get(BASE_URL + PATH, headers=headers)
print(r.text + "\n")

print(f"{OKGREEN}##### Done{ENDC}")
