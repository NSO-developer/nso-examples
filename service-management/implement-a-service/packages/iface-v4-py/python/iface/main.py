# -*- mode: python; python-indent: 4 -*-
import ipaddress
import ncs
from ncs.application import Service
from ncs.dp import Action


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

    # @Service.post_modification
    # def cb_post_modification(self, tctx, op, kp, root, proplist):
    #     self.log.info('Service postmod(service=', kp, ')')


class IfaceActions(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        # root = ncs.maagic.get_root(trans)
        # service = ncs.maagic.cd(root, kp)
        # device = root.devices.device[service.device]

        status = 'unknown'  # Replace with your own code that checks e.g.
        # operational status of the interface

        output.status = status


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
        self.register_action('iface-test-enabled', IfaceActions)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Main FINISHED')
