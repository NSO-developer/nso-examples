#!/usr/bin/env python3
"""Simulate an IOS-XR Option 67 payload and RESTCONF call-home."""

import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from urllib import error, request
from urllib.parse import quote, urlencode, urlsplit, urlunsplit


def arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument('--netsim-name', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--dhcp-address', required=True)
    parser.add_argument('--option67-url', required=True)
    parser.add_argument('--download-dir', default='logs')
    parser.add_argument('--username', default='admin')
    parser.add_argument('--password', default='admin')
    parser.add_argument(
        '--url',
        default=os.environ.get(
            'NSO_RESTCONF_URL', 'http://localhost:8180/restconf'))
    parser.add_argument(
        '--nso-user', default=os.environ.get('NSO_USER', 'admin'))
    parser.add_argument(
        '--nso-password', default=os.environ.get('NSO_PASSWORD', 'admin'))
    parser.add_argument('--label', default=os.environ.get('NSO_COMMIT_LABEL'))
    parser.add_argument('--retry-timeout', type=float, default=0)
    parser.add_argument('--retry-interval', type=float, default=0.1)
    parser.add_argument('--request-timeout', type=float, default=10)
    return parser.parse_args()


def provisioning_url(args, path):
    option_67 = urlsplit(args.option67_url)
    return urlunsplit((
        option_67.scheme,
        option_67.netloc,
        path,
        '',
        '',
    ))


def download(args, url, destination, description):
    try:
        with request.urlopen(url, timeout=args.request_timeout) as response:
            destination.write_bytes(response.read())
    except (error.HTTPError, error.URLError, TimeoutError) as exc:
        raise RuntimeError(f'failed to download {description}: {exc}') from exc
    print(f'Downloaded {description}: {url}', flush=True)
    print(f'Saved {description} to {destination}', flush=True)


def discover_router(args, download_dir):
    inventory_file = download_dir / 'downloaded-ztp-inventory.json'
    inventory_url = provisioning_url(args, '/ztp/ztp-inventory.json')
    download(args, inventory_url, inventory_file, 'ZTP inventory')
    try:
        inventory = json.loads(inventory_file.read_text(encoding='utf-8'))
        router = inventory['routers'][args.serial]
        hostname = router['hostname']
        management_ipv4 = router['management-ipv4']
    except (KeyError, TypeError, json.JSONDecodeError) as exc:
        raise RuntimeError(
            f'no valid ZTP inventory entry for serial {args.serial}') from exc
    if hostname != args.netsim_name:
        raise RuntimeError(
            f'serial {args.serial} maps to {hostname}, not {args.netsim_name}')
    if management_ipv4 == args.dhcp_address:
        raise RuntimeError('temporary and permanent management addresses match')

    print(f'Discovered chassis serial: {args.serial}', flush=True)
    print(f'Temporary DHCP address: {args.dhcp_address}', flush=True)
    print(
        f'ZTP inventory mapping: hostname={hostname} '
        f'management-ipv4={management_ipv4}', flush=True)
    return hostname, management_ipv4


def apply_config(config_file, description):
    confd_dir = os.environ.get('CONFD_DIR')
    if not confd_dir:
        raise RuntimeError('CONFD_DIR is required to apply netsim config')
    environment = os.environ.copy()
    environment.pop('NCS_IPC_PATH', None)
    result = subprocess.run(
        [str(Path(confd_dir) / 'bin/confd_load'),
         '-F', 'c', '-m', '-l', str(config_file)],
        check=False, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        encoding='utf-8', env=environment)
    if result.stdout:
        print(result.stdout.rstrip(), flush=True)
    if result.returncode != 0:
        raise RuntimeError(
            f'confd_load failed with status {result.returncode}')
    print(f'Applied {description} from {config_file}', flush=True)


def transition_to_management_vrf(args, download_dir, hostname):
    pre_file = download_dir / 'downloaded-ztp-pre.cli'
    pre_url = provisioning_url(args, '/ztp/ztp-pre.cli')
    download(args, pre_url, pre_file, 'pre-VRF configuration')
    apply_config(pre_file, 'pre-VRF configuration')
    print('Removed the temporary DHCP address before changing VRF', flush=True)

    bootstrap_file = download_dir / f'downloaded-{hostname}-ztp.cli'
    bootstrap_url = provisioning_url(
        args, f'/ztp/config/{quote(hostname, safe="")}-ztp.cli')
    download(args, bootstrap_url, bootstrap_file, 'ZTP configuration')
    apply_config(bootstrap_file, 'ZTP configuration')
    print('Reapplied DHCP addressing in the management VRF', flush=True)


def send_patch(args, payload):
    endpoint = f"{args.url.rstrip('/')}/data"
    if args.label:
        endpoint = f'{endpoint}?{urlencode({"label": args.label})}'
    encoded = json.dumps(payload).encode('utf-8')
    credentials = base64.b64encode(
        f'{args.nso_user}:{args.nso_password}'.encode('utf-8')).decode('ascii')
    call = request.Request(endpoint, data=encoded, method='PATCH')
    call.add_header('Authorization', f'Basic {credentials}')
    call.add_header('Accept', 'application/yang-data+json')
    call.add_header('Content-Type', 'application/yang-patch+json')

    deadline = time.monotonic() + args.retry_timeout
    attempt = 0
    while True:
        attempt += 1
        try:
            with request.urlopen(
                    call, timeout=args.request_timeout) as response:
                return endpoint, response.status
        except error.HTTPError as exc:
            detail = exc.read().decode('utf-8', errors='replace')
            failure = f'HTTP {exc.code}: {detail.strip()[:500]}'
        except (error.URLError, TimeoutError) as exc:
            failure = str(exc)

        if args.retry_timeout <= 0 or time.monotonic() >= deadline:
            raise RuntimeError(f'Day0 call-home failed: {failure}')
        if attempt == 1 or attempt % 20 == 0:
            print(f'NSO is not ready ({failure}); retrying',
                  file=sys.stderr, flush=True)
        time.sleep(args.retry_interval)


def call_home(args, hostname, management_ipv4):
    service = {
        'device': hostname,
        'management-ipv4': management_ipv4,
        'bootstrap-username': args.username,
        'bootstrap-password': args.password,
    }
    payload = {
        'ietf-yang-patch:yang-patch': {
            'patch-id': f'day0-call-home-{hostname}',
            'edit': [{
                'edit-id': 'create-day0-service',
                'operation': 'merge',
                'target': f'/base-cfs:base-cfs={hostname}',
                'value': {'base-cfs:base-cfs': [service]},
            }],
        },
    }
    endpoint, status = send_patch(args, payload)
    print(f'POST-ZTP call-home from {hostname}')
    print(f'PATCH {endpoint}')
    print(json.dumps(payload, indent=2))
    print(f'Call-home accepted: HTTP {status}')


def main():
    args = arguments()
    try:
        download_dir = Path(args.download_dir)
        download_dir.mkdir(parents=True, exist_ok=True)
        print(
            f'Executing downloaded IOS-XR ZTP payload for '
            f'{args.netsim_name}', flush=True)
        hostname, management_ipv4 = discover_router(args, download_dir)
        transition_to_management_vrf(args, download_dir, hostname)
        call_home(args, hostname, management_ipv4)
    except (OSError, ValueError, RuntimeError) as exc:
        print(f'IOS-XR ZTP failed: {exc}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
