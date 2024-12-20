"""NCS MoveDevice Package

Implements an package with actions

See the README file for more information
"""
from __future__ import print_function
import math
import ncs
import _ncs
import _ncs.dp
import socket
from ncs.dp import Action
from ncs.application import Application
from _namespaces.move_device_ns import ns


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
                 _ncs.maapi.CONFIG_UNHIDE_ALL +
                 _ncs.maapi.CONFIG_WITH_SERVICE_META)
    c_id = _ncs.maapi.save_config(m.msock, th, dev_flags, path)
    peername = m.msock.getpeername()
    if type(peername) is tuple:
        c_sock = socket.socket()
        (ncsip, ncsport) = peername
        _ncs.stream_connect(c_sock, c_id, 0, ncsip, ncsport)
    else:
        c_sock = socket.socket(family=socket.AF_UNIX)
        _ncs.stream_connect(c_sock, c_id, 0, path=peername)
    data = recv_all_and_close(c_sock, c_id)
    return data


class MoveDeviceHandler(Action):
    """This class implements the dp.Action class."""

    def __move_device(self, src_nso, dest_nso, dev_name, read_from_db):
        src_xpath = ("/ncs:devices/device[name='"+src_nso+"']" +
                     "/config/dRFS/device[name='"+dev_name+"']")
        src_path = ("/ncs:devices/device{"+src_nso+"}" +
                    "/config/dRFS/device{"+dev_name+"}")
        self.log.info("move-device: move {0} from {1} to "
                      "{2} (read from db {3}".format(dev_name,
                                                     src_nso,
                                                     dest_nso,
                                                     read_from_db))
        with ncs.maapi.Maapi() as m:
            with ncs.maapi.Session(m, 'admin', 'system'):
                # grab partial lock of dRFS instance
                self.log.info("partial lock for {0}".format(src_xpath))
                try:
                    lockid = _ncs.maapi.lock_partial(m.msock, _ncs.RUNNING,
                                                     [src_xpath])
                except Exception:
                    lockid = -1

                self.log.info("move-device: partial lock id: {0}".
                              format(lockid))

                # extract device config
                root = ncs.maagic.get_root(m)
                if (read_from_db):
                    with open("../db_store/"+dev_name+".cfg",
                              'r') as content_file:
                        dev_config = content_file.read()
                else:
                    # extract device and dRFS from source NSO
                    edin = root.devices.device[src_nso].live_status.\
                            device_action.extract_device.get_input()
                    edin.name = dev_name
                    edin.delete = False
                    edout = root.devices.device[src_nso].live_status.\
                        device_action.extract_device(edin)
                    dev_config = edout.config

                self.log.info("move-device: got config {0}".
                              format(dev_config))

                # install on target NSO
                idin = root.devices.device[dest_nso].live_status.\
                    device_action.install_device.get_input()
                idin.name = dev_name
                idin.config = dev_config
                root.devices.device[dest_nso].\
                    live_status.device_action.\
                    install_device(idin)

                # delete from source NSO
                ddin = root.devices.device[src_nso].live_status.\
                    device_action.delete_device.get_input()
                ddin.name = dev_name
                root.devices.device[src_nso].live_status.device_action.\
                    delete_device(ddin)

                # update mapping table
                with m.start_write_trans() as trans:
                    root = ncs.maagic.get_root(trans)
                    root.dispatch_map[dev_name].rfs_node = dest_nso
                    _ncs.maapi.apply_trans_flags(
                        m.msock, trans.th, False,
                        _ncs.maapi.COMMIT_NCS_NO_NETWORKING)

                # release partial lock
                if (lockid >= 0):
                    _ncs.maapi.unlock_partial(m.msock, lockid)

                # redeploy services that uses device
                with m.start_write_trans() as trans:
                    try:
                        backp = _ncs.maapi.get_attrs(
                            m.msock, trans.th, [ncs.ATTR_BACKPOINTER],
                            src_path)
                    except Exception:
                        backp = []

                    for x in backp:
                        for y in x.v:
                            strpath = _ncs.maapi.xpath2kpath(m.msock, str(y))
                            service = ncs.maagic.get_node(trans, strpath)
                            self.log.info("redeploying {0}".format(strpath))
                            sin = service.re_deploy.get_input()
                            sin.no_networking.create()
                            sin.no_lsa.create()
                            service.re_deploy(sin)

                # update transaction ids
                with m.start_read_trans() as trans:
                    root = ncs.maagic.get_root(trans)
                    root.devices.device[dest_nso].compare_config()
                    root.devices.device[src_nso].compare_config()

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
        if name == ns.md_move_:
            src_nso = str(input.src_nso)
            from_db = input.read_from_db
            dest_nso = str(input.dest_nso)
            dev_name = str(input.device_name)
            self.__move_device(src_nso, dest_nso, dev_name, from_db)

        elif name == ns.md_rebalance_:
            dryrun = input.dry_run.exists()
            from_db = input.read_from_db.exists()

            nodes = {}
            for node in input.nodes:
                nodes[str(node)] = []

            self.log.info("rebalance: nodes={0}".format(nodes))

            with ncs.maapi.Maapi() as m:
                with ncs.maapi.Session(m, 'admin', 'system'):
                    with m.start_read_trans() as trans:
                        root = ncs.maagic.get_root(trans)

                        # If no nodes specified, look for lsa nodes
                        # among devices
                        if len(nodes) == 0:
                            for node in root.devices.device.keys():
                                xres = _ncs.maapi.xpath_eval_expr(
                                    m.msock, trans.th,
                                    'derived-from-or-self' +
                                    '(/ncs:devices/device[name="' +
                                    str(node[0]) + '"]' +
                                    '/device-type/netconf/ned-id,' +
                                    ' "ned:lsa-netconf")', None, '')
                                if (xres == "true"):
                                    nodes[str(node[0])] = []

                        dev_count = 0
                        # First figure out the number of devices and where
                        # they are
                        for key in root.dispatch_map.keys():
                            lsa_node = str(root.dispatch_map[key].rfs_node)
                            # we are only interested in devices that belong
                            # to a node in the set of nodes we are re-balancing
                            if lsa_node not in nodes:
                                continue
                            dev_count += 1
                            dev = str(key[0])
                            nodes[lsa_node].append(dev)

                        # Calculate the optimal number of devices per node
                        max_on_dev = math.trunc(
                            math.ceil(dev_count/len(nodes)))

                        # Find devices that needs to be moved
                        devs_to_move = []
                        for lsa_node in sorted(nodes.keys()):
                            self.log.info("rebalance: node {0} has {1} devs".
                                          format(lsa_node,
                                                 len(nodes[lsa_node])))
                            while len(nodes[lsa_node]) > max_on_dev:
                                dev = nodes[lsa_node].pop()
                                devs_to_move.append((lsa_node, dev))

                        # Find nodes that have room for the devices
                        move_list = []
                        for lsa_node in sorted(nodes.keys()):
                            while (len(nodes[lsa_node]) < max_on_dev and
                                   len(devs_to_move) > 0):
                                (src_node, dev) = devs_to_move.pop()
                                nodes[lsa_node].append(dev)
                                move_list.append((src_node, lsa_node, dev))

                        if len(devs_to_move) > 0:
                            self.log.info(
                                "rebalance: error, no place for nodes: {0}".
                                format(devs_to_move))

                        # Finally move the devices
                        result = []
                        for m in sorted(move_list, key=lambda x: x[2]):
                            (src_node, dest_node, dev) = m
                            result.append("move %s from %s to %s" %
                                          (dev, src_node, dest_node))
                            if not dryrun:
                                self.__move_device(src_node, dest_node, dev,
                                                   from_db)
                        if len(result) == 0:
                            output.result = "in balance"
                        else:
                            output.result = '\n'.join(result)
        elif name == ns.md_evacuate_:
            dryrun = input.dry_run.exists()
            from_db = input.read_from_db.exists()
            evacuate_node = str(input.node)

            dest_nodes = {}
            for node in input.dest_nodes:
                dest_nodes[str(node)] = []

            with ncs.maapi.Maapi() as m:
                with ncs.maapi.Session(m, 'admin', 'system'):
                    with m.start_read_trans() as trans:
                        root = ncs.maagic.get_root(trans)

                        # If no nodes specified, look for lsa nodes
                        # among devices
                        if len(dest_nodes) == 0:
                            for node in root.devices.device.keys():
                                xres = _ncs.maapi.xpath_eval_expr(
                                    m.msock, trans.th,
                                    'derived-from-or-self' +
                                    '(/ncs:devices/device[name="' +
                                    str(node[0]) + '"]' +
                                    '/device-type/netconf/ned-id,' +
                                    ' "ned:lsa-netconf")', None, '')
                                if (xres == "true"):
                                    dest_nodes[str(node[0])] = []

                        # remove nso node if included in dest_nodes
                        if evacuate_node in dest_nodes:
                            del dest_nodes[evacuate_node]

                        dev_count = 0
                        devs_to_move = []
                        # First figure out the number of devices, where
                        # they are, and which devices to move
                        for key in root.dispatch_map.keys():
                            lsa_node = str(root.dispatch_map[key].rfs_node)
                            dev = str(key[0])
                            if lsa_node == evacuate_node:
                                devs_to_move.append(dev)
                                dev_count += 1
                            elif lsa_node in dest_nodes:
                                dest_nodes[lsa_node].append(dev)
                                dev_count += 1

                        # Calculate the optimal number of devices per node
                        if len(dest_nodes) > 0:
                            max_on_dev = math.trunc(
                                math.ceil(dev_count/(len(dest_nodes))))
                        else:
                            max_on_dev = dev_count

                        self.log.info("dest_nodes={0}".format(dest_nodes))
                        self.log.info(
                            "evacuate_node={0}".format(evacuate_node))

                        # Find nodes that have room for the devices
                        move_list = []
                        for lsa_node in sorted(dest_nodes.keys()):
                            while (len(dest_nodes[lsa_node]) < max_on_dev and
                                   len(devs_to_move) > 0):
                                dev = devs_to_move.pop()
                                dest_nodes[lsa_node].append(dev)
                                move_list.append((
                                    evacuate_node, lsa_node, dev))

                        if len(devs_to_move) > 0:
                            self.log.info(
                                "rebalance: error, no place for nodes: {0}".
                                format(devs_to_move))

                        # Finally move the devices
                        result = []
                        for m in sorted(move_list, key=lambda x: x[2]):
                            (src_node, dest_node, dev) = m
                            result.append("move %s from %s to %s" %
                                          (dev, src_node, dest_node))
                            if not dryrun:
                                self.__move_device(src_node, dest_node, dev,
                                                   from_db)
                        for dev in devs_to_move:
                            result.append(
                                "failed to find destination node for %s" % dev)

                        if len(dest_nodes) == 0:
                            output.result = "no destination nodes found"
                        elif len(result) == 0:
                            output.result = "node already evacuated"
                        else:
                            output.result = '\n'.join(result)
        else:
            self.log.debug("got bad operation: {0}".format(name))
            return _ncs.CONFD_ERR

# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------


class MoveDevice(Application):
    """This class is referred to from the package-meta-data.xml."""

    def setup(self):
        """Setting up the action callback."""
        self.log.debug('action app start')
        self.register_action('move-device-point', MoveDeviceHandler, [])
