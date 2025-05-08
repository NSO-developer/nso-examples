# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service

from common.vpn import assign_vni, port_configuration


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        customer = root.inventory.customer[service.customer]
        vni = assign_vni(customer, 'eline', service.vni, proplist, root)

        for p in service.ports:
            self.log.info(f'Configuring port {p}')
            params = port_configuration(root, customer, p,
                                        vni, service.force)
            template = ncs.template.Template(service)
            template.apply('eline-port', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_service('eline-service', ServiceCallbacks)

    def teardown(self):
        self.log.info('Main FINISHED')
