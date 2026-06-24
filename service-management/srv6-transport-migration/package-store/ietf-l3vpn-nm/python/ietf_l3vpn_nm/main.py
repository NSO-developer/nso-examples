# -*- mode: python; python-indent: 4 -*-
import hashlib
import ipaddress
import re

import ncs
from ncs.application import Service
from ncs.dp import ValidationError, ValidationPoint


def child_name(prefix: str, value: str) -> str:
    safe = re.sub(r'[^A-Za-z0-9-]+', '-', value).strip('-').lower()
    safe = re.sub(r'-+', '-', safe) or 'service'
    digest = hashlib.sha1(value.encode('utf-8')).hexdigest()[:6]
    budget = 24 - len(prefix) - len(digest) - 2
    trimmed = safe[:max(budget, 1)].strip('-') or 'svc'
    return f'{prefix}-{trimmed}-{digest}'


def iter_accesses(service):
    entries = []
    for node in service.vpn_nodes.vpn_node:
        node_id = str(node.vpn_node_id)
        for access in node.vpn_network_accesses.vpn_network_access:
            entries.append((node_id, str(access.id), access))
    entries.sort(key=lambda item: (item[0], item[1]))
    return entries


def first_ipv4_neighbor(bgp, network):
    for neighbor in bgp.neighbor:
        text = str(neighbor)
        if ':' in text:
            continue
        try:
            addr = ipaddress.IPv4Address(text)
        except ValueError:
            continue
        if addr in network:
            return addr
    return None


def access_parameters(root, node_id, access):
    port_id = str(access.interface_id)
    if port_id not in root.inventory.port:
        raise ValueError(
            f'VPN access {access.id} references unknown inventory port '
            f'{port_id}'
        )

    port = root.inventory.port[port_id]
    if str(port.device) != node_id:
        raise ValueError(
            f'VPN node {node_id} does not own inventory port {port_id}'
        )

    result = {
        'customer': str(port.customer),
        'port': port_id,
        'subnet': '',
        'pe_ip': '',
        'ce_ip': '',
        'peer_as': '',
    }

    try:
        local_address = str(access.ip_connection.ipv4.local_address)
        prefix_length = int(access.ip_connection.ipv4.prefix_length)
        network = ipaddress.ip_network(
            f'{local_address}/{prefix_length}', strict=False)
        result['subnet'] = str(network)
        result['pe_ip'] = str(
            int(ipaddress.ip_address(local_address))
            - int(network.network_address)
        )
    except Exception:
        network = None

    try:
        for proto in access.routing_protocols.routing_protocol:
            if 'bgp-routing' not in str(proto.type):
                continue
            if not proto.bgp.peer_as:
                continue
            result['peer_as'] = str(proto.bgp.peer_as)
            if network is not None:
                neighbor = first_ipv4_neighbor(proto.bgp, network)
                if neighbor is not None:
                    result['ce_ip'] = str(
                        int(neighbor) - int(network.network_address)
                    )
            break
    except Exception:
        pass

    return result


class IetfL3vpnVpnNodeValidator(ValidationPoint):
    @ValidationPoint.validate
    def cb_validate(self, tctx, keypath, value, validationpoint):
        return ncs.CONFD_OK


class IetfL3vpnServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        vpn_id = str(service.vpn_id)
        current_node = str(service.vpn_node_id)
        if vpn_id not in root.l3vpn_ntw.vpn_services.vpn_service:
            raise ValidationError(
                f'IETF L3NM VPN service {vpn_id} does not exist'
            )

        parent = root.l3vpn_ntw.vpn_services.vpn_service[vpn_id]
        if current_node not in parent.vpn_nodes.vpn_node:
            raise ValidationError(
                f'IETF L3NM VPN service {vpn_id} has no vpn-node '
                f'{current_node}'
            )

        accesses = iter_accesses(parent)
        if not accesses:
            raise ValidationError(
                'The example wrapper expects at least one VPN network access')

        template = ncs.template.Template(service)
        child = child_name('l3nm', str(parent.vpn_id))
        customer = None

        for link_id, item in enumerate(accesses, start=1):
            node_id, _access_id, access = item
            if node_id != current_node:
                continue
            try:
                params = access_parameters(root, node_id, access)
            except ValueError as exc:
                raise ValidationError(str(exc))

            if customer is None:
                customer = params['customer']
            elif customer != params['customer']:
                raise ValidationError(
                    'All accesses in the example wrapper must belong to the '
                    'same customer'
                )

            variables = ncs.template.Variables()
            variables.add('CHILD_NAME', child)
            variables.add('CUSTOMER', customer)
            variables.add('LINK_ID', link_id)
            variables.add('PORT', params['port'])
            variables.add('SUBNET', params['subnet'])
            variables.add('PE_IP', params['pe_ip'])
            variables.add('CE_IP', params['ce_ip'])
            variables.add('PEER_AS', params['peer_as'])
            template.apply('ietf-l3vpn-link', variables)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('ietf-l3vpn-nm Main RUNNING')
        self.register_service('l3vpn-nm-servicepoint',
                              IetfL3vpnServiceCallbacks)
        self.register_validation('l3vpn-nm-vpn-node-validation',
                                 IetfL3vpnVpnNodeValidator)

    def teardown(self):
        self.log.info('ietf-l3vpn-nm Main FINISHED')
