"""NSO DeviceAction Package example.

Implements an package with actions

See the README file for more information
"""
from __future__ import print_function
import ncs
import _ncs
import _ncs.dp
import socket
from ncs.dp import Action
from ncs.application import Application
from _namespaces.device_action_ns import ns


def recv_all_and_close(c_sock, c_id):
    data = ''
    while True:
        buf = c_sock.recv(4096)
        if buf:
            data += buf.decode('utf-8')
        else:
            c_sock.close()
            return data


def read_config(m, th, path):
    dev_flags = (_ncs.maapi.CONFIG_J +
                 _ncs.maapi.CONFIG_CDB_ONLY +
                 _ncs.maapi.CONFIG_WITH_OPER +
                 _ncs.maapi.CONFIG_UNHIDE_ALL +
                 _ncs.maapi.CONFIG_WITH_SERVICE_META)
    c_id = _ncs.maapi.save_config(m.msock, th, dev_flags, path)
    c_sock = socket.socket(m.msock.family)
    peername = m.msock.getpeername()
    if type(peername) is tuple:
        (ncsip, ncsport) = peername
        _ncs.stream_connect(c_sock, c_id, 0, ncsip, ncsport)
    else:
        _ncs.stream_connect(c_sock, c_id, 0, path=peername)
    data = recv_all_and_close(c_sock, c_id)
    return data


class DeviceActionHandler(Action):
    """This class implements the dp.Action class."""

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
        self.log.info("action(uinfo={0}, name={1})".format(uinfo.usid, name))
        if name == ns.da_extract_device_:
            dev_name = str(input.name)
            dev_del = str(input.delete)
            dev_path = "/ncs:devices/device{"+dev_name+"}"
            rfs_path = "/drfs:dRFS/device{"+dev_name+"}"
            self.log.info("device-action: extract device {0}".format(dev_name))
            with ncs.maapi.Maapi() as m:
                with ncs.maapi.Session(m, 'admin', 'system'):
                    with m.start_read_trans() as trans:
                        dev_config = read_config(m, trans.th, dev_path)
                        rfs_config = read_config(m, trans.th, rfs_path)
                    self.log.info("device-action: delete device {0}".
                                  format(dev_del))
                    if (dev_del == "True"):
                        with m.start_write_trans() as trans:
                            root = ncs.maagic.get_root(trans)
                            del root.devices.device[dev_name]
                            del root.dRFS.device[dev_name]
                            _ncs.maapi.apply_trans_flags(
                                m.msock, trans.th, False,
                                _ncs.maapi.COMMIT_NCS_NO_NETWORKING)
            output.config = dev_config+rfs_config

        elif name == ns.da_delete_device_:
            dev_name = str(input.name)
            dev_path = "/ncs:devices/device{"+dev_name+"}"
            rfs_path = "/drfs:dRFS/device{"+dev_name+"}"
            self.log.info("device-action: delete device {0}".format(dev_name))
            with ncs.maapi.Maapi() as m:
                with ncs.maapi.Session(m, 'admin', 'system'):
                    with m.start_write_trans() as trans:
                        root = ncs.maagic.get_root(trans)
                        del root.devices.device[dev_name]
                        del root.dRFS.device[dev_name]
                        _ncs.maapi.apply_trans_flags(
                            m.msock, trans.th, False,
                            _ncs.maapi.COMMIT_NCS_NO_NETWORKING)
            return _ncs.CONFD_OK

        elif name == ns.da_install_device_:
            dev_name = input.name
            dev_conf = input.config
            self.log.debug("action: install device {0} with {1}".
                           format(dev_name, dev_conf))
            with ncs.maapi.Maapi() as m:
                with ncs.maapi.Session(m, 'admin', 'system'):
                    with m.start_write_trans() as trans:
                        _ncs.maapi.load_config_cmds(
                            m.msock, trans.th,
                            (_ncs.maapi.CONFIG_J +
                             _ncs.maapi.CONFIG_UNHIDE_ALL +
                             _ncs.maapi.CONFIG_MERGE),
                            dev_conf, "")
                        root = ncs.maagic.get_root(trans)
                        root.ncs__devices.device[dev_name].state.\
                            admin_state = 'southbound-locked'
                        _ncs.maapi.apply_trans_flags(
                            m.msock, trans.th, False,
                            _ncs.maapi.COMMIT_NCS_NO_DEPLOY)
                    with m.start_write_trans() as trans:
                        root = ncs.maagic.get_root(trans)
                        root.ncs__devices.device[dev_name].state.\
                            admin_state = 'unlocked'
                        _ncs.maapi.apply_trans_flags(
                            m.msock, trans.th, False,
                            _ncs.maapi.COMMIT_NCS_NO_NETWORKING)
                    with m.start_read_trans() as trans:
                        root = ncs.maagic.get_root(trans)
                        root.devices.device[dev_name].compare_config()
        else:
            self.log.debug("got bad operation: {0}".format(name))
            return _ncs.CONFD_ERR

# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------


class DeviceAction(Application):
    """This class is referred to from the package-meta-data.xml."""

    def setup(self):
        """Setting up the action callback."""
        self.log.debug('action app start')
        self.register_action('device-action-point',
                             DeviceActionHandler, [])
