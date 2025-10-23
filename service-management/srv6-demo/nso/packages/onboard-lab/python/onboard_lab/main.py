# -*- mode: python; python-indent: 4 -*-
import ipaddress

import ncs
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


def construct_ip_from_base(base, index: int):
    ip_base = int(ipaddress.ip_address(base))
    return ipaddress.ip_address(ip_base + index)


class OnboardAction(Action):
    @Action.action
    @only_works_in_config
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        if input.device:
            devices = input.device
        else:
            devices = [x.name for x in root.core_network.devices if x.enabled]
            devices.extend([x.name for x in root.inventory.ce if x.managed])

        mgmt_base = root.core_network.settings.management_base
        if not mgmt_base:
            error = ('management-base must be set under core-network/settings')
            self.log.error(error)
            raise Exception(error)

        self.log.info('Onboarding devices: ', devices)
        existing = root.devices.device
        onboarded = []
        for d in devices:
            if d in existing:
                continue

            if d in root.core_network.devices:
                index = root.core_network.devices[d].index
                addr = construct_ip_from_base(mgmt_base, index)
            elif d in root.inventory.ce:
                addr = root.inventory.ce[d].management_ip
            else:
                raise ValueError(f'Unknown device {d}')

            device = existing.create(d)
            device.address = addr
            device.authgroup = input.authgroup or 'cisco'
            device.auto_configure.create()
            device.auto_configure.vendor = 'Cisco'
            device.auto_configure.operating_system = 'IOS-XR'
            onboarded.append(d)

        output.onboarding = onboarded
        self.log.info('Onboarding devices done.')


class ResetAction(Action):
    @Action.action
    @only_works_in_config
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        if input.device:
            devices = input.device
        else:
            devices = [x.name for x in root.core_network.devices if x.enabled]
            devices.extend([x.name for x in root.inventory.ce if x.managed])

        mgmt_base = root.core_network.settings.management_base
        if not mgmt_base:
            error = ('management-base must be set under core-network/settings')
            self.log.error(error)
            raise Exception(error)

        for d in devices:
            self.log.info(f'Resetting {d}')
            if d in root.core_network.devices:
                index = root.core_network.devices[d].index
                addr = construct_ip_from_base(mgmt_base, index)
            elif d in root.inventory.ce:
                addr = root.inventory.ce[d].management_ip
            else:
                raise ValueError(f'Unknown device {d}')

            params = ncs.template.Variables()
            params.add('NAME', d)
            params.add('ADDRESS', addr)
            template = ncs.template.Template(root.devices.device[d])
            template.apply('node-reset', params)

            if d.startswith('ce-'):
                template.apply(f'{d}-extra', params)

        self.log.info('Reset done.')


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_action('onboard-lab', OnboardAction)
        self.register_action('onboard-lab-reset', ResetAction)

    def teardown(self):
        self.log.info('Main FINISHED')
