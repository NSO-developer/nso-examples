# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service
from ncs.dp import Action


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        # check if it is reasonable to assume that devices
        # initially has been sync-from:ed
        self.log.info("cb_create(f'service{{{service.name}}})")
        devroot = root.ncs__devices.device
        vlan = service
        for dev in devroot:
            if len(dev.capability) == 0:
                raise Exception(f'Device {dev.name} has no known'
                                'capabilities, has sync-from been performed?')
                return
        for dev in devroot:
            ifs = dev.config.r__sys.interfaces
            iface = ifs.interface.create(vlan.iface)
            unit = iface.unit.create(vlan.unit)
            unit.vlan_id = vlan.vid
            unit.enabled = True
            unit.description = vlan.description


class SelfTestHandler(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output):
        key = kp[0]
        output.success = True
        output.message = key


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class VlanServiceRFS(ncs.application.Application):
    def setup(self):
        self.log.info('VlanServiceRFS RUNNING')
        # Service callback
        self.register_service('vlanspnt_v2', ServiceCallbacks)
        # Action for self test
        self.register_action('vlanselftest', SelfTestHandler)

    def teardown(self):
        self.log.info('VlanServiceRFS FINISHED')
