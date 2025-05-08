# -*- mode: python; python-indent: 4 -*-
import ipaddress

import ncs
from ncs.application import Service

from common.vpn import assign_vni, extract_interface_number, port_configuration


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        customer = root.inventory.customer[service.customer]
        vni = assign_vni(customer, 'l3vpn', service.vni, proplist, root)

        for link in service.link:
            if not link.enabled:
                continue

            self.log.info(f'Configuring link {link.link_id}')
            params = port_configuration(root, customer, link.port,
                                        vni, service.force)

            subnet = link.subnet or f'10.1.{link.link_id}.0/24'
            network = ipaddress.IPv4Network(subnet)
            params.add('AS', 65000)
            params.add('PE_IP', network[link.pe_ip])
            params.add('CE_IP', network[link.ce_ip])
            params.add('SUBNET_CIDR', network.prefixlen)
            params.add('SUBNET_MASK', network.netmask)
            params.add('CE_AS', link.bgp_peering.peer_as or '')

            template = ncs.template.Template(service)
            template.apply('l3vpn-port', params)
            if link.bgp_peering.enabled:
                template.apply('l3vpn-bgp', params)

            port = root.inventory.port[link.port]
            if port.ce:
                ce = root.inventory.ce[port.ce]
                if ce.managed:
                    self.log.info(f'Configuring CE {ce.name}')
                    ce_intf = extract_interface_number(port.ce_port)
                    params.add('CE', ce.name)
                    params.add('CE_INTERFACE_NO', ce_intf)
                    params.add('CE_BGP', 1 if link.bgp_peering.enabled else '')
                    params.add('CE_BGP_ID', ce.management_ip)
                    template.apply('l3vpn-ce', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_service('l3vpn-service', ServiceCallbacks)

    def teardown(self):
        self.log.info('Main FINISHED')
