# -*- mode: python; python-indent: 4 -*-
#
# This is a helper module for service.py

import unittest

_ipv4_size = 32
_ipv4_max = 2 ** _ipv4_size - 1


def getIpAddress(addr):
    """Return the Ip part of a 'Ip/Net' string."""
    parts = addr.split('/')
    return parts[0]


def getIpPrefix(addr):
    """Return the Net part of a 'Ip/Net' string."""
    parts = addr.split('/')
    return parts[1]


def getNetMask(addr):
    """Get the NetMask from a 'Ip/Net' string."""
    return ipv4_int_to_str(prefix_to_netmask(int(getIpPrefix(addr))))


def getNextIPV4Address(addr):
    """Get the next succeeding IP address...hm..."""
    i = ipv4_str_to_int(getIpAddress(addr)) + 1

    if i > _ipv4_max:
        raise ValueError("next IPV4 address out of bound")
    else:
        if (i & 0xff) == 255:
            i += 2

    return ipv4_int_to_str(i)


def prefixToWildcardMask(prefix):
    """Transform a prefix (as string) to a netmask (as a string)."""
    return ipv4_int_to_str(prefix_to_netmask(int(prefix)))


def prefix_to_netmask(prefix):
    """Transform an IP integer prefix to a netmask integer."""
    global _ipv4_size
    global _ipv4_max
    if (prefix >= 0) and (prefix <= _ipv4_size):
        return _ipv4_max ^ (2 ** (_ipv4_size - prefix) - 1)
    else:
        raise ValueError('IPV4 prefix out of bound')


def ipv4_str_to_int(addr):
    """Transform an IPV4 address string to an integer."""
    parts = addr.split('.')
    if len(parts) == 4:
        return (int(parts[0]) << 24) | (int(parts[1]) << 16) | \
            (int(parts[2]) << 8) | int(parts[3])
    else:
        raise ValueError('wrong format of IPV4 string')


def ipv4_int_to_str(value):
    """Transform an IP integer to a string"""
    global _ipv4_max
    if (value >= 0) and (value <= _ipv4_max):
        return '%d.%d.%d.%d' % (value >> 24, (value >> 16) & 0xff,
                                (value >> 8) & 0xff, value & 0xff)
    else:
        raise ValueError('IPV4 value out of bound')


class TestTheMethods(unittest.TestCase):

    def test_str_to_int(self):
        self.assertEqual(ipv4_int_to_str(ipv4_str_to_int('192.168.128.2')),
                         '192.168.128.2')

    def test_ipAddr(self):
        self.assertEqual(getIpAddress('192.168.128.2/24'), '192.168.128.2')

    def test_ipPrefix(self):
        self.assertEqual(getIpPrefix('192.168.128.2/24'), '24')

    def test_netMask(self):
        self.assertEqual(getNetMask('192.168.128.2/24'), '255.255.255.0')
        self.assertEqual(getNetMask('192.168.128.2/16'), '255.255.0.0')

    def test_nextIPV4Address(self):
        self.assertEqual(getNextIPV4Address('192.168.128.2'), '192.168.128.3')
        self.assertEqual(getNextIPV4Address('192.168.128.254'),
                         '192.168.129.1')

    def test_prefixToWildcardMask(self):
        self.assertEqual(prefixToWildcardMask('24'), '255.255.255.0')
        self.assertEqual(prefixToWildcardMask('16'), '255.255.0.0')


if __name__ == '__main__':
    unittest.main()
