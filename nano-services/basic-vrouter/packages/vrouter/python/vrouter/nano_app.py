"""NSO Nano service example.

Implements a Nano service callback

See the README file for more information
"""
import ncs
from ncs.application import NanoService


# -----------------------------
# NANO SERVICE CALLBACK EXAMPLE
# -----------------------------
class NanoServiceCallbacks(NanoService):
    '''Nano service callbacks'''
    @NanoService.create
    def cb_nano_create(self, tctx, root, service, plan, component, state,
                       proplist, component_proplist):
        '''Nano service create callback'''
        self.log.info('Nano create(state=', state, ')')

        if state == 'vr:vm-requested':
            # Create and initialize the vrouter instance
            vmi = root.vm_instance.create(service.name)
            vmi.type = 'csr-small'

    # @NanoService.delete
    # def cb_nano_delete(self, tctx, root, service, plan, component, state,
    #                    proplist, component_proplist):


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class NanoApp(ncs.application.Application):
    '''Nano service appliction implementing the nano create callback'''
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('NanoApp RUNNING')

        # Nano service callbacks require a registration for a service point,
        # component, and state, as specified in the corresponding data model
        # and plan outline.
        self.register_nano_service('vrouter-servicepoint',  # Service point
                                   'vr:vrouter',            # Component
                                   'vr:vm-requested',       # State
                                   NanoServiceCallbacks)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('NanoApp FINISHED')
