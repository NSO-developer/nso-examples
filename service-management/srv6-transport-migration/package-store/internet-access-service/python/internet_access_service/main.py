# -*- mode: python; python-indent: 4 -*-
import ipaddress

import ncs
from ncs.application import Service

from common.vpn import extract_interface_number, port_configuration


UNSET = ('', 'None', 'none')


def text(value, default=''):
    try:
        rendered = str(value)
    except Exception:
        return default
    return default if rendered in UNSET else rendered


def boolean(value) -> bool:
    rendered = text(value).lower()
    if rendered in ('true', '1', 'yes'):
        return True
    if rendered in ('false', '0', 'no', ''):
        return False
    return bool(value)


def exists(node) -> bool:
    if node is None:
        return False
    backend = getattr(node, '_backend', None)
    path = getattr(node, '_path', None)
    if backend is not None and path:
        try:
            return bool(backend.exists(path))
        except Exception:
            pass
    return text(node) != ''


def is_xr(root, device_name: str) -> bool:
    ned_ids = []
    device_type = root.devices.device[device_name].device_type
    try:
        ned_ids.append(str(device_type.cli.ned_id))
    except Exception:
        pass
    try:
        ned_ids.append(str(device_type.netconf.ned_id))
    except Exception:
        pass
    return any('cisco-iosxr-netsim-cli' in ned_id or
               'cisco-iosxr-netsim-nc' in ned_id for ned_id in ned_ids)


def add_common_params(params, service, root):
    subnet = ipaddress.IPv4Network(str(service.access_subnet))
    params.add('PE_IP', subnet[int(service.pe_ip_index)])
    params.add('CE_IP', subnet[int(service.ce_ip_index)])
    params.add('SUBNET_MASK', subnet.netmask)
    params.add('SUBNET_PREFIXLEN', subnet.prefixlen)
    params.add('PROVIDER_AS', service.provider_as)
    params.add('CUSTOMER_AS', '')
    params.add('CUSTOMER_PREFIX', text(service.customer_prefix))
    port = root.inventory.port[service.port]
    ce_managed = False
    ce_name = text(port.ce)
    if ce_name:
        ce = root.inventory.ce[ce_name]
        params.add('CE', ce.name)
        ce_managed = boolean(ce.managed)
        params.add('CE_MANAGED', 'true' if ce_managed else '')
        params.add('CE_INTERFACE_NO', extract_interface_number(port.ce_port))
    else:
        params.add('CE', '')
        params.add('CE_MANAGED', '')
        params.add('CE_INTERFACE_NO', '')
    return ce_managed


class DiaCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        customer = root.inventory.customer[service.customer]
        port = root.inventory.port[service.port]
        if not is_xr(root, str(port.device)):
            raise ValueError(
                f'DIA port {service.port} must be on IOS XR in this example'
            )

        params = port_configuration(root, customer, service.port, None,
                                    service.force)
        ce_managed = add_common_params(params, service, root)

        template = ncs.template.Template(service)
        template.apply('internet-access-port', params)

        bgp = getattr(service, 'bgp', None)
        if exists(bgp):
            params.add('CUSTOMER_AS', bgp.peer_as)
            template.apply('internet-access-bgp', params)
        else:
            template.apply('internet-access-static', params)

        if ce_managed:
            template.apply('internet-access-ce', params)

        if text(service.pm_profile):
            params.add('PM_PROFILE', service.pm_profile)
            template.apply('internet-access-pm', params)

        assurance = getattr(service, 'service_assurance', None)
        if exists(assurance) and text(service.assurance_profile):
            params.add('ASSURANCE_PROFILE', service.assurance_profile)
            params.add('ASSURANCE_PROFILE_NAME',
                       text(assurance.profile_name, 'transport-dia'))
            params.add('ASSURANCE_RULE_NAME',
                       text(assurance.rule_name, 'dia-reachability'))
            params.add('ASSURANCE_PRESERVATION',
                       text(assurance.preservation, 'current'))
            params.add('ASSURANCE_MONITORING_STATE',
                       text(assurance.monitoring_state, 'active'))
            template.apply('internet-access-assurance-monitor', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('internet-access-service Main RUNNING')
        self.register_service('internet-access-service', DiaCallbacks)

    def teardown(self):
        self.log.info('internet-access-service Main FINISHED')
