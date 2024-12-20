"""NSO transaction performance example.

Implements service and validation callbacks and an action to calibrate CPU load

See the README file for more information
"""
from multiprocessing import Process, Value
import time
import ncs
from ncs.application import Service, NanoService
from ncs.dp import Action


def factorial(n):
    """CPU hogger"""
    num = 1
    while n >= 1:
        num = num * n
        n = n - 1
    return num


def sim_work(tw, nf, nw):
    """ Do some CPU hogging to simulate work"""
    # nw = number of  "work items" - work item calibrated by the action below
    # nf = number of factorials - same as used when calibrating
    # tw = time consumed by work
    # Note that, while rarely feasible, we can here sometimes divide the
    # workload from a large transaction into multiple processes to utilize
    # all CPU cores. Likely, we rather want to rely on large workloads being
    # divided up into several transactions that will be handled in parallel
    # by NSO, and do the work in a single process here.
    start = time.time()
    for i in range(nw):
        factorial(nf)
    tw.value = time.time() - start


class DevsServiceCallbacks(Service):
    """Service setting simulated device work
    """
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        """Service create callback"""
        self.log.info(f'Service create(service={service._path})')
        # Set the transaction delay on the devices. Controls how long netsim
        # devices will sleep in the prepare phase of a transaction updating the
        # device configuration.
        for device in root.ncs__devices.device:
            device.config.r__sys.trans_delay = service.dev_delay


class T3ServiceCallbacks(NanoService):
    """Nano service callback simulating service handling work and configuring
    devices
    """
    @NanoService.create
    def cb_nano_create(self, tctx, root, service, plan, component, state,
                       proplist, component_proplist):
        """Nano service create callback"""
        self.log.info(f'Nano create(state={state} id={service.id}'
                      f' value={service.value}')
        if state == 't3:configured':
            # Get settings from CDB
            # ndtrans = number of transactions to each device
            nf = root.t3s.t3_settings.nfactorial
            nw = root.t3s.t3_settings.nwork
            ndtrans = root.t3s.t3_settings.ndtrans

            # Simulate some CPU hogging work - using a worker process to escape
            # the Python GIL and allow other transactions to run in parallel
            t = Value('d', 0.0)
            p = Process(target=sim_work, args=(t, nf, nw))
            p.start()
            p.join()
            tw = t.value
            self.log.info(f'Wall clock time for the simulated work(service='
                          f'{service._path}): {tw}')

            if ndtrans > 0:  # number of devs to configure for the transaction
                dev_list = [x.name for x in root.devices.device]
                dev_list.sort()
                dev_n = len(dev_list)
                start_id = service.id
                for i in range(ndtrans):
                    dev_name = dev_list[(start_id * ndtrans + i) % dev_n]
                    device = root.devices.device[dev_name]
                    if_name = f'I-{service.id}@{tctx.th}'
                    device.config.sys.interfaces.interface.create(if_name)


class T3ActionHandler(Action):
    """Action handlers for validating and calibrating the CPU load"""
    @Action.action
    def cb_action(self, uinfo, name, keypath, input, output, trans):
        """Action callback"""
        self.log.info(f'Action(action point={str(name)})')
        if name == "validate":
            nf = 0
            nw = 0
            # Get settings from CDB
            with ncs.maapi.single_read_trans('admin', 'python',
                                             db=ncs.OPERATIONAL) as opertrans:
                root = ncs.maagic.get_root(opertrans)
                nf = root.t3s.t3_settings.nfactorial

            root = ncs.maagic.get_root(trans)
            nw = root.t3s.t3_settings.nwork

            # Simulate some CPU hogging work - using a worker process to escape
            # the Python GIL and allow other transactions to run in parallel
            t = Value('d', 0.0)
            p = Process(target=sim_work, args=(t, nf, nw))
            p.start()
            p.join()
            tw = t.value
            self.log.info('Wall clock time for the simulated work(action'
                          f' point={str(keypath)}): {tw}')

            service = ncs.maagic.get_node(trans, keypath)
            if "fail" in service.value:
                output.result = "false"
                raise Exception("Failed to validate")
            else:
                output.result = "true"
        elif name == "calibrate-cpu-time":
            ival = 10
            val = ival

            # Calculate how many factorial are needed to keep a CPU core busy
            # for one second
            while True:
                start = time.time()
                factorial(val)
                ts = time.time() - start
                if ts > 1.05:
                    val -= ival
                    ival = round(ival/2)
                elif ts < 0.01:
                    ival = ival * 10
                elif ts < 0.1:
                    ival = ival * 3
                elif ts > 1:
                    break
                val += ival

            # Store the result in CDB so that the service and validation
            # callbacks can use it to simulate CPU load
            # self.log.info(f'Action ival(action point={str(name)}):'
            #               f' {val} TS {ts}')
            with ncs.maapi.single_write_trans('admin', 'python',
                                              db=ncs.OPERATIONAL) as opertrans:
                root = ncs.maagic.get_root(opertrans)
                root.t3s.t3_settings.nfactorial = val
                opertrans.apply()


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO.
# ---------------------------------------------
class T3Application(ncs.application.Application):
    """Service appliction implementing service, validation, and action
    callbacks
    """
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('T3Application RUNNING')

        # Register services and action handlers:
        self.register_service('devs-servicepoint', DevsServiceCallbacks)
        self.register_nano_service('t3-servicepoint',       # Service point
                                   't3:ne',                 # Component
                                   't3:configured',         # State
                                   T3ServiceCallbacks)
        self.register_action('t3-validate', T3ActionHandler, [])
        self.register_action('t3-cputime', T3ActionHandler, [])

        # When we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NSO went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('T3Application FINISHED')
