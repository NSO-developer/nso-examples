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
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
            ipint += 1
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
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
            ipint += 1
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

    @staticmethod
    def make_tagval_xml(ns_hash, tag, init, vtype):
        return _ncs.TagValue(_ncs.XmlTag(ns_hash, tag),
                             _ncs.Value(init, vtype))

    @staticmethod
    def make_tagval_str(ns_hash, tag, str, cs_node):
        return _ncs.TagValue(_ncs.XmlTag(ns_hash, tag),
                             _ncs.Value.str2val(str, cs_node))

    def traverse_xml(self, xml_node, cs_node, tvs):
        if cs_node.is_leaf() or cs_node.is_leaf_list() or cs_node.is_leafref():
            if xml_node.text is None:
                # Type empty
                tvs.append(self.make_tagval_xml(cs_node.ns(),
                                                cs_node.tag(),
                                                1,
                                                _ncs.C_NOEXISTS))
            else:
                tvs.append(self.make_tagval_str(cs_node.ns(),
                                                cs_node.tag(),
                                                xml_node.text,
                                                cs_node))
            return tvs
        tvs.append(self.make_tagval_xml(cs_node.ns(),
                                        cs_node.tag(),
                                        (cs_node.tag(), cs_node.ns()),
                                        _ncs.C_XMLBEGIN))
        cs_node_child = cs_node.children()
        curr_cs_node = cs_node_child
        for xml_child in xml_node:
            if '}' in xml_child.tag:
                temp = xml_child.tag.split('}', 1)
                xml_child.tag = temp[1]
                xml_child_ns = temp[0].split('{', 1)[1]
            while True:
                cs_node_tag = _ncs.hash2str(curr_cs_node.tag())
                cs_node_ns = _ncs.hash2str(curr_cs_node.ns())
                if '#' in cs_node_ns:
                    cs_node_ns = cs_node_ns.split('#', 1)[1]
                if cs_node_tag == xml_child.tag and cs_node_ns == xml_child_ns:
                    break
                curr_cs_node = curr_cs_node.next()  # if None - bad XML
            tvs = self.traverse_xml(xml_child, curr_cs_node, tvs)
            curr_cs_node = cs_node_child
        tvs.append(self.make_tagval_xml(cs_node.ns(),
                                        cs_node.tag(),
                                        (cs_node.tag(), cs_node.ns()),
                                        _ncs.C_XMLEND))
        return tvs

    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        """Service create callback"""
        self.log.info(f'Service create(service={service._path})')
        xml_config = self.gen_xml(service.num_routes)
        xml_node = ET.fromstring(xml_config)
        trans = ncs.maagic.get_trans(root)
        cs_node = _ncs.cs_node_cd(None,
                                  "/ncs:devices/device/config")
        tvs = []
        tvs = self.traverse_xml(xml_node, cs_node, tvs)
        trans.shared_set_values(tvs,
                                f'/devices/device{{{service.device}}}')


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
