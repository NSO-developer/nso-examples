# -*- mode: python; python-indent: 4 -*-
import ipaddress
import ncs
from ncs.application import Service


# ------------------------
# SERVICE CALLBACK EXAMPLE
# ------------------------
class ServiceCallbacks(Service):

    # The create() callback is invoked inside NCS FASTMAP and
    # must always exist.
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        cidr_mask = service.cidr_netmask

        quad_mask = ipaddress.IPv4Network((0, cidr_mask)).netmask

        vars = ncs.template.Variables()
        vars.add('NETMASK', quad_mask)
        template = ncs.template.Template(service)
        template.apply('iface-template', vars)

        proplist.append(('DEVICE', service.device))
        proplist.append(('INTERFACE', service.interface))
        return proplist

    # The pre_modification() and post_modification() callbacks are optional,
    # and are invoked outside FASTMAP. pre_modification() is invoked before
    # create, update, or delete of the service, as indicated by the enum
    # ncs_service_operation op parameter. Conversely
    # post_modification() is invoked after create, update, or delete
    # of the service. These functions can be useful e.g. for
    # allocations that should be stored and existing also when the
    # service instance is removed.

    # @Service.pre_modification
    # def cb_pre_modification(self, tctx, op, kp, root, proplist):
    #     self.log.info('Service premod(service=', kp, ')')

    @Service.post_modification
    def cb_post_modification(self, tctx, op, kp, root, proplist):
        self.log.info('iface-postmod post-modification callback')
        # On deprovision
        if op == ncs.dp.NCS_SERVICE_DELETE:
            # Cannot use service any longer (it's gone), so use vars instead
            vars = ncs.template.Variables()
            for pname, pvalue in proplist:
                vars.add(pname, pvalue)

            template = ncs.template.Template(root.services)
            template.apply('iface-deactivate', vars)

        return proplist


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class Main(ncs.application.Application):
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('Main RUNNING')

        # Service callbacks require a registration for a 'service point',
        # as specified in the corresponding data model.
        #
        self.register_service('iface-servicepoint', ServiceCallbacks)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Main FINISHED')
