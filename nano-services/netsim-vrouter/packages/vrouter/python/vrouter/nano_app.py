"""NSO nano service example.

Implement Nano service callbacks and post-actions

See the README file for more information
"""
import os
import shutil
import re
import subprocess
from multiprocessing import Manager
import time
import ncs
from ncs.application import NanoService
from ncs.dp import Action
import _ncs
from _ncs import maapi as _maapi


# -----------------------------
# NANO SERVICE CALLBACK EXAMPLE
# -----------------------------
class NanoServiceCallbacks(NanoService):
    '''Nano service callbacks'''
    @staticmethod
    def netconf_notif_cfg(name, stream, user, tag):
        '''NETCONF notification configuration'''
        return """  <notifications>
    <subscription>
      <name>{0}</name>
      <stream>{1}</stream>
      <local-user>{2}</local-user>
    </subscription>
  </notifications>
  {3}""".format(name, stream, user, tag)

    @NanoService.create
    def cb_nano_create(self, tctx, root, service, plan, component, state,
                       proplist, component_proplist):
        '''Nano service create callback'''
        self.log.info("Nano create(state={} name={})".format(state,
                                                             service.name))
        if state == 'vr:onboarded':
            # Get the initialization config for the netsim vrouter from the
            # ncs-netsim tool and load it and the notification config into
            # NSO CDB
            result = subprocess.run(['ncs-netsim', 'ncs-xml-init',
                                     service.name],
                                    stdout=subprocess.PIPE, check=True,
                                    encoding='utf-8')
            device_xml = result.stdout
            device_xml = re.sub('</device>',
                                self.netconf_notif_cfg(
                                                      "Interface link status",
                                                      "iflink", "admin",
                                                      "</device>"), device_xml)
            try:
                trans = ncs.maagic.get_trans(root)
                maapi = ncs.maagic.get_maapi(root)
                _maapi.load_config_cmds(
                                       maapi.msock, trans.th,
                                       _maapi.CONFIG_XML | _maapi.CONFIG_MERGE,
                                       device_xml, "/")
            except (_ncs.error.Error) as exception:
                self.log.debug('Nano create failed(state=', state,
                               ' error=', exception, ')')
                service.plan.component[component].state[state].status = \
                    'failed'

    # @NanoService.delete
    # def cb_nano_delete(self, tctx, root, service, plan, component, state,
    #                     proplist, component_proplist):


# --------------------------------
# NANO SERVICE POST-ACTION EXAMPLE
# --------------------------------
class CreateActionHandler(Action):
    '''Create action handler'''
    def init(self, init_args):
        self.action_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput):
        '''Action callback'''
        name = str(keypath[0][0])
        self.log.info("Nano create action(name={})".format(name))
        with self.action_semaphore:
            self.log.info("Nano create LOCK(name={})".format(name))
            # Create and start a netsim vrouter
            if os.path.isfile('./netsim/.netsiminfo'):
                result = None
                try:
                    result = subprocess.run(['ncs-netsim', 'add-device',
                                             'packages/router-nc-1.1', name],
                                            stdout=subprocess.PIPE, check=True,
                                            encoding='utf-8')
                except subprocess.CalledProcessError:
                    pass
                if result is None or "DEVICE {} CREATED".format(name) not in \
                   result.stdout:
                    self.log.error("Failed add-device {}".format(name))
                    aoutput.result = False
                    return
                self.log.info(
                            "Nano create action add-device: {}".format(
                                result.stdout))
            else:
                result = None
                try:
                    result = subprocess.run(['ncs-netsim', 'create-device',
                                             'packages/router-nc-1.1', name],
                                            stdout=subprocess.PIPE, check=True,
                                            encoding='utf-8')
                except subprocess.CalledProcessError:
                    pass
                if result is None or "DEVICE {} CREATED".format(name) not in \
                   result.stdout:
                    self.log.error("Failed create-device {}".format(name))
                    aoutput.result = False
                    return
                self.log.info(
                         "Nano create action create-device: {}".format(
                            result.stdout))
            result = None
            try:
                result = subprocess.run(['ncs-netsim', 'start', name],
                                        stdout=subprocess.PIPE, check=True,
                                        encoding='utf-8')
            except subprocess.CalledProcessError:
                pass
            if result is None or "DEVICE {} OK STARTED".format(name) not in \
               result.stdout:
                self.log.error("Failed start device {}".format(name))
                aoutput.result = False
                return
            self.log.info(
                "Nano vrouter create action start: {}".format(result.stdout))
            try:
                result = subprocess.run(['./start-app.sh'],
                                        cwd="netsim/{0}/{0}".format(name),
                                        check=True, encoding='utf-8')
            except subprocess.CalledProcessError:
                self.log.error(
                             "Failed start device application {}".format(name))
                aoutput.result = False
                return
            self.log.info("Nano create UNLOCK(name={})".format(name))
        aoutput.result = True


