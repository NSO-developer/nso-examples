# -*- mode: python; python-indent: 4 -*-
"""NSO concurrency model conflict retry example.

Implement services and actions with retry on transaction conflicts

See the README file for more information
"""
from multiprocessing import Manager
import ncs
from ncs.application import Service
from ncs.dp import Action


# -------------------------
# SERVICE CALLBACK EXAMPLES
# -------------------------
class DNSServiceCallbacks(Service):
    '''DNS server service'''
    def init(self, init_args):
        self.server_semaphore = init_args[0]

    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        '''DNS server service create callback'''
        self.log.info('Service create(service=', service._path, ')')
        server_ip = root.servers.server[service.server].ip
        # Acquire a seemaphore which only purpose is to enable simulating a
        # config update conflict before continuing
        self.log.info('DNS service - try acquiring the semaphore')
        with self.server_semaphore:
            template_vars = ncs.template.Variables()
            template_vars.add("DNS_IP", server_ip)
            template = ncs.template.Template(service)
            template.apply("dns-config-template", template_vars)
        self.log.info('Service create done(service=', service._path, ')')


class NTPServiceCallbacks(Service):
    '''NTP server service'''
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        '''NTP server service create callback'''
        self.log.info('Service create(service=', service._path, ')')
        server_ip = root.servers.server[service.server].ip

        # The below transaction will cause a conflict and trigger an automatic
        # retry
        server_ip = server_ip[:server_ip.rfind('.')+1] + "121"
        with ncs.maapi.single_write_trans('admin', 'python') as trans:
            mroot = ncs.maagic.get_root(trans)
            mroot.servers.server[service.server].ip = server_ip
            trans.apply()

        template_vars = ncs.template.Variables()
        template_vars.add("NTP_IP", server_ip)
        template = ncs.template.Template(service)
        template.apply("ntp-config-template", template_vars)
        self.log.info('Service create done(service=', service._path, ')')


# ---------------
# ACTION EXAMPLES
# ---------------
class AcquireActionHandler(Action):
    '''Acquire action handler'''
    def init(self, init_args):
        self.server_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput):
        '''Action callback'''
        self.log.info("Acquire action")
        self.server_semaphore.acquire()
        aoutput.result = True


class ReleaseActionHandler(Action):
    '''Release action handler'''
    def init(self, init_args):
        self.server_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput):
        '''Action callback'''
        self.log.info("Release action")
        self.server_semaphore.release()
        aoutput.result = True


class UpdateNTPActionHandler(Action):
    '''Update NTP action handler'''
    def init(self, init_args):
        self.server_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput):
        '''Action callback'''
        # Acquire a semaphore whose only purpose is to enable simulating a
        # config update conflict before continuing
        with ncs.maapi.single_write_trans('admin', 'python') as trans:
            root = ncs.maagic.get_root(trans)
            server_ip = root.servers.server[ainput.server].ip
            self.log.info("Update NTP without retry action - try acquiring" +
                          " the semaphore")
            with self.server_semaphore:
                ntp = root.devices.device[ainput.device].config.sys.ntp
                ntp.server.create(server_ip)
                trans.apply()
        self.log.info("Update NTP without retry action - done")
        aoutput.result = True


class UpdateNTPRetryActionHandler(Action):
    '''Update NTP with retry action handler'''
    def init(self, init_args):
        self.server_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput):
        '''Action callback'''
        def update_ntp(trans, device, server):
            root = ncs.maagic.get_root(trans)
            server_ip = root.servers.server[ainput.server].ip
            # Acquire a semaphore which only purpose is to enable simulating a
            # config update conflict before continuing
            self.log.info("Update NTP with retry action - try acquiring" +
                          " the semaphore")
            with self.server_semaphore:
                ntp = root.devices.device[ainput.device].config.sys.ntp
                ntp.server.create(server_ip)
            self.log.info("Update NTP with retry action - done")
            return True

        self.log.info("Update NTP with retry action")
        with ncs.maapi.Maapi() as maapi:
            with ncs.maapi.Session(maapi, 'admin', 'python'):
                maapi.run_with_retry(lambda t: update_ntp(t, ainput.device,
                                     ainput.server))
        aoutput.result = True


class UpdateNTPRetryDecActionHandler(Action):
    '''Update NTP with retry decorator action handler'''
    def init(self, init_args):
        self.server_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput):
        '''Action callback'''
        @ncs.maapi.retry_on_conflict()
        def update_ntp(device, server):
            with ncs.maapi.single_write_trans('admin', 'python') as trans:
                root = ncs.maagic.get_root(trans)
                server_ip = root.servers.server[ainput.server].ip
                # Acquire a semaphore which only purpose is to enable
                # simulating a config update conflict before continuing
                self.log.info("Update NTP with decorator retry action - try" +
                              " acquiring the semaphore")
                with self.server_semaphore:
                    ntp = root.devices.device[ainput.device].config.sys.ntp
                    ntp.server.create(server_ip)
                trans.apply()
                self.log.info("Update NTP with retry decorator action - done")
            return True

        self.log.info("Update NTP with retry decorator action")
        aoutput.result = update_ntp(ainput.device, ainput.server)


# --------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO
# --------------------------------------------
class ServerApp(ncs.application.Application):
    '''Concurrency model retry service and action callbacks on conflict'''
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('Main RUNNING')

        manager = Manager()
        server_semaphore = manager.Semaphore()
        init_args = [server_semaphore]

        # Service callbacks require a registration for a 'service point',
        # as specified in the corresponding data model.
        self.register_service('dns-config-servicepoint', DNSServiceCallbacks,
                              init_args)
        self.register_service('ntp-config-servicepoint', NTPServiceCallbacks)

        # Action callback registration
        self.register_action('acquire-sem', AcquireActionHandler, init_args)
        self.register_action('release-sem', ReleaseActionHandler, init_args)
        self.register_action('update-ntp', UpdateNTPActionHandler, init_args)
        self.register_action('update-ntp-retry', UpdateNTPRetryActionHandler,
                             init_args)
        self.register_action('update-ntp-retry-dec',
                             UpdateNTPRetryDecActionHandler, init_args)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Main FINISHED')
