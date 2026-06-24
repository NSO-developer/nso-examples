# -*- mode: python; python-indent: 4 -*-
import hashlib
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
    for node in service.vpn_nodes.vpn_node:
        node_id = str(node.vpn_node_id)
        for access in node.vpn_network_accesses.vpn_network_access:
            yield node_id, access


def validate_ports(root, service):
    accesses = list(iter_accesses(service))
    if len(accesses) < 2:
        raise ValueError(
            'The example wrapper expects at least two VPN accesses'
        )

    customers = set()
    for node_id, access in accesses:
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
        customers.add(str(port.customer))

    if len(customers) != 1:
        raise ValueError(
            'All accesses in the example wrapper must belong to the same '
            'customer'
        )

    return next(iter(customers)), accesses


def is_point_to_point(service, access_count: int) -> bool:
    vpn_type = str(service.vpn_type) if service.vpn_type else ''
    topology = (str(service.vpn_service_topology)
                if service.vpn_service_topology else '')

    if 'point-to-point' in topology or 'vpws' in vpn_type:
        if access_count != 2:
            raise ValueError(
                'A point-to-point or VPWS service in this example must use '
                'exactly two accesses'
            )
        return True
    return access_count == 2


class IetfL2vpnValidator(ValidationPoint):
    @ValidationPoint.validate
    def cb_validate(self, tctx, keypath, value, validationpoint):
        return ncs.CONFD_OK


class IetfL2vpnNmCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        vpn_id = str(service.vpn_id)
        if vpn_id not in root.l2vpn_ntw.vpn_services.vpn_service:
            raise ValidationError(
                f'IETF L2NM VPN service {vpn_id} does not exist'
            )
        vpn_service = root.l2vpn_ntw.vpn_services.vpn_service[vpn_id]

        try:
            customer, accesses = validate_ports(root, vpn_service)
        except ValueError as exc:
            raise ValidationError(str(exc))

        child = child_name('l2nm', vpn_id)

        template = ncs.template.Template(service)
        template_name = 'ietf-l2vpn-eline'
        if is_point_to_point(vpn_service, len(accesses)):
            template_name = 'ietf-l2vpn-eline'
        else:
            template_name = 'ietf-l2vpn-service'

        for _, access in accesses:
            params = ncs.template.Variables()
            params.add('CHILD_NAME', child)
            params.add('CUSTOMER', customer)
            params.add('PORT', access.interface_id)
            template.apply(template_name, params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('ietf-l2vpn-nm Main RUNNING')
        self.register_service('l2vpn-nm-servicepoint',
                              IetfL2vpnNmCallbacks)
        self.register_validation('l2vpn-nm-service-validation',
                                 IetfL2vpnValidator)

    def teardown(self):
        self.log.info('ietf-l2vpn-nm Main FINISHED')
