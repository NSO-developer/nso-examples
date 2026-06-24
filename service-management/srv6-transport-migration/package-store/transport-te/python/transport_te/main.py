# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service


def device_loopback(root, device_name: str) -> str:
    info = root.core_network.devices[device_name]
    return f'fd00::{info.index}'


def exists(node) -> bool:
    backend = getattr(node, '_backend', None)
    path = getattr(node, '_path', None)
    if backend is not None and path:
        try:
            return bool(backend.exists(path))
        except Exception:
            return False
    return False


def ned_id(root, device_name: str) -> str:
    device_type = root.devices.device[device_name].device_type
    try:
        cli_ned_id = str(device_type.cli.ned_id)
        if cli_ned_id not in ('', 'None'):
            return cli_ned_id
    except Exception:
        pass
    try:
        netconf_ned_id = str(device_type.netconf.ned_id)
        if netconf_ned_id not in ('', 'None'):
            return netconf_ned_id
    except Exception:
        pass
    return ''


def head_end_kind(root, device_name: str) -> str:
    device_ned_id = ned_id(root, device_name)
    if ('cisco-iosxr-netsim-cli' in device_ned_id or
            'cisco-iosxr-netsim-nc' in device_ned_id):
        return 'xr'
    if 'juniper-junos-netsim-nc' in device_ned_id:
        return 'junos'
    raise ValueError(
        f'Head-end {device_name} must be an IOS XR or Junos netsim device, '
        f'got {device_ned_id or "unknown device type"}'
    )


def ensure_junos_dynamic_paths(service):
    for path in service.path:
        if not exists(path.dynamic):
            raise ValueError(
                f'Junos head-end rendering for SR policy {service.name} '
                'currently supports dynamic paths only'
            )


def validate_junos_odn(service):
    if not exists(service.dynamic):
        raise ValueError(
            f'Junos head-end rendering for ODN template {service.name} '
            'requires dynamic path options'
        )


def add_ip_choice(params: ncs.template.Variables, name: str, value: str):
    params.add(f'{name}_IPV4', value if '.' in value else '')
    params.add(f'{name}_IPV6', value if ':' in value else '')


class PolicyServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        endpoint = (
            device_loopback(root, str(service.tail_end_device))
            if service.tail_end_device
            else str(service.end_point)
        )

        for head_end in service.head_end:
            head_end = str(head_end)
            kind = head_end_kind(root, head_end)
            source = (
                str(service.source_address)
                if service.source_address
                else device_loopback(root, head_end)
            )

            params = ncs.template.Variables()
            params.add('HEAD_END', head_end)
            params.add('ENDPOINT', endpoint)
            params.add('SOURCE', source)
            add_ip_choice(params, 'ENDPOINT', endpoint)
            add_ip_choice(params, 'SOURCE', source)

            template = ncs.template.Template(service)
            if kind == 'junos':
                ensure_junos_dynamic_paths(service)
            template.apply('transport-te-policy', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class OdnServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        for head_end in service.head_end:
            head_end = str(head_end)
            kind = head_end_kind(root, head_end)
            source = (
                str(service.source_address)
                if service.source_address
                else device_loopback(root, head_end)
            )

            params = ncs.template.Variables()
            params.add('HEAD_END', head_end)
            params.add('SOURCE', source)
            add_ip_choice(params, 'SOURCE', source)

            template = ncs.template.Template(service)
            if kind == 'junos':
                validate_junos_odn(service)
            template.apply('transport-te-odn', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_service('transport-te-policy-service',
                              PolicyServiceCallbacks)
        self.register_service('transport-te-odn-service',
                              OdnServiceCallbacks)

    def teardown(self):
        self.log.info('Main FINISHED')
