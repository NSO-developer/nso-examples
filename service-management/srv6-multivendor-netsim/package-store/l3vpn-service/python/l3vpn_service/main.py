# -*- mode: python; python-indent: 4 -*-
import ipaddress

import ncs
from ncs.application import Service

from common.vpn import (
    assign_vni,
    extract_interface_number,
    get_rm_allocator,
    maybe_rm_service,
    port_configuration,
)


def assign_subnet(preferred,
                  customer,
                  service,
                  link,
                  root,
                  rm_service=None) -> str:
    try:
        # If Resource Manager is available, use it to allocate a subnet.
        Allocator = get_rm_allocator()
    except ModuleNotFoundError:
        # Otherwise fall back to a simple deterministic algorithm.
        return preferred or f'10.1.{link.link_id}.0/24'

    # A deterministic fallback overlaps when one customer has multiple VPNs.
    # RM lets us keep one pool per customer and avoid those collisions.
    allocation_root = ncs.maagic.get_root(rm_service) if rm_service else root
    pool = allocation_root.\
            ralloc__resource_pools.ipalloc__ip_address_pool.create(
                f'customer-vpn-subnets-{customer.cid}')
    pool.subnet.create('10.1.0.0', 16)

    if preferred:
        if not preferred.startswith('10.1.'):
            return preferred
        allocator = Allocator(rm_service or service).ip(preferred)
    else:
        allocator = Allocator(rm_service or service).ip().prefix_length(24)

    request = allocator.pool(pool.name)
    return request.allocate(f'l3vpn-link{link.link_id}-{service.name}')


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        customer = root.inventory.customer[service.customer]
        vni = assign_vni(customer, 'l3vpn', service.vni, proplist,
                         service, root)

        with maybe_rm_service(service) as rm_service:
            for link in service.link:
                if not link.enabled:
                    continue

                self.log.info(f'Configuring link {link.link_id}')
                params = port_configuration(root, customer, link.port,
                                            vni, service.force)

                subnet = assign_subnet(link.subnet, customer, service, link,
                                       root, rm_service=rm_service)
                network = ipaddress.IPv4Network(subnet)
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
                        params.add('CE_BGP',
                                   1 if link.bgp_peering.enabled else '')
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
