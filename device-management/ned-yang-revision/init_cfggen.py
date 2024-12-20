#!/usr/bin/env python3

"""NSO device YANG model upgrade example.

Script to generate the inital device config

See the README file for more information
"""
import sys
import math


def print_xml_config(dev_str):
    """Print the config"""
    print("""<?xml version="1.0" encoding="UTF-8"?>
<config xmlns="http://tail-f.com/ns/config/1.0">

  <devices xmlns="http://tail-f.com/ns/ncs">
    <authgroups>
      <group>
        <name>my-group</name>
        <default-map>
          <remote-name>admin</remote-name>
          <remote-password>admin</remote-password>
        </default-map>
      </group>
    </authgroups>{}
  </devices>
</config>""".format(dev_str))


def gen_cfg_xml(n):
    """Generate the config"""
    dev_str = ""
    x = 0
    m = int(math.ceil(n / 256.0))
    for i in range(0, m):
        if n > 256:
            len = 256
        else:
            len = n
        for j in range(0, len):
            dev_str += """
    <device>
      <name>ex{}</name>
      <address>127.0.{}.{}</address>
      <port>12022</port>
      <authgroup>my-group</authgroup>
      <device-type>
        <netconf>
          <ned-id>router-nc-1.0</ned-id>
        </netconf>
      </device-type>
      <trace>raw</trace>
      <state>
        <admin-state>unlocked</admin-state>
      </state>
    </device>""".format(x, i, j)
            x += 1
        n -= len
    print_xml_config(dev_str)


def parse_num(num_str):
    """Handle power statement"""
    if num_str[:2] == '2^':
        return pow(2, parse_num(num_str[2:]))
    return int(num_str)


gen_cfg_xml(parse_num(sys.argv[1]))
