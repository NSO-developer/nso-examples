# -*- mode: python; python-indent: 4 -*-
import ipaddress
import ncs
from ncs.application import Service
from ncs.dp import Action


class ServiceCallbacks(Service):

    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        cidr_mask = service.cidr_netmask

        quad_mask = ipaddress.IPv4Network((0, cidr_mask)).netmask

        vars = ncs.template.Variables()
        vars.add('NETMASK', quad_mask)
        template = ncs.template.Template(service)
        template.apply('iface-template', vars)


def update_service_status(service, root):
    status = str(root
        .devices.device[service.device]
        .live_status.sys.interfaces.interface[service.interface]
        .status.link
    )
    service.status = status
    return status


class IfaceActions(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        if name == 'update-status':
            with ncs.maapi.single_write_trans('admin', 'python',
                                              db=ncs.OPERATIONAL) as t:
                root = ncs.maagic.get_root(t)
                service = ncs.maagic.cd(root, kp)

                # Getting called from (telemetry) kicker?
                if input.tid:
                    # Attach to the synthetic transaction with updated data;
                    # if we read from the main transaction, the system has to
                    # make another call to the device to get the live-status,
                    # since YANG Push data is not saved or cached.
                    with trans.maapi.attach(input.tid) as dt:
                        try:
                            diff_root = ncs.maagic.get_root(dt)
                            status = update_service_status(service, diff_root)
                        except KeyError:
                            self.log.error(f'No service-related data in update')
                            status = 'unknown'
                else:
                    # Check live-status when called manually, e.g. from CLI
                    status = update_service_status(service, root)

                output.status = status
                t.apply()
        else:
            raise NotImplementedError(f'Unknown action: {name}')


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_service('iface-servicepoint', ServiceCallbacks)
        self.register_action('iface-update-status', IfaceActions)
        self.register_fun(init_oper_data, lambda _: None)

    def teardown(self):
        self.log.info('Main FINISHED')


def init_oper_data(state):
    # Note that this is only called on package (re)load; it won't handle
    # newly created instances.
    state.log.info('Populating operational data')
    with ncs.maapi.single_write_trans('admin', 'python',
                                      db=ncs.OPERATIONAL) as t:
        root = ncs.maagic.get_root(t)
        for service in root.iface:
            update_service_status(service, root)
        t.apply()
    state.log.info('Populating operational data DONE')

    return state
