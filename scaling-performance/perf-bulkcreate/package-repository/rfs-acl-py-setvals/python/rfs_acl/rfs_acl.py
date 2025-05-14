"""NSO example.

See the README file for more information
"""
import ipaddress
import xml.etree.ElementTree as ET
import ncs
from ncs.application import Service
import _ncs


class RfsAclServiceCallbacks(Service):
    """Service callback
    """
    @staticmethod
    def gen_route_xml(to):
        r_str = ""
        ipstr = ipaddress.IPv4Address(u'1.0.0.2')
        for i in range(to):
            r_str += f'''
    <route xmlns="http://cisco.com/ned/asa">
      <id>ics</id>
      <net>{ipstr}</net>
      <net-mask>255.255.255.255</net-mask>
      <gw>1.0.0.1</gw>
      <metric>1</metric>
    </route>'''
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr))) + 1
            ipstr = ipaddress.IPv4Address(ipint)
        return r_str

    @staticmethod
    def gen_rule_xml(to):
        r_str = ""
        ipstr = ipaddress.IPv4Address(u'1.0.0.1')
        for i in range(to):
            r_str += f'''
      <rule>
        <id>extended permit tcp host {ipstr} host {ipstr} eq https</id>
        <log/>
      </rule>'''
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr))) + 1
            ipstr = ipaddress.IPv4Address(ipint)
        return r_str

    def gen_xml(self, to):
        route_str = self.gen_route_xml(to)
        rule_str = self.gen_rule_xml(to)
        c_str = f'''<?xml version="1.0" encoding="UTF-8"?>
<config xmlns="http://tail-f.com/ns/ncs">{route_str}
  <access-list xmlns="http://cisco.com/ned/asa">
    <access-list-id>
      <id>tailf_42</id>{rule_str}
      <rule>
        <id>extended deny ip any4 any4</id>
        <log/>
      </rule>
      <rule>
        <id>extended deny ip any6 any6</id>
        <log/>
      </rule>
    </access-list-id>
  </access-list>
</config>'''
        return c_str

    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        """Service create callback"""
        self.log.info(f'Service create(service={service._path})')
        xml_config = self.gen_xml(service.num_routes)
        node = root.devices.device[service.device].config
        ncs.maagic.shared_set_values_xml(node, xml_config)

# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO.
# ---------------------------------------------
class RfsAclApplication(ncs.application.Application):
    """Service appliction
    """
    def setup(self):
        self.log.info('RfsAclApplication RUNNING')
        self.register_service('rfs-acl-servicepoint', RfsAclServiceCallbacks)

    def teardown(self):
        self.log.info('RfsAclApplication FINISHED')
