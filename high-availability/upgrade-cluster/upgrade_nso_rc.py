#!/usr/bin/env python3

"""An NSO Cluster Built-in High Availability Three Node NSO Version Upgrade
Example.

Upgrade script

See the README file for more information
"""
import subprocess
import json
import argparse
import time
import os
import requests
from packaging import version

USAGE_STR = """Example of a cluster high availability setup with one primary
and two secondary nodes performing a NSO version upgrade.
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


def ha_upgrade_demo(ip1, ip2, ip3, olddir, newdir):
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
    ipc1 = 4561
    ipc2 = 4562
    ipc3 = 4563

    session = requests.Session()
    session.auth = auth
    headers = {'Content-Type': 'application/yang-data+json'}

    r = subprocess.run(['{}/bin/ncs'.format(olddir), '--version'], check=True,
                       stdout=subprocess.PIPE, encoding='utf-8')
    # Replace underscore characters with dot and remove anything after a
    # non numeric/dot character in the NSO version string.
    old_version = r.stdout.split('_')[0]
    r = subprocess.run(['{}/bin/ncs'.format(newdir), '--version'], check=True,
                       stdout=subprocess.PIPE, encoding='utf-8')
    new_version = r.stdout.split('_')[0]

    print(f"\n{okblue}##### Reset, setup, start node 1-3, and enable HA"
          f" with start-up settings\n{endc}")
    my_env = os.environ.copy()
    my_env['NSO_IP1'] = ip1
    my_env['NSO_IP2'] = ip2
    my_env['NSO_IP3'] = ip3
    subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
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
        path = '/data/tailf-ncs:high-availability?content=nonconfig'
        if i == 1:
            node_url = node1_url
        elif i == 2:
            node_url = node2_url
        else:
            node_url = node3_url
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

        path = '/data/dummy:dummies'
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
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
    subprocess.run(['make', 'backup'], check=True, encoding='utf-8')

    # NSO 5.5 removed the show-log-directory parameter.
    if version.parse(old_version) < version.parse("5.5") and \
       version.parse(new_version) >= version.parse("5.5"):
        for i in range(1, 4):
            with open("nso-node{}/ncs.conf".format(i), "r",
                      encoding='utf-8') as file:
                data = file.read().replace("<show-log-directory>./logs"
                                           "</show-log-directory>", "")
            with open("nso-node{}/ncs.conf".format(i), "w",
                      encoding='utf-8') as file:
                file.write(data)

    # NSO 5.6 removed large-scale parameters.
    if version.parse(old_version) < version.parse("5.6") and \
       version.parse(new_version) >= version.parse("5.6"):
        for i in range(1, 4):
            with open("nso-node{}/ncs.conf".format(i), "r",
                      encoding='utf-8') as file:
                data = file.read().replace("<large-scale>",
                                           "<!-- large-scale>")
                data = data.replace("</large-scale>", "</large-scale -->")
            with open("nso-node{}/ncs.conf".format(i), "w",
                      encoding='utf-8') as file:
                file.write(data)

    print(f"\n{okblue}##### Disable high availability in node 3\n{endc}")
    path = '/operations/high-availability/disable'
    print(f"{bold}POST " + node3_url + path + f"{endc}")
    r = session.post(node3_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Disable node 1 high availability for node 2 to"
          f"automatically failover and assume primary role in read-only mode"
          f"\n{endc}")
    path = '/operations/high-availability/disable'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Switch VIP to point to node 2 instead of node 1"
          f"\n{endc}")

    print(f"\n{okblue}##### Rebuild node 1 package(s) with " + new_version +
          f"\n##### Note: This step could be performed earlier to further"
          f"minimize downtime.\n{endc}")
    subprocess.run(['tar', 'xvfz', 'dummy-1.0.tar.gz'],
                   cwd='nso-node1/packages', check=True, encoding='utf-8')
    os.remove("nso-node1/packages/dummy-1.0.tar.gz")
    subprocess.run(['make', '-C', 'nso-node1/packages/dummy-1.0/src', 'clean',
                    'all'], check=True, encoding='utf-8')

    print(f"\n{okblue}##### Upgrade node 1 to " + new_version + f"\n{endc}")
    my_env = os.environ.copy()
    my_env["sname"] = 'ncsd1'
    if "NCS_IPC_PATH" in my_env:
        my_env["NCS_IPC_PATH"] = my_env["NCS_IPC_PATH"] + "." + str(ipc1)
    else:
        my_env["NCS_IPC_ADDR"] = '127.0.0.1'
        my_env["NCS_IPC_PORT"] = str(ipc1)
    subprocess.run(['{}/bin/ncs'.format(olddir), '--stop'], check=True,
                   env=my_env, encoding='utf-8')
    subprocess.run(['{}/bin/ncs'.format(newdir), '--cd', 'nso-node1', '-c',
                    '{}/nso-node1/ncs.conf'.format(os.getcwd()),
                    '--with-package-reload'], check=True, env=my_env,
                   encoding='utf-8')

    print(f"\n{okblue}##### Switch VIP back to point to node 1\n{endc}")

    print(f"\n{okblue}##### Disable high availability in node 2\n{endc}")
    path = '/operations/high-availability/disable'
    print(f"{bold}POST " + node2_url + path + f"{endc}")
    r = session.post(node2_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    print(f"\n{okblue}##### Enable high availability in node 1"
          f" that will assume primary role\n{endc}")
    path = '/operations/high-availability/enable'
    print(f"{bold}POST " + node1_url + path + f"{endc}")
    r = session.post(node1_url + path, headers=headers)
    print("Status code: {}\n".format(r.status_code))

    for i in range(2, 4):
        print(f"\n{okblue}##### Rebuild node " + str(i) + " package(s) with " +
              new_version + f"\n{endc}")
        subprocess.run(['tar', 'xvfz', 'dummy-1.0.tar.gz'],
                       cwd='nso-node{}/packages'.format(i), check=True,
                       encoding='utf-8')
        os.remove("nso-node{}/packages/dummy-1.0.tar.gz".format(i))
        subprocess.run(['make', '-C',
                        'nso-node{}/packages/dummy-1.0/src'.format(i), 'clean',
                        'all'], check=True, encoding='utf-8')

        print(f"\n{okblue}##### Upgrade node " + str(i) + " to " +
              new_version + f"\n{endc}")
        my_env = os.environ.copy()
        my_env["sname"] = f'ncsd{i}'
        if "NCS_IPC_PATH" in my_env:
            if i == 2:
                my_env["NCS_IPC_PATH"] = my_env["NCS_IPC_PATH"] + "." + \
                    str(ipc2)
            else:
                my_env["NCS_IPC_PATH"] = my_env["NCS_IPC_PATH"] + "." + \
                    str(ipc3)
        else:
            my_env["NCS_IPC_ADDR"] = '127.0.0.1'
            if i == 2:
                my_env["NCS_IPC_PORT"] = str(ipc2)
            else:
                my_env["NCS_IPC_PORT"] = str(ipc3)
        subprocess.run(['{}/bin/ncs'.format(olddir), '--stop'], check=True,
                       env=my_env, encoding='utf-8')
        subprocess.run(['{}/bin/ncs'.format(newdir), '--cd',
                        'nso-node{}'.format(i), '-c',
                        '{}/nso-node{}/ncs.conf'.format(os.getcwd(), i),
                        '--with-package-reload'], check=True, env=my_env,
                       encoding='utf-8')

        while True:
            path = '/data/tailf-ncs:high-availability/status/mode'
            print(f"{bold}GET " + node1_url + path + f"{endc}")
            r = session.get(node1_url + path, headers=headers)
            if "primary" in r.text:
                break
            print(f"{header}#### Waiting for node 1 to become"
                  f" primary...{endc}")
            time.sleep(1)

        print(f"\n{okblue}##### Enable high availability in node " + str(i) +
              f" that will assume secondary role\n{endc}")
        path = '/operations/high-availability/enable'
        if i == 2:
            print(f"{bold}POST " + node2_url + path + f"{endc}")
            r = session.post(node2_url + path, headers=headers)
        else:
            print(f"{bold}POST " + node3_url + path + f"{endc}")
            r = session.post(node3_url + path, headers=headers)
        print("Status code: {}\n".format(r.status_code))

    while True:
        path = '/data/tailf-ncs:high-availability/status/connected-secondary'
        print(f"{bold}GET " + node1_url + path + f"{endc}")
        r = session.get(node1_url + path, headers=headers)
        if "n2" in r.text and "n3" in r.text:
            break
        print(f"{header}#### Waiting for node 2 & 3 to become secondary to"
              f" node 1...{endc}")
        time.sleep(1)

    for i in range(1, 4):
        path = '/data/tailf-ncs:high-availability?content=nonconfig'
        if i == 1:
            node_url = node1_url
        elif i == 2:
            node_url = node2_url
        else:
            node_url = node3_url
        print(f"{bold}GET " + node_url + path + f"{endc}")
        r = session.get(node_url + path, headers=headers)
        print(r.text)

        path = '/data/dummy:dummies'
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
    parser.add_argument('-o', '--olddir', nargs=1, type=str,
                        default="{}".format(os.getenv('NCS_DIR')),
                        help='Path to old NSO local install')
    parser.add_argument('-n', '--newdir', nargs=1, type=str,
                        default="{}".format(os.getenv('NCS_DIR')),
                        help='Path to new NSO local install')

    args = parser.parse_args()
    ha_upgrade_demo("".join(args.ip1), "".join(args.ip2), "".join(args.ip3),
                    "".join(args.olddir), "".join(args.newdir))
