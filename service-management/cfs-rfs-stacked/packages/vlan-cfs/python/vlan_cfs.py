# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info(f'cb_create(service={service.name})')
        vlan_cfs = service
        for dev in root.ncs__devices.device:
            self.log.info(f'cb_create(device={dev.name})')
            vlan_rfs = root.vr__rfs_vlans.vlan.create(dev.name)
            vlan_rfs.iface = vlan_cfs.iface
            vlan_rfs.unit = vlan_cfs.unit
            vlan_rfs.vid = vlan_cfs.vid
            vlan_rfs.description = vlan_cfs.description


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class VlanCFS(ncs.application.Application):
    def setup(self):
        self.log.info('Vlam CFS RUNNING')
        # Service callback
        self.register_service('vlan-cfs-servicepoint', ServiceCallbacks)

    def teardown(self):
        self.log.info('Vlan CFS FINISHED')
