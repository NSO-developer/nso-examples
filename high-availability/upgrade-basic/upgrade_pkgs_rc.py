#!/usr/bin/env python3

"""A Basic NSO Built-in High Availability Two Node Package Upgrade
   Example.

Upgrade script

See the README file for more information
"""
import subprocess
import json
import argparse
import time
import os
import shutil
import requests

USAGE_STR = """Example of a basic high availability setup with one primary and
one secondary node performing a package upgrade.
On most Linux distributions the above default IP addresses are configured for
the loopback interface by default. On MacOS the two unique IP addresses can be
created using for example the ip or ifconfig command:

        # MacOS example setup:
        $ sudo ifconfig lo0 alias 127.0.1.1/24 up
        $ sudo ifconfig lo0 alias 127.0.2.1/24 up

        # MacOS cleanup:
        $ sudo ifconfig lo0 -alias 127.0.1.1
        $ sudo ifconfig lo0 -alias 127.0.2.1"""


def ha_upgrade_demo(ip1, ip2):
    """Run the HA upgrade demo"""
    auth = ('admin', 'admin')
    node1_url = 'http://{}:8080/restconf'.format(ip1)
    node2_url = 'http://{}:8080/restconf'.format(ip2)
    header = '\033[95m'
    okblue = '\033[94m'
    okgreen = '\033[92m'
    endc = '\033[0m'
    bold = '\033[1m'

    session = requests.Session()
    session.auth = auth
    headers = {'Content-Type': 'application/yang-data+json'}

    print(f"\n{okblue}##### Reset, setup, start node 1 & 2, and enable HA"
          f" with start-up settings\n{endc}")
    my_env = os.environ.copy()
    my_env['NSO_IP1'] = ip1
    my_env['NSO_IP2'] = ip2
    subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
                   stderr=subprocess.PIPE, stdout=subprocess.PIPE,
                   env=my_env, encoding='utf-8')

    print(f"\n{okblue}##### Initial high-availability config for both"
          f" nodes\n{endc}")
    path = '/data/tailf-ncs:high-availability?content=config&' \
           'with-defaults=report-all'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    print(f"\n{okblue}##### Add some dummy config to node 1, replicated to"
          f" secondary node 2\n{endc}")

    dummy_data = {}
    dummy_data["name"] = "d1"
    dummy_data["dummy"] = "1.2.3.4"
    dummies_data = {}
    dummies_data["dummy"] = [dummy_data]
    input_data = {"dummy:dummies": dummies_data}

    path = '/data'
    print(f"{bold}PATCH " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.patch(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs:packages/package=dummy/package-version'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs:packages/package=dummy/package-version'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    print(f"\n{okblue}##### Enable read-only mode for node 1\n{endc}")
    path = '/operations/high-availability/read-only'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Enable read-only mode for node 2\n{endc}")
    path = '/operations/high-availability/read-only'
    print(f"{bold}POST " + node2_url + path + f"{endc}")
    r = session.post(node2_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Backup before upgrading\n##### Since we are using"
          f" a local NSO install, we backup the runtime directories for"
          f" potential disaster recovery.\n{endc}")
    subprocess.run(['make', 'backup'],
                   check=True, encoding='utf-8')

    print(f"\n{okblue}##### Disable read-only mode for node 1 to allow rw"
          f" transactionn{endc}")
    path = '/operations/high-availability/read-only'
    input_data = {
        "input": {
            "mode": "false"
        }
    }

    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Disable node 1 high availability for node 2 to"
          f"automatically failover and assume primary role in read-only mode"
          f"\n{endc}")
    path = '/operations/high-availability/disable'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Upgrade node 1 local install packages\n{endc}")
    shutil.copy("package-store/dummy-1.1.tar.gz", "nso-node1/packages")
    shutil.copy("package-store/inert-1.0.tar.gz", "nso-node1/packages")
    os.remove("nso-node1/packages/dummy-1.0.tar.gz")
    path = '/operations/packages/reload'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Disable high availability for node 2\n{endc}")
    path = '/operations/high-availability/disable'
    print(f"{bold}POST " + node2_url + path + f"{endc}")
    r = session.post(node2_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Enable high availability for node 1\n{endc}")
    path = '/operations/high-availability/enable'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Upgrade node 2 local install packages and enable"
          f" high availability to assume secondary role\n{endc}")
    shutil.copy("package-store/dummy-1.1.tar.gz", "nso-node2/packages")
    shutil.copy("package-store/inert-1.0.tar.gz", "nso-node2/packages")
    os.remove("nso-node2/packages/dummy-1.0.tar.gz")
    path = '/operations/packages/reload'
    print(f"{bold}POST " + node2_url + path + f"{endc}")
    r = session.post(node2_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    while True:
        path = '/data/tailf-ncs:high-availability/status/mode'
        print(f"{bold}GET " + node1_url + path + f"{endc}")
        r = session.get(node1_url + path, headers=headers)
        if "primary" in r.text:
            break
        print(f"{header}#### Waiting for node 1 to become primary...{endc}")
        time.sleep(1)

    path = '/operations/high-availability/enable'
    print(f"{bold}POST " + node2_url + path + f"{endc}")
    r = session.post(node2_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    while True:
        path = '/data/tailf-ncs:high-availability/status/connected-secondary'
        print(f"{bold}GET " + node1_url + path + f"{endc}")
        r = session.get(node1_url + path, headers=headers)
        if "n2" in r.text:
            break
        print(f"{header}#### Waiting for node 2 to become secondary to node 1"
              f"...{endc}")
        time.sleep(1)

    print(f"\n{okblue}##### Add some new config through node 1\n{endc}")
    dummy_data = {}
    dummy_data["name"] = "d1"
    dummy_data["description"] = "hello world"
    dummies_data = {}
    dummies_data["dummy"] = [dummy_data]
    input_data = {"dummy:dummies": dummies_data}

    path = '/data'
    print(f"{bold}PATCH " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.patch(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    inert_data = {}
    inert_data["name"] = "i1"
    inert_data["dummy"] = "4.3.2.1"
    inerts_data = {}
    inerts_data["inert"] = [inert_data]
    input_data = {"inert:inerts": inerts_data}

    path = '/data'
    print(f"{bold}PATCH " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.patch(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/inert:inerts'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data?fields=tailf-ncs:packages/package(name;package-version)'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    path = '/data/inert:inerts'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    path = '/data?fields=tailf-ncs:packages/package(name;package-version)'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    print(f"\n{okgreen}##### Done!\n{endc}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=USAGE_STR,
    )
    parser.add_argument('-a', '--ip1', nargs=1, type=str, default="127.0.1.1",
                        help='IP address for node 1')
    parser.add_argument('-b', '--ip2', nargs=1, type=str, default="127.0.2.1",
                        help='IP address for node 2')
    args = parser.parse_args()
    ha_upgrade_demo("".join(args.ip1), "".join(args.ip2))
