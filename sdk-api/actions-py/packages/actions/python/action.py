"""NSO Action Package example.

Implements an package with actions

See the README file for more information
"""
import time
import ncs
from ncs.dp import Action
from ncs.application import Application


class RebootActionHandler(Action):
    """This class implements the dp.Action class."""
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        """Called when the actionpoint is invoked.

        The function is called with:
            uinfo -- a UserInfo object
            name -- the action name (string)
            kp -- the keypath of the action (HKeypathRef)
            ainput -- input node (maagic.Node)
            aoutput -- output node (maagic.Node)
            trans -- transaction
        """
        self.log.debug("action(uinfo={0}, name={1})".format(uinfo.usid, name))
        if name == "reboot":
            self.log.debug("action: reboot")
        elif name == "restart":
            mode = input.mode
            self.log.debug("action: restart mode={0}".format(mode))
            output.time = time.strftime("%H:%M:%S")
        elif name == "reset":
            server = ncs.maagic.get_node(trans, kp)
            self.log.debug(f"action: reset server={server.name}"
                           f" when={input.when}")
            output.time = time.strftime("%H:%M:%S")
        else:
            self.log.debug("got bad operation: {0}".format(name))
            return ncs.CONFD_ERR


class VerifyActionHandler(Action):
    """This class implements the dp.Action class."""
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        """Called when the verify actionpoint is invoked."""
        self.log.debug(f"action(uinfo={uinfo.usid}, name={name}, kp={kp})")
        system = ncs.maagic.get_node(trans, kp)
        if system.sys_name == "tst":
            output.consistent = True
        else:
            output.consistent = False


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class Action(Application):
    """This class is referred to from the package-meta-data.xml."""

    def setup(self):
        """Setting up the action callback."""
        self.log.debug('action app start')
        self.register_action('reboot-point', RebootActionHandler, [])
        self.register_action('verify-point', VerifyActionHandler, [])
