"""NSO Cowlog Action Package example.

Implements a package with an action that logs a message using cowsay.

See the README file for more information
"""
import cowsay
import ncs


class CowlogActionHandler(ncs.dp.Action):
    """This class implements the dp.Action class."""
    @ncs.dp.Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        """Called when the cowlog actionpoint is invoked."""
        self.log.info(f"action(uinfo={uinfo.usid}, name={name}, kp={kp})")
        cowlog = cowsay.get_output_string(char='cow',
                                          text='Hello from cowlog action!')
        self.log.info(f'\n{cowlog}\n')


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class Action(ncs.application.Application):
    """This class is referred to from the package-meta-data.xml."""

    def setup(self):
        """Setting up the action callback."""
        self.log.info('Action RUNNING')
        self.register_action('cowlog-point', CowlogActionHandler, [])

    def teardown(self):
        self.log.info('Action FINISHED')