class DeleteActionHandler(Action):
    '''Delete action handler'''
    def init(self, init_args):
        self.action_semaphore = init_args[0]

    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput):
        ''''Action callback'''
        name = str(keypath[0][0])
        while True:
            # Wait for the device to be started before removing it incase
            # this delete action is called just before the create action
            # for the same device is called.
            if os.path.isfile('./netsim/.netsiminfo'):
                result = None
                try:
                    result = subprocess.run(['ncs-netsim', 'list'],
                                            stdout=subprocess.PIPE, check=True,
                                            encoding='utf-8')
                except subprocess.CalledProcessError:
                    self.log.info("Failed list devices {}".format(name))
                if result is not None and name in result.stdout:
                    try:
                        result = subprocess.run(['ncs-netsim', 'is-alive',
                                                 name], stdout=subprocess.PIPE,
                                                check=True, encoding='utf-8')
                    except subprocess.CalledProcessError:
                        self.log.info(
                                     "Waiting for the device to be created...")
                    else:
                        if result is not None and "DEVICE {} OK".format(name) \
                           in result.stdout:
                            break
            self.log.info(
                   "Wait for {} to be started before removing it".format(name))
            time.sleep(.5)
        self.log.info("Nano delete action(name={})".format(name))
        with self.action_semaphore:
            self.log.info("Nano delete LOCK(name={})".format(name))
            # Stop and delete the netsim vrouter
            result = None
            try:
                result = subprocess.run(['ncs-netsim', 'stop', name],
                                        stdout=subprocess.PIPE, check=True,
                                        encoding='utf-8')
            except subprocess.CalledProcessError:
                pass
            if result is None or "DEVICE {} STOPPED".format(name) not in \
               result.stdout:
                self.log.error("Failed stop device {}".format(name))
                aoutput.result = False
                return
            shutil.rmtree("./netsim/{}".format(name), ignore_errors=True)
            with open('./netsim/.netsiminfo', encoding='utf-8') as file:
                nsistr = file.read()
            pstr = "\n## device {}.*?#######\n\n".format(name)
            nsistr = re.sub(pstr, '', nsistr, flags=re.DOTALL)
            if not nsistr:
                shutil.rmtree("./netsim", ignore_errors=True)
            else:
                lines = nsistr.splitlines(True)
                with open('./netsim/.netsiminfo', 'w',
                          encoding='utf-8') as file:
                    i = 0
                    tmpstr = "[{}]".format(i)
                    for line in lines:
                        line = re.sub(r'\[.*?\]', tmpstr, line)
                        file.write(line)
                        if line == "#######\n":
                            i += 1
                            tmpstr = "[{}]".format(i)
            self.log.info("Nano delete UNLOCK(name={})".format(name))
        aoutput.result = True


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO.
# ---------------------------------------------
class NanoApp(ncs.application.Application):
    '''Nano service appliction implementing nano create and action callbacks'''
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('NanoApp RUNNING')

        # Nano service callbacks require a registration for a service point,
        # component, and state, as specified in the corresponding data model
        # and plan outline.
        self.register_nano_service('vrouter-servicepoint',  # Service point
                                   'vr:vrouter',            # Component
                                   'vr:onboarded',          # State
                                   NanoServiceCallbacks)

        manager = Manager()
        action_semaphore = manager.Semaphore()
        init_args = [action_semaphore]
        self.register_action('create-vrouter', CreateActionHandler, init_args)
        self.register_action('delete-vrouter', DeleteActionHandler, init_args)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NSO went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('NanoApp FINISHED')
