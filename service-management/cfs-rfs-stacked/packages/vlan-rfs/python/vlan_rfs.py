# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service
from ncs.dp import Action


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info("cb_create(f'service{{{service.name}}})")
        vlan = service
        dev = root.ncs__devices.device[vlan.device]
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
class VlanRFS(ncs.application.Application):
    def setup(self):
        self.log.info('VlanRFS RUNNING')
        # Service callback
        self.register_service('vlan-servicepoint', ServiceCallbacks)
        # Action for self test
        self.register_action('vlanselftest', SelfTestHandler)

    def teardown(self):
        self.log.info('VlanRFS FINISHED')
