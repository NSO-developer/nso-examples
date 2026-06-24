# -*- mode: python; python-indent: 4 -*-
import ipaddress
import re

import ncs
from ncs.application import Service
from ncs.dp import Action


def only_works_in_config(func):
    def hint_on_error(*args, **kwargs):
        try:
            func(*args, **kwargs)
        except Exception as e:
            if getattr(e, 'confd_errno', 0) == ncs.ERR_NOT_WRITABLE:
                raise Exception('This action requires configure mode.')
            else:
                raise e
    return hint_on_error


class ProvisionAction(Action):
    @Action.action
    @only_works_in_config
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        devices = [x.name for x in root.core_network.devices if x.enabled]
        services = root.core_network.services.srv6_node

        self.log.info('Provisioning devices: ', devices)
        provisioned = []
        for d in devices:
            if d not in services:
                services.create(d)
                provisioned.append(d)

        output.provisioning = provisioned
        self.log.info('Provisioning devices done.')


def construct_ip_from_base(base, index: int):
    ip_base = int(ipaddress.ip_address(base))
    return ipaddress.ip_address(ip_base + index)


def deconstruct_interface_string(intf: str) -> tuple:
    for i, c in enumerate(intf):
        if c.isdigit():
            intf_number = intf[i:]
            break

    intf_types = {
        'gi':   '1g',
        'te':   '10g',
        'twe':  '25g',
        'for':  '40g',
        'fi':   '50g',
        'hu':   '100g',
        'two':  '200g',
        'fou':  '400g',
        'ge':   '1g',
        'xe':   '10g',
        'et':   '100g',
    }
    for t in intf_types:
        if intf.lower().startswith(t):
            return intf_types[t], intf_number
    raise ValueError(intf)


def junos_end_x_sid(srv6_prefix: str, intf_no: str) -> str:
    match = re.fullmatch(r'(\d+)/(\d+)/(\d+)(?::(\d+))?', intf_no)
    if not match:
        return f'{srv6_prefix}1aff::'

    _, pic, port, channel = match.groups()
    pic = int(pic)
    port = int(port)
    channel = int(channel or 0)

    # Keep the SID stable and human-readable in a Junos/MX-like style:
    # 0/0/0:0 -> ...:1a00::, 0/0/0:2 -> ...:1a02::, 0/0/1:0 -> ...:1a10::
    pic_nibble = min(0xA + pic, 0xF)
    port_nibble = min(port, 0xF)
    channel_nibble = min(channel, 0xF)
    return f'{srv6_prefix}1{pic_nibble:x}{port_nibble:x}{channel_nibble:x}::'


def base_template_params(root, info, fast_reroute) -> ncs.template.Variables:
    mgmt_base = root.core_network.settings.management_base
    params = ncs.template.Variables()
    params.add('AS', 65000)
    params.add('ROUTER_ID', str(construct_ip_from_base(mgmt_base,
                                                       info.index)))
    params.add('LOOPBACK_IP', f'fd00::{info.index}')
    params.add('INDEX_4CHAR', str(info.index).rjust(4, '0'))
    params.add('SRV6_PREFIX', f'5f00:0:{info.index}:')
    params.add('USE_CDP', 1 if root.core_network.settings.enable_cdp else '')
    params.add('USE_FRR', 1 if fast_reroute else '')
    return params


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        # Use per-node config if supplied, fallback to global setting
        fast_reroute = service.fast_reroute
        if fast_reroute is None:
            fast_reroute = root.core_network.settings.fast_reroute

        info = root.core_network.devices[service.name]
        template = ncs.template.Template(service)

        if 'core' in info.role or 'pe' in info.role:
            # First we apply a baseline SRv6 config
            self.log.info('Applying host config')
            params = base_template_params(root, info, fast_reroute)
            template.apply('srv6-node', params)

            # Then we add PE specific config
            if 'pe' in info.role:
                reflectors = []
                for d in root.core_network.devices:
                    if 'rr' in d.role:
                        reflectors.append(f'fd00::{d.index}')
                service.rr_neighbors = reflectors
                self.log.info('Applying PE config')
                params = base_template_params(root, info, fast_reroute)
                template.apply('srv6-node-pe', params)

            # Then we apply per-interface config
            interfaces = []
            for link in root.core_network.links:
                if link.device_a == service.name:
                    interfaces.append(link.interface_a)
                if link.device_b == service.name:
                    interfaces.append(link.interface_b)

            for intf in interfaces:
                self.log.info('Applying interface config for ', intf)
                params = base_template_params(root, info, fast_reroute)
                params.add('JUNOS_END_X_SID', '')
                if re.fullmatch(r'\d+/\d+/\d+', intf):
                    intf_type = '1g'
                    intf_no = intf
                    params.add('ALU_PORT', intf)
                    params.add('JUNOS_IFD', '')
                    params.add('JUNOS_IFL', '')
                else:
                    intf_type, intf_no = deconstruct_interface_string(intf)
                    params.add('ALU_PORT', '')
                    params.add('JUNOS_IFD', intf if intf.startswith(
                        ('ge-', 'xe-', 'et-')) else '')
                    params.add('JUNOS_IFL',
                               f'{intf}.0' if intf.startswith(
                                   ('ge-', 'xe-', 'et-')) else '')
                    params.add('JUNOS_END_X_SID',
                               junos_end_x_sid(f'5f00:0:{info.index}:',
                                               intf_no)
                               if intf.startswith(
                                   ('ge-', 'xe-', 'et-')) else '')
                params.add('INTERFACE_NO', intf_no)
                params.add('JUNOS_PORT', intf_no.split('/')[-1])
                template.apply(f'srv6-node-intf-{intf_type}', params)

        if 'rr' in info.role:
            clients = []
            for d in root.core_network.devices:
                if 'pe' in d.role:
                    clients.append(f'fd00::{d.index}')
            service.rr_neighbors = clients
            self.log.info('Applying RR config')
            params = base_template_params(root, info, fast_reroute)
            template.apply('srv6-node-rr', params)

        self.log.info('Service create(service=', service._path, ') DONE')


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_action('srv6-node-provision', ProvisionAction)
        self.register_service('srv6-node-service', ServiceCallbacks)

    def teardown(self):
        self.log.info('Main FINISHED')
