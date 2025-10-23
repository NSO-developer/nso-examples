# -*- mode: python; python-indent: 4 -*-
import ipaddress

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
    }
    for t in intf_types:
        if intf.lower().startswith(t):
            return intf_types[t], intf_number
    raise ValueError(intf)


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        # Use per-node config if supplied, fallback to global setting
        fast_reroute = service.fast_reroute
        if fast_reroute is None:
            fast_reroute = root.core_network.settings.fast_reroute

        info = root.core_network.devices[service.name]
        mgmt_base = root.core_network.settings.management_base

        params = ncs.template.Variables()
        params.add('AS',
                   65000)
        params.add('ROUTER_ID',
                   str(construct_ip_from_base(mgmt_base, info.index)))
        params.add('LOOPBACK_IP',
                   f'fd00::{info.index}')
        params.add('INDEX_4CHAR',
                   str(info.index).rjust(4, '0'))
        params.add('SRV6_PREFIX',
                   f'5f00:0:{info.index}:')
        params.add('USE_CDP',
                   1 if root.core_network.settings.enable_cdp else '')
        params.add('USE_FRR',
                   1 if fast_reroute else '')
        template = ncs.template.Template(service)

        if 'core' in info.role or 'pe' in info.role:
            # First we apply a baseline SRv6 config
            self.log.info('Applying host config')
            template.apply('srv6-node', params)

            # Then we add PE specific config
            if 'pe' in info.role:
                reflectors = []
                for d in root.core_network.devices:
                    if 'rr' in d.role:
                        reflectors.append(f'fd00::{d.index}')
                service.rr_neighbors = reflectors
                self.log.info('Applying PE config')
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
                intf_type, intf_no = deconstruct_interface_string(intf)
                params.add('INTERFACE_NO', intf_no)
                template.apply(f'srv6-node-intf-{intf_type}', params)

        if 'rr' in info.role:
            clients = []
            for d in root.core_network.devices:
                if 'pe' in d.role:
                    clients.append(f'fd00::{d.index}')
            service.rr_neighbors = clients
            self.log.info('Applying RR config')
            template.apply('srv6-node-rr', params)


        self.log.info('Service create(service=', service._path, ') DONE')


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_action('srv6-node-provision', ProvisionAction)
        self.register_service('srv6-node-service', ServiceCallbacks)

    def teardown(self):
        self.log.info('Main FINISHED')
