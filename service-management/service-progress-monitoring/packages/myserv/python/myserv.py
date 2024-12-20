"""NSO SPM Package example.

Implements a package with Service Progress Monitoring of Plans

See the README file for more information
"""
import _ncs
import ncs
from ncs.application import Service, PlanComponent
from ncs.dp import Action


class MyService(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        """Create callback."""
        self.log.info('cb_create(', service, '{', service.name, '})')

        devices = []
        for dev in root.ncs__devices.device:
            if len(dev.capability) == 0:
                raise Exception('Device %s has no known capabilities, ' +
                                'has sync-from been performed?' % (dev.name))
            devices.append(dev.name)

        planinfo = {}
        key = service.name
        fail_state = service.fail_state or ''
        myoper = ncs.maagic.cd(service, '/myserv:myoper')
        devroot = root.ncs__devices.device
        ip = service.dummy

        if len(devices) and key in myoper:
            oper = myoper[key]
            if oper.dns:
                if fail_state == 'dns':
                    planinfo['fail-state'] = fail_state
                else:
                    for q in devices:
                        devroot[q].config.r__sys.dns.server.create(ip)
                        planinfo['dns'] = 'INIT'
            if oper.ntp:
                if fail_state == 'ntp':
                    planinfo['fail-state'] = fail_state
                else:
                    for q in devices:
                        devroot[q].config.r__sys.ntp.server.create(ip)
                        planinfo['ntp'] = 'INIT'
            if oper.syslog:
                if fail_state == 'syslog':
                    planinfo['fail-state'] = fail_state
                else:
                    for q in devices:
                        devroot[q].config.r__sys.syslog.server.create(ip)
                        planinfo['syslog'] = 'INIT'

            if service.router2ready:
                planinfo['router2ready'] = 'INIT'

            if service.extended:
                if oper.interfaces:
                    for q in devices:
                        devroot[q].config.r__sys.interfaces.\
                            interface.create('dns')
                        planinfo['interfaces'] = 'INIT'
                planinfo['extended'] = 'INIT'

        self.write_kicker(root, key)
        self.write_plan_data(service, planinfo)

    def write_kicker(self, root, name):
        """Write kicker."""
        kicker = root.kicker__kickers.data_kicker.create('myservkicker')
        kicker.monitor = '/myserv:myoper[name=\'' + name + '\']'
        kicker.kick_node = '/myserv:myserv[name=current()/name]'
        kicker.action_name = 'reactive-re-deploy'

    def write_plan_data(self, service, planinfo):
        """Write plan."""
        self_plan = PlanComponent(service, 'self', 'ncs:self')
        self_plan.append_state('ncs:init')
        self_plan.append_state('ncs:ready')
        self_plan.set_reached('ncs:init')

        route_plan = PlanComponent(service, 'router', 'myserv:router')
        route_plan.append_state('ncs:init')
        route_plan.append_state('myserv:syslog-initialized')
        route_plan.append_state('myserv:ntp-initialized')
        route_plan.append_state('myserv:dns-initialized')
        route_plan.append_state('ncs:ready')
        route_plan.set_reached('ncs:init')

        route_plan2 = PlanComponent(service, 'router2', 'myserv:router')
        route_plan2.append_state('ncs:init')
        route_plan2.append_state('ncs:ready')
        route_plan2.set_reached('ncs:init')

        if 'fail-state' in planinfo:
            route_plan.set_failed('myserv:' + planinfo['fail-state'] +
                                  '-initialized')
        if 'dns' in planinfo:
            route_plan.set_reached('myserv:dns-initialized')
        if 'ntp' in planinfo:
            route_plan.set_reached('myserv:ntp-initialized')
        if 'syslog' in planinfo:
            route_plan.set_reached('myserv:syslog-initialized')

        if 'router2ready' in planinfo:
            route_plan2.set_reached('ncs:ready')

        if 'extended' in planinfo:
            ext_plan = PlanComponent(service, 'extended', 'myserv:router')
            ext_plan.append_state('ncs:init')
            ext_plan.append_state('myserv:interfaces-initialized')
            ext_plan.append_state('ncs:ready')
            ext_plan.set_reached('ncs:init')

            if 'interfaces' in planinfo:
                ext_plan.set_reached('myserv:interfaces-initialized')

        if 'dns' in planinfo and 'ntp' in planinfo and 'syslog' in planinfo:
            route_plan.set_reached('ncs:ready')
            if 'interfaces' in planinfo:
                ext_plan.set_reached('ncs:ready')

        if 'dns' in planinfo and 'ntp' in planinfo and \
           'syslog' and 'router2ready' in planinfo:
            route_plan.set_reached('ncs:ready')
            self_plan.set_reached('ncs:ready')
            if 'interfaces' in planinfo:
                ext_plan.set_reached('ncs:ready')


class SelfTest(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output):
        key = kp[0][0]

        with ncs.maapi.single_write_trans('admin', 'system',
                                          db=ncs.OPERATIONAL) as trans:
            myoper = ncs.maagic.get_node(trans, '/myserv:myoper')
            myoper = myoper.create(key)
            if input.dns:
                myoper.dns = input.dns
            if input.ntp:
                myoper.ntp = input.ntp
            if input.syslog:
                myoper.syslog = input.syslog
            if input.interfaces:
                myoper.interfaces = input.interfaces
            trans.apply()

        output.success = True
        output.message = key


class TimeoutHandler(Action):
    """This class implements Service Progress Monitoring Timeouts."""

    @Action.action
    def cb_action(self, uinfo, name, kp, input, output):
        """Called when the actionpoint is invoked.

        The function is called with:
            uinfo -- a UserInfo object
            name -- the tailf:action name (string)
            kp -- the keypath of the action (HKeypathRef)
            input -- input node (maagic.Node)
            output -- output node (maagic.Node)
        """
        self.log.debug("timeout(service={0}, result={1})".
                       format(str(input.service), str(input.timeout)))
        return _ncs.CONFD_OK


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('--- myserv.Main setup')
        self.register_service('myserv-servicepoint', MyService)
        self.register_action('myserv-self-test', SelfTest)
        self.register_action('myserv-timeout-point', TimeoutHandler)

    def teardown(self):
        self.log.info('--- myserv.Main teardown')
