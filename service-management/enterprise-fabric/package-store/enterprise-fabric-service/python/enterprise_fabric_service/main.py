# -*- mode: python; python-indent: 4 -*-
import ipaddress

import ncs
from ncs.application import Service


OSPF_PROCESS = 100
DEFAULT_DNS_SERVERS = ('192.0.2.53', '192.0.2.54')
BFD_MIN_INTERVAL = 300
BFD_MULTIPLIER = 3
PRIMARY_OSPF_COST = 10
BACKUP_OSPF_COST = 200
JUNOS_SYSLOG_SEVERITY = {
    'alert': 'alert',
    'critical': 'critical',
    'error': 'error',
    'warning': 'warning',
    'notice': 'notice',
    'informational': 'info',
}


def add_many(params, values):
    for key, value in values.items():
        params.add(key, str(value))
    return params


def prefix_data(prefix):
    iface = ipaddress.IPv4Interface(str(prefix))
    return {
        'PREFIX': str(prefix),
        'IP': iface.ip,
        'MASK': iface.network.netmask,
        'NETWORK': iface.network.network_address,
        'WILDCARD': iface.network.hostmask,
    }


def area_to_dotted(area):
    return str(ipaddress.IPv4Address(int(area)))


def ntp_targets(root, service):
    selected = [str(item) for item in service.ntp_server]
    if not selected:
        selected = [
            str(item.name)
            for item in root.enterprise_fabric_catalog.ntp_server
        ]
    return [
        root.enterprise_fabric_catalog.ntp_server[name] for name in selected
    ]


def dns_servers(service):
    selected = [str(item) for item in service.management_profile.dns_server]
    if selected:
        return selected
    return list(DEFAULT_DNS_SERVERS)


def management_profile_data(service):
    profile = service.management_profile
    severity = str(profile.syslog_severity)
    return {
        'DOMAIN_NAME': profile.domain_name,
        'SYSLOG_SERVER': profile.syslog_server,
        'SYSLOG_IOS_SEVERITY': severity,
        'SYSLOG_JUNOS_SEVERITY': JUNOS_SYSLOG_SEVERITY[severity],
        'NTP_SOURCE_LOOPBACK': str(profile.ntp_source_loopback).lower(),
        'IOS_CONFIG_ARCHIVE': str(profile.ios_config_archive).lower(),
    }


def node_base_data(node, service):
    loopback = prefix_data(node.loopback)
    return {
        'DEVICE': node.device,
        'NODE_NAME': node.name,
        'BANNER': service.banner,
        'LOOPBACK_PREFIX': node.loopback,
        'LOOPBACK_IP': loopback['IP'],
        'LOOPBACK_MASK': loopback['MASK'],
        'ROUTER_ID': loopback['IP'],
        'LOOPBACK_NETWORK': loopback['NETWORK'],
        'LOOPBACK_WILDCARD': loopback['WILDCARD'],
        'LOOPBACK_AREA': node.loopback_ospf_area,
        'LOOPBACK_AREA_DOTTED': area_to_dotted(node.loopback_ospf_area),
        'BGP_AS': service.ibgp_as,
    }


def link_ospf_cost(link):
    if str(link.path_role) == 'backup':
        return BACKUP_OSPF_COST
    return PRIMARY_OSPF_COST


def link_data(link):
    address = prefix_data(link.address)
    return {
        'INTERFACE_NAME': link.interface_name,
        'DESCRIPTION': link.description,
        'PROVIDER': link.provider,
        'CIRCUIT_ID': link.circuit_id,
        'BANDWIDTH_MBPS': link.bandwidth_mbps,
        'LINK_BANDWIDTH_KBPS': int(link.bandwidth_mbps) * 1000,
        'PATH_ROLE': link.path_role,
        'BFD_ENABLED': str(link.bfd_enabled).lower(),
        'BFD_MIN_INTERVAL': BFD_MIN_INTERVAL,
        'BFD_MULTIPLIER': BFD_MULTIPLIER,
        'OSPF_COST': link_ospf_cost(link),
        'LINK_PREFIX': link.address,
        'LINK_IP': address['IP'],
        'LINK_MASK': address['MASK'],
        'LINK_NETWORK': address['NETWORK'],
        'LINK_WILDCARD': address['WILDCARD'],
        'LINK_AREA': link.ospf_area,
        'LINK_AREA_DOTTED': area_to_dotted(link.ospf_area),
    }


def routing_allows(link):
    current = str(link.routing_role)
    return current in ('core-primary', 'transit-backup')


def node_links(root, node_name):
    return [
        link for link in root.enterprise_fabric_catalog.link
        if str(link.node) == str(node_name)
    ]


