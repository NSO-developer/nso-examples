# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service
from ncs.dp import Action


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        # check if it is reasonable to assume that devices
        # initially has been sync-from:ed
        self.log.info("cb_create(f'service{{{service.tunnel_name}}})")
        devroot = root.ncs__devices.device
        tunnel = service
        for dev in devroot:
            if len(dev.capability) == 0:
                raise Exception(f'Device {dev.name} has no known'
                                'capabilities, has sync-from been performed?')
                return
        for dev in devroot:
            ifs = dev.config.r__sys.interfaces
            iface = ifs.interface.create(tunnel.interface)
            unit = iface.unit.create(tunnel.assembly)
            unit.vlan_id = tunnel.tunnel_id
            unit.enabled = True
            unit.description = tunnel.descr


class SelfTestHandler(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output):
        key = kp[0]
        output.success = True
        output.message = key


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class TunnelServiceRFS(ncs.application.Application):
    def setup(self):
        self.log.info('TunnelServiceRFS RUNNING')
        # Service callback
        self.register_service('tunnelspnt', ServiceCallbacks)
        # Action for self test
        self.register_action('tunnelselftest', SelfTestHandler)

    def teardown(self):
        self.log.info('TunnelServiceRFS FINISHED')
