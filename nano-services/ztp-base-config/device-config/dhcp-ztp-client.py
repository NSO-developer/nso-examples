#!/usr/bin/env python3
"""Simulate an NCS 540 DHCP client and Option 67 bootloader."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from urllib import error, request


def arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument('--device', required=True)
    parser.add_argument('--leases', required=True)
    parser.add_argument('--download-dir', default='logs')
    parser.add_argument('--retry-timeout', type=float, default=300)
    parser.add_argument('--retry-interval', type=float, default=0.1)
    parser.add_argument('--request-timeout', type=float, default=10)
    return parser.parse_args()


def lease_for(args):
    with open(args.leases, encoding='utf-8') as lease_file:
        leases = json.load(lease_file)
    try:
        serial = leases['netsim-devices'][args.device]
        return leases, serial, leases['leases'][serial]
    except KeyError as exc:
        raise RuntimeError(
            f'{args.device} is missing from {args.leases}') from exc


def validate_ncs540_class(leases, lease):
    expected = leases['classes']['ncs540']
    vendor_class = lease['vendor-class-identifier']
    if (not vendor_class.startswith(expected['vendor-class-prefix']) or
            expected['pid-substring'] not in vendor_class):
        raise RuntimeError(
            f'{vendor_class} does not match the simulated ncs540 class')
    if lease['user-class'] != expected['user-class']:
        raise RuntimeError(
            f"unexpected DHCP user-class {lease['user-class']}")
    return expected['option-67']


def apply_initial_dhcp(download_dir):
    confd_dir = os.environ.get('CONFD_DIR')
    if not confd_dir:
        raise RuntimeError('CONFD_DIR is required to apply DHCP state')
    config_file = download_dir / 'simulated-dhcp-lease.cli'
    config_file.write_text(
        'interface MgmtEth0/RP0/CPU0/0\n'
        ' ipv4 address dhcp\n'
        '!\n', encoding='utf-8')
    environment = os.environ.copy()
    environment.pop('NCS_IPC_PATH', None)
    result = subprocess.run(
        [str(Path(confd_dir) / 'bin/confd_load'),
         '-F', 'c', '-m', '-l', str(config_file)],
        check=False, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        encoding='utf-8', env=environment)
    if result.returncode != 0:
        raise RuntimeError(
            f'failed to apply simulated DHCP state: {result.stdout.strip()}')
    print('Applied temporary DHCP state to MgmtEth0/RP0/CPU0/0', flush=True)


def download_payload(args, url, destination):
    deadline = time.monotonic() + args.retry_timeout
    while True:
        try:
            with request.urlopen(url, timeout=args.request_timeout) as response:
                destination.write_bytes(response.read())
            return
        except (error.HTTPError, error.URLError, TimeoutError) as exc:
            failure = str(exc)
        if args.retry_timeout <= 0 or time.monotonic() >= deadline:
            raise RuntimeError(
                f'failed to download DHCP Option 67 payload: {failure}')
        time.sleep(args.retry_interval)


def main():
    args = arguments()
    try:
        leases, serial, lease = lease_for(args)
        option_67 = validate_ncs540_class(leases, lease)
        server = leases['server']
        download_dir = Path(args.download_dir)
        download_dir.mkdir(parents=True, exist_ok=True)
        payload = download_dir / 'downloaded-option-67.py'

        print(
            'DHCPDISCOVER '
            f"interface={server['interface']} client-id={serial} "
            f"vendor-class={lease['vendor-class-identifier']} "
            f"user-class={lease['user-class']}", flush=True)
        print(
            f"DHCP server: {server['address']} "
            f"({server['interface']}, {server['daemon']})", flush=True)
        print(
            f"DHCPACK device={args.device} "
            f"dhcp-address={lease['dhcp-address']}", flush=True)
        print(f'DHCP Option 67 (bootfile-name): {option_67}', flush=True)

        apply_initial_dhcp(download_dir)
        download_payload(args, option_67, payload)
        print(f'Downloaded Option 67 payload to {payload}', flush=True)

        command = [
            sys.executable, '-u', str(payload),
            '--netsim-name', args.device,
            '--serial', serial,
            '--dhcp-address', lease['dhcp-address'],
            '--option67-url', option_67,
            '--download-dir', str(download_dir),
            '--retry-timeout', str(args.retry_timeout),
            '--request-timeout', str(args.request_timeout),
        ]
        return subprocess.run(command, check=False).returncode
    except (KeyError, OSError, ValueError, RuntimeError) as exc:
        print(f'DHCP/ZTP bootstrap failed: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
