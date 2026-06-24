# -*- mode: python; python-indent: 4 -*-
import ipaddress

import ncs
from ncs.application import Service


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


def device_kind(root, device_name: str) -> str:
    device_ned_id = ned_id(root, device_name)
    if ('cisco-iosxr-netsim-cli' in device_ned_id or
            'cisco-iosxr-netsim-nc' in device_ned_id):
        return 'xr'
    if 'juniper-junos-netsim-nc' in device_ned_id:
        return 'junos'
    if 'alu-sr-netsim-cli' in device_ned_id:
        return 'sros'
    raise ValueError(
        f'Device {device_name} is not a supported RSVP/MPLS-TE netsim node, '
        f'got {device_ned_id or "unknown device type"}'
    )


def is_supported_device(root, device_name: str) -> bool:
    try:
        device_kind(root, device_name)
        return True
    except ValueError:
        return False


def router_id(root, device_name: str) -> str:
    mgmt_base = root.core_network.settings.management_base
    mgmt_base = ipaddress.ip_address(str(mgmt_base))
    index = int(root.core_network.devices[device_name].index)
    return str(mgmt_base + index)


def iter_device_interfaces(root, device_name: str):
    seen = set()
    for link in root.core_network.links:
        enabled = True
        try:
            enabled = bool(link.enabled)
        except Exception:
            enabled = True
        if not enabled:
            continue

        if str(link.device_a) == device_name:
            ifname = str(link.interface_a)
        elif str(link.device_b) == device_name:
            ifname = str(link.interface_b)
        else:
            continue

        if ifname in seen:
            continue
        seen.add(ifname)
        yield ifname


class RsvpUnderlayCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        selected = [str(name) for name in service.device]
        if not selected:
            selected = [
                str(device.name)
                for device in root.core_network.devices
                if (
                    device.enabled
                    and is_supported_device(root, str(device.name))
                )
            ]

        for device_name in selected:
            if device_name not in root.core_network.devices:
                raise ValueError(f'Unknown core-network device {device_name}')
            device_kind(root, device_name)

            template = ncs.template.Template(service)

            params = ncs.template.Variables()
            params.add('DEVICE', device_name)
            params.add('LOOPBACK_IPV4', router_id(root, device_name))
            params.add('INTERFACE', '')
            params.add('BANDWIDTH_PERCENT', '')
            params.add('APPLY_DEVICE', 'true')
            params.add('APPLY_INTERFACE', '')
            template.apply('rsvp-underlay', params)

            for ifname in sorted(iter_device_interfaces(root, device_name)):
                params = ncs.template.Variables()
                params.add('DEVICE', device_name)
                params.add('LOOPBACK_IPV4', '')
                params.add('INTERFACE', ifname)
                params.add('BANDWIDTH_PERCENT', int(service.bandwidth_percent))
                params.add('APPLY_DEVICE', '')
                params.add('APPLY_INTERFACE', 'true')
                template.apply('rsvp-underlay', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('rsvp-underlay Main RUNNING')
        self.register_service('rsvp-underlay-service',
                              RsvpUnderlayCallbacks)

    def teardown(self):
        self.log.info('rsvp-underlay Main FINISHED')
