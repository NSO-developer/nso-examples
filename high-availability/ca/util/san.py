#!/usr/bin/env python3
# Copyright 2026 Cisco Systems, Inc.
# SPDX-License-Identifier: MIT

import ipaddress
import sys

def main(hosts):
    san = []
    for host in hosts:
        if '_' in host:
            continue
        try:
            ipaddress.ip_address(host)
            san.append(f'IP:{host}')
        except ValueError:
            if '.' in host:
                san.append(f'DNS:{host}')

    if san:
        return 'subjectAltName=' + ', '.join(san)
    else:
        return ''


if __name__ == "__main__":
    print(main(sys.argv[1:]))
