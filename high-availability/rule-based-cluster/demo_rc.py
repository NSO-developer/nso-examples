#!/usr/bin/env python3

"""An NSO Built-in High Availability Three Node Cluster Example.

Demo script

See the README file for more information
"""
import subprocess
import json
import argparse
import time
import os
import requests

USAGE_STR = """Example of a basic high availability setup with one primary
and two secondary nodes.
On most Linux distributions the above default IP addresses are configured for
the loopback interface by default. On MacOS the three unique IP addresses can
be created using for example the ip or ifconfig command:

        # MacOS setup:
        $ sudo ifconfig lo0 alias 127.0.1.1/24 up
        $ sudo ifconfig lo0 alias 127.0.2.1/24 up
        $ sudo ifconfig lo0 alias 127.0.3.1/24 up

        # MacOS cleanup:
        $ sudo ifconfig lo0 -alias 127.0.1.1
        $ sudo ifconfig lo0 -alias 127.0.2.1
        $ sudo ifconfig lo0 -alias 127.0.3.1"""


def ha_demo(ip1, ip2, ip3):
    """Run the demo"""
    auth = ('admin', 'admin')
    node1_url = 'http://{}:8080/restconf'.format(ip1)
    node2_url = 'http://{}:8080/restconf'.format(ip2)
    node3_url = 'http://{}:8080/restconf'.format(ip3)
    header = '\033[95m'
    okblue = '\033[94m'
    okgreen = '\033[92m'
    endc = '\033[0m'
    bold = '\033[1m'
    ipc1 = 4561
    # ipc2 = 4562
    # ipc3 = 4563

    session = requests.Session()
    session.auth = auth
    headers = {'Content-Type': 'application/yang-data+json'}

    print(f"\n{okblue}##### Reset, setup, start node 1-3, and enable HA"
          f" assuming start-up settings\n{endc}")
    my_env = os.environ.copy()
    my_env['NSO_IP1'] = ip1
    my_env['NSO_IP2'] = ip2
    my_env['NSO_IP3'] = ip3
    subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
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

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
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

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    print(r.text)

    print(f"\n{okblue}##### Stop node 1 to make node 2 failover to primary"
          f" role and node 3 connect to the new primary\n{endc}")
    my_env = os.environ.copy()
    if "NCS_IPC_PATH" in my_env:
        my_env["NCS_IPC_PATH"] = my_env["NCS_IPC_PATH"] + "." + str(ipc1)
    else:
        my_env["NCS_IPC_ADDR"] = '127.0.0.1'
        my_env["NCS_IPC_PORT"] = str(ipc1)
    subprocess.run(['ncs', '--stop'], check=True, env=my_env, encoding='utf-8')

    path = '/data/tailf-ncs:high-availability/settings/reconnect-interval' \
           '?with-defaults=report-all'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    rci = r.json()["tailf-ncs:reconnect-interval"]

    path = '/data/tailf-ncs:high-availability/settings/reconnect-attempts' \
           '?with-defaults=report-all'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    rca = r.json()["tailf-ncs:reconnect-attempts"]
    rct = rci * rca
    while True:
        path = '/data/tailf-ncs:high-availability/status/mode'
        print(f"{bold}GET " + node2_url + path + f"{endc}")
        r = session.get(node2_url + path, headers=headers)
        if "primary" in r.text:
            break
        print(f"{header}#### Waiting for node 2 to fail reconnect to node 1"
              " and assume primary role... " + str(rct) + f"{endc}")
        if rct > 0:
            rct -= 1
        time.sleep(1)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs-alarms:alarms/alarm-list?content=nonconfig'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    while True:
        path = '/data/tailf-ncs:high-availability/status/be-secondary-result'
        print(f"{bold}GET " + node3_url + path + f"{endc}")
        r = session.get(node3_url + path, headers=headers)
        if "initialized" in r.text or "connected" in r.text:
            break
        print(f"{header}#### Waiting for node 3 to connect to new primary"
              f" node 2...{endc}")
        time.sleep(1)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs-alarms:alarms/alarm-list?content=nonconfig'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    print(r.text)

    print(f"\n{okblue}##### Start node 1 that will now assume secondary"
          f" role\n{endc}")
    subprocess.run(['ncs', '--cd', 'nso-node1', '-c',
                    '{}/nso-node1/ncs.conf'.format(os.getcwd())],
                   check=True, encoding='utf-8')

    while True:
        path = '/data/tailf-ncs:high-availability/status/mode'
        print(f"{bold}GET " + node1_url + path + f"{endc}")
        r = session.get(node1_url + path, headers=headers)
        if "secondary" in r.text:
            break
        print(f"{header}#### Waiting for node 1 to become secondary to node"
              f" 2...{endc}")
        time.sleep(1)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node2_url + path + f"{endc}")
    r = session.get(node2_url + path, headers=headers)
    print(r.text)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    print(r.text)

    print(f"\n{okblue}##### Role-revert the nodes back to start-up"
          f" settings\n{endc}")
    path = '/operations/high-availability/be-primary'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    input_data = {"input": {"node": "n1"}}
    path = '/operations/high-availability/be-secondary-to'
    print(f"{bold}POST " + node2_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.post(node2_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))
    print(r.text)

    input_data = {"input": {"node": "n1"}}
    path = '/operations/high-availability/be-secondary-to'
    print(f"{bold}POST " + node3_url + path + f"{endc}")
    print(f"{header}" + json.dumps(input_data, indent=2) + f"{endc}")
    r = session.post(node3_url + path, json=input_data, headers=headers)
    print("Status code: {}\n".format(r.status_code))
    print(r.text)

    while True:
        path = '/data/tailf-ncs:high-availability/status/mode'
        print(f"{bold}GET " + node1_url + path + f"{endc}")
        r = session.get(node1_url + path, headers=headers)
        if "primary" in r.text:
            break
        print(f"{header}#### Waiting for node 1 to revert to primary role"
              f"...{endc}")
        time.sleep(1)

    while True:
        path = '/data/tailf-ncs:high-availability/status/mode'
        print(f"{bold}GET " + node2_url + path + f"{endc}")
        r2 = session.get(node2_url + path, headers=headers)
        path = '/data/tailf-ncs:high-availability/status/be-secondary-result'
        print(f"{bold}GET " + node3_url + path + f"{endc}")
        r3 = session.get(node3_url + path, headers=headers)
        if "secondary" in r2.text and \
           ("initialized" in r3.text or "connected" in r3.text):
            break
        print(f"{header}#### Waiting for node 2 & 3 to revert to secondary"
              f" role for primary node 1...{endc}")
        time.sleep(1)

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node1_url + path + f"{endc}")
    r = session.get(node1_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
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

    path = '/data/tailf-ncs:high-availability?content=nonconfig'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
    print(r.text)

    path = '/data/dummy:dummies'
    print(f"{bold}GET " + node3_url + path + f"{endc}")
    r = session.get(node3_url + path, headers=headers)
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
                        help='IP address for node 3')
    args = parser.parse_args()
    ha_demo("".join(args.ip1), "".join(args.ip2), "".join(args.ip3))