def node_bgp_neighbors(root, node_name):
    return [
        peer for peer in root.enterprise_fabric_catalog.bgp_neighbor
        if str(peer.node) == str(node_name)
    ]


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        template = ncs.template.Template(service)
        ntp_servers = ntp_targets(root, service)
        profile = management_profile_data(service)
        dns_targets = dns_servers(service)

        for node in root.enterprise_fabric_catalog.node:
            node_info = node_base_data(node, service)

            variables = ncs.template.Variables()
            add_many(variables, node_info)
            add_many(variables, profile)
            template.apply('enterprise-fabric-base', variables)

            for dns_server in dns_targets:
                dns_vars = ncs.template.Variables()
                add_many(dns_vars, node_info)
                dns_vars.add('DNS_SERVER', dns_server)
                template.apply('enterprise-fabric-management-dns', dns_vars)

            for ntp in ntp_servers:
                ntp_vars = ncs.template.Variables()
                add_many(ntp_vars, node_info)
                ntp_vars.add('NTP_ADDRESS', ntp.address)
                template.apply('enterprise-fabric-ntp', ntp_vars)

            for link in node_links(root, node.name):
                link_info = link_data(link)
                link_vars = ncs.template.Variables()
                add_many(link_vars, node_info)
                add_many(link_vars, link_info)
                template.apply('enterprise-fabric-link', link_vars)

            if str(node.role) == 'edge' and node.static_next_hop:
                static_vars = ncs.template.Variables()
                add_many(static_vars, node_info)
                static_vars.add('NEXT_HOP', node.static_next_hop)
                template.apply('enterprise-fabric-ios-static', static_vars)

            if str(node.vendor) == 'ios' and str(node.role) == 'core':
                ospf_vars = ncs.template.Variables()
                add_many(ospf_vars, node_info)
                ospf_vars.add('OSPF_PROCESS', OSPF_PROCESS)
                template.apply(
                    'enterprise-fabric-ios-ospf-base',
                    ospf_vars,
                )

                loopback_vars = ncs.template.Variables()
                add_many(loopback_vars, node_info)
                loopback_vars.add('OSPF_PROCESS', OSPF_PROCESS)
                loopback_vars.add('NETWORK', node_info['LOOPBACK_NETWORK'])
                loopback_vars.add(
                    'WILDCARD',
                    node_info['LOOPBACK_WILDCARD'],
                )
                loopback_vars.add('AREA', node.loopback_ospf_area)
                template.apply(
                    'enterprise-fabric-ios-ospf-network',
                    loopback_vars,
                )

                for link in node_links(root, node.name):
                    if not routing_allows(link):
                        continue
                    link_info = link_data(link)
                    ospf_link_vars = ncs.template.Variables()
                    add_many(ospf_link_vars, node_info)
                    add_many(ospf_link_vars, link_info)
                    ospf_link_vars.add('OSPF_PROCESS', OSPF_PROCESS)
                    ospf_link_vars.add(
                        'NETWORK',
                        link_info['LINK_NETWORK'],
                    )
                    ospf_link_vars.add(
                        'WILDCARD',
                        link_info['LINK_WILDCARD'],
                    )
                    ospf_link_vars.add('AREA', link.ospf_area)
                    template.apply(
                        'enterprise-fabric-ios-ospf-network',
                        ospf_link_vars,
                    )
                    template.apply(
                        'enterprise-fabric-ios-ospf-interface',
                        ospf_link_vars,
                    )

                bgp_base_vars = ncs.template.Variables()
                add_many(bgp_base_vars, node_info)
                template.apply('enterprise-fabric-ios-bgp-base',
                               bgp_base_vars)

                network_vars = ncs.template.Variables()
                add_many(network_vars, node_info)
                network_vars.add('NETWORK', node_info['LOOPBACK_NETWORK'])
                network_vars.add('MASK', node_info['LOOPBACK_MASK'])
                template.apply(
                    'enterprise-fabric-ios-bgp-network',
                    network_vars,
                )

                for peer in node_bgp_neighbors(root, node.name):
                    peer_vars = ncs.template.Variables()
                    add_many(peer_vars, node_info)
                    peer_vars.add('PEER_ADDRESS', peer.peer_address)
                    peer_vars.add('PEER_NODE', peer.peer_node)
                    peer_vars.add('REMOTE_AS', peer.remote_as)
                    template.apply(
                        'enterprise-fabric-ios-bgp-neighbor',
                        peer_vars,
                    )

            if str(node.vendor) == 'junos':
                loopback_vars = ncs.template.Variables()
                add_many(loopback_vars, node_info)
                loopback_vars.add('AREA_DOTTED', node_info[
                    'LOOPBACK_AREA_DOTTED'
                ])
                loopback_vars.add('OSPF_INTERFACE', 'lo0.0')
                loopback_vars.add('PASSIVE', 'true')
                loopback_vars.add('OSPF_COST', '')
                loopback_vars.add('BFD_ENABLED', 'false')
                loopback_vars.add('BFD_MIN_INTERVAL', BFD_MIN_INTERVAL)
                loopback_vars.add('BFD_MULTIPLIER', BFD_MULTIPLIER)
                template.apply(
                    'enterprise-fabric-junos-ospf-interface',
                    loopback_vars,
                )

                for link in node_links(root, node.name):
                    if not routing_allows(link):
                        continue
                    link_info = link_data(link)
                    link_vars = ncs.template.Variables()
                    add_many(link_vars, node_info)
                    add_many(link_vars, link_info)
                    link_vars.add(
                        'AREA_DOTTED',
                        link_info['LINK_AREA_DOTTED'],
                    )
                    link_vars.add(
                        'OSPF_INTERFACE', f'{link.interface_name}.0'
                    )
                    link_vars.add('PASSIVE', 'false')
                    template.apply(
                        'enterprise-fabric-junos-ospf-interface',
                        link_vars,
                    )

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_service('enterprise-fabric-service', ServiceCallbacks)

    def teardown(self):
        self.log.info('Main FINISHED')
