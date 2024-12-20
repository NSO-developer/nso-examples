#!/usr/bin/env python3

"""An NSO Cluster Simulated System Install Built-in High Availability Three
   Node Package Upgrade Example.

Upgrade script

See the README file for more information
"""
import subprocess
import json
import argparse
import os
import requests

USAGE_STR = """Example of a basic high availability setup simulating a NSO
system installation with one primary and two secondary nodes performing a
package upgrade.
On most Linux distributions the above default IP addresses are configured for
the loopback interface by default. On MacOS the three unique IP addresses can
be created using for example the ip or ifconfig command:

        # MacOS example setup:
        $ sudo ifconfig lo0 alias 127.0.1.1/24 up
        $ sudo ifconfig lo0 alias 127.0.2.1/24 up
        $ sudo ifconfig lo0 alias 127.0.3.1/24 up

        # MacOS cleanup:
        $ sudo ifconfig lo0 -alias 127.0.1.1
        $ sudo ifconfig lo0 -alias 127.0.2.1
        $ sudo ifconfig lo0 -alias 127.0.3.1"""


def ha_upgrade_demo(ip1, ip2, ip3):
    """Run the HA upgrade demo"""
    auth = ('admin', 'admin')
    node1_url = 'http://{}:8080/restconf'.format(ip1)
    node2_url = 'http://{}:8080/restconf'.format(ip2)
    node3_url = 'http://{}:8080/restconf'.format(ip3)
    header = '\033[95m'
    okblue = '\033[94m'
    okgreen = '\033[92m'
    endc = '\033[0m'
    bold = '\033[1m'
    # ipc1 = 4561
    # ipc2 = 4562
    # ipc3 = 4563

    session = requests.Session()
    session.auth = auth
    headers = {'Content-Type': 'application/yang-data+json'}

    print(f"\n{okblue}##### Reset, setup, start node 1-3, and enable HA"
          f" with start-up settings\n{endc}")
    my_env = os.environ.copy()
    my_env['NSO_IP1'] = ip1
    my_env['NSO_IP2'] = ip2
    my_env['NSO_IP3'] = ip3
    subprocess.run(['make', 'stop', 'clean', 'system', 'start'], check=True,
                   stderr=subprocess.PIPE, stdout=subprocess.PIPE,
                   env=my_env, encoding='utf-8')

    print(f"\n{okblue}##### Initial high-availability config for all three"
          f" nodes\n{endc}")
    path = '/data/tailf-ncs:high-availability?content=config&' \
           'with-defaults=report-all'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    print(f"\n{okblue}##### Add some dummy config to node 1, replicated to"
          f" secondary nodes 2 and 3\n{endc}")

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

    for i in range(1, 4):
        if i == 1:
            node_url = node1_url
        elif i == 2:
            node_url = node2_url
        else:
            node_url = node3_url
        path = '/data/tailf-ncs:high-availability?content=nonconfig'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

        path = '/data/dummy:dummies'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

        path = '/data/tailf-ncs:packages/package=dummy/package-version'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

    print(f"\n{okblue}##### Backup before upgrading\n##### Since we are"
          f" simulating a system install with a local NSO install, we backup"
          f" the runtime directories for potential disaster recovery. Normally"
          f" we would use the ncs-backup tool for a system install\n{endc}")
    subprocess.run(['make', 'backup'],
                   check=True, encoding='utf-8')

    print(f"\n{okblue}##### Upgrade node 1 system install packages and sync"
          f" the packages to node 2 and 3\n{endc}")
    path = '/operations/software/packages/list'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print(r.text)

    input_data = {"input": {"package-from-file": os.getcwd() +
                            "/package-store/inert-1.0.tar.gz"}}
    path = '/operations/software/packages/fetch'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.post(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    input_data = {"input": {"package-from-file": os.getcwd() +
                            "/package-store/dummy-1.1.tar.gz"}}
    path = '/operations/software/packages/fetch'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.post(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    path = '/operations/software/packages/list'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print(r.text)

    input_data = {"input": {"package": "inert-1.0"}}
    path = '/operations/software/packages/install'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.post(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    input_data = {"input": {"package": "dummy-1.1", "replace-existing": ""}}
    path = '/operations/software/packages/install'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.post(node1_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    path = '/operations/software/packages/list'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print(r.text)

    input_data = {"input": {"and-reload": {"wait-commit-queue-empty": "",
                                           "max-wait-time": 3}}}
    path = '/operations/packages/ha/sync'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.post(node1_url + path, json=input_data, headers=headers)
    print(r.text)

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

    for i in range(1, 4):
        if i == 1:
            node_url = node1_url
        elif i == 2:
            node_url = node2_url
        else:
            node_url = node3_url
        path = '/data/tailf-ncs:high-availability?content=nonconfig'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

        path = '/data/dummy:dummies'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

        path = '/data/inert:inerts'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

        path = '/data/tailf-ncs:packages/package=dummy/package-version'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
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
    parser.add_argument('-c', '--ip3', nargs=1, type=str, default="127.0.3.1",
                        help='IP address for node 2')
    args = parser.parse_args()
    ha_upgrade_demo("".join(args.ip1), "".join(args.ip2), "".join(args.ip3))
