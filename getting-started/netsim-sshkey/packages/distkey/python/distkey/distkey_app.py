"""Nano service example that implements nano service callbacks and post-actions
to generate SSH keys, distribute the public key to network elements,
configure NSO to use those keys for authentication with the network elements,
and test the public key authentication.

See the README file for more information.
"""
import errno
import os
import subprocess
from multiprocessing import Manager
import ncs
from ncs.application import NanoService
from ncs.dp import Action
import _ncs
from _ncs import maapi as _maapi


# -----------------------------
# NANO SERVICE CALLBACK EXAMPLE
# -----------------------------
class DistKeyServiceCallbacks(NanoService):
    '''Nano service callbacks'''
    @NanoService.create
    def cb_nano_create(self, tctx, root, service, plan, component, state,
                       proplist, component_proplist):
        '''Nano service create callback'''
        self.log.info(f'Nano create(state={state} ne-name={service.ne_name}'
                      f' local-user={service.local_user}'
                      f' remote-name={service.remote_name}')
        if state == 'dk:distributed':
            # Distribute the public key to the network element's authorized
            # keys list
            self.log.info(f'Distribute the pubkey (ne-name={service.ne_name},'
                          f'local-user={service.local_user},'
                          f'remote-name={service.remote_name})')
            with open(f'./{service.local_user}_ed25519.pub', 'r',
                      encoding='utf-8') as file:
                pubkey_data = file.read()
                config = root.devices.device[service.ne_name].config
                users = config.aaa.authentication.users
                users.user[service.remote_name].authkey.create(pubkey_data)
            self.log.info(f'Done distribute the pubkey'
                          f' (ne-name={service.ne_name},'
                          f'local-user={service.local_user},'
                          f'remote-name={service.remote_name})')
        elif state == 'dk:configured':
            # Configure NSO to use a public key for authentication with the
            # network element
            template_vars = ncs.template.Variables()
            template_vars.add('CWD', os.getcwd())
            template = ncs.template.Template(service)
            self.log.info(f'Apply template (ne-name={service.ne_name},'
                          f'local-user={service.local_user},'
                          f'remote-name={service.remote_name})')
            template.apply('distkey-configured', template_vars)
            self.log.info(f'Done template (ne-name={service.ne_name},'
                          f'local-user={service.local_user},'
                          f'remote-name={service.remote_name})')

    # @NanoService.delete
    # def cb_nano_delete(self, tctx, root, service, plan, component, state,
    #                    proplist, component_proplist):
    #     '''Nano service delete callback'''
    #     self.log.info(f'Nano delete(state={state} ne-name={service.ne_name}'
    #                   f' local-user={service.local_user})')


# --------------------------------
# NANO SERVICE POST-ACTION EXAMPLE
# --------------------------------
class GenerateActionHandler(Action):
    '''Generate action handler'''
    def init(self, init_args):
        self.action_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, input, output, trans):
        '''Action callback'''
        service = ncs.maagic.get_node(trans, keypath)
        self.log.info(f'Nano generate action(ne-name={service.ne_name},'
                      f' local-user={service.local_user})')
        # Install the crypto keys used to decrypt the service passphrase leaf
        # as input to the key generation.
        with ncs.maapi.Maapi() as maapi:
            _maapi.install_crypto_keys(maapi.msock)
        # Decrypt the passphrase leaf for use when generating the keys
        encrypted_passphrase = service.passphrase
        decrypted_passphrase = _ncs.decrypt(str(encrypted_passphrase))
        output.result = True
        # Semaphore for generating one private/public key pair at a time to
        # avoid concurrent re-deploys to cause a mismatch between the public
        # and private keys.
        with self.action_semaphore:
            # If they do not exist already, generate the private and public
            # keys
            if not os.path.isfile(f'./{service.local_user}_ed25519'):
                result = subprocess.run(['ssh-keygen', '-N',
                                         f'{decrypted_passphrase}', '-t',
                                         'ed25519', '-f',
                                         f'./{service.local_user}_ed25519'],
                                        stdout=subprocess.PIPE, check=True,
                                        encoding='utf-8')
                if "has been saved" not in result.stdout:
                    output.result = False


class DeleteActionHandler(Action):
    '''Delete action handler'''
    @Action.action
    def cb_action(self, uinfo, name, keypath, input, output, trans):
        '''Action callback'''
        service = ncs.maagic.get_node(trans, keypath)
        self.log.info(f'Nano delete action(ne-name={service.ne_name},'
                      f' local-user={service.local_user})')
        # Only delete the key files if no more network elements use this
        # users keys
        cur = trans.cursor('/pubkey-dist/key-auth')
        remove_key = True
        while True:
            try:
                value = next(cur)
                self.log.info(f'ne-name={value[0]} local-user={value[1]}')
                if value[0] != service.ne_name and \
                   value[1] == service.local_user:
                    remove_key = False
                    break
            except StopIteration:
                break
        output.result = True
        if remove_key is True:
            try:
                os.remove(f'./{service.local_user}_ed25519.pub')
                os.remove(f'./{service.local_user}_ed25519')
            except OSError as err:
                if err.errno != errno.ENOENT:
                    output.result = False


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO.
# ---------------------------------------------
class DistKeyApp(ncs.application.Application):
    '''Nano service appliction implementing nano create and action callbacks'''
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('DistKeyApp RUNNING')

        # Nano service callbacks require a registration for a service point,
        # component, and state, as specified in the corresponding data model
        # and plan outline.
        self.register_nano_service('distkey-servicepoint',  # Service point
                                   'dk:ne',                 # Component
                                   'dk:distributed',        # State
                                   DistKeyServiceCallbacks)
        self.register_nano_service('distkey-servicepoint',  # Service point
                                   'dk:ne',                 # Component
                                   'dk:configured',         # State
                                   DistKeyServiceCallbacks)

        # Semaphore to be used with the generate action handler
        manager = Manager()
        action_semaphore = manager.Semaphore()
        init_args = [action_semaphore]

        # Side effect action that uses ssh-keygen to create the key files
        self.register_action('generate-keys', GenerateActionHandler, init_args)
        # Action to delete the keys created by the generate keys action
        self.register_action('delete-keys', DeleteActionHandler)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NSO went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('DistKeyApp FINISHED')
