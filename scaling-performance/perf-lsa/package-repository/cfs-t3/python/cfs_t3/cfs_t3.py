"""NSO transaction performance example.

Implements service and validation callbacks and an action to calibrate CPU load

See the README file for more information
"""
import ncs
from ncs.application import Service


class CfsDevsServiceCallbacks(Service):
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
            device.config.t3__t3s.dev_settings.create()
            device.config.t3__t3s.dev_settings.dev_delay = service.dev_delay


class CfsT3ServiceCallbacks(Service):
    """Service callback simulating service handling work and configuring
    devices
    """

    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        """Service create callback"""
        self.log.info(f'Service create(service={service._path})')
        for device in root.ncs__devices.device:
            # Set the number of transactions per device.
            device.config.t3__t3s.t3_settings.ndtrans = service.ndtrans

            # Set the amount of work done per process.
            # One "work" item equals 1s of CPU time.
            device.config.t3__t3s.t3_settings.nwork = service.nwork

            # Configure the service instances
            run_id = service.run_id
            ntrans = service.ntrans
            for i in range(ntrans):
                device.config.t3__t3s.t3.create(f"{i}")
                device.config.t3__t3s.t3[f"{i}"].value = f"{i}-{run_id}"


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO.
# ---------------------------------------------
class CfsT3Application(ncs.application.Application):
    """Service appliction implementing service, validation, and action
    callbacks
    """
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('CfsT3Application RUNNING')

        # Register service handlers:
        self.register_service('cfs-devs-servicepoint', CfsDevsServiceCallbacks)
        self.register_service('cfs-t3-servicepoint', CfsT3ServiceCallbacks)

        # When we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NSO went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('CfsT3Application FINISHED')
