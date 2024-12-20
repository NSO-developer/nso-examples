"""IETF Hardware NMDA system controller example
See the README file for more information
"""
from datetime import datetime
import logging
import multiprocessing
from multiprocessing import Process
import sys
import ncs as tm
import _ncs as _tm

deleted = []


class ComponentDeletedIter:
    """Iterate deleted component list entries in config"""
    def __init__(self, log, name='component-deletion'):
        """Init"""
        self.log = log

    def iterate(self, kp, op, oldv, newv, state):
        """Handle deleted component list entries"""
        self.log.info(f'iterate deleted kp={kp} op={op} oldv={oldv}'
                      f' newv={newv}')
        if op is tm.MOP_DELETED:
            c_name = kp[0][0]
            deleted.append(str(c_name))


class ComponentIter:
    """Iterate component configuration changes"""
    def __init__(self, log, port, subsock, name='component-changes'):
        """Init"""
        self.log = log
        self.name = name
        self.port = port
        self.subsock = subsock

    def pre_iterate(self):
        """Get any deleted components to the iter function state and clear the
        global list for the next transaction.
        """
        d = deleted.copy()
        deleted.clear()
        return [d]

    def iterate(self, kp, op, oldv, newv, state):
        """Apply the intended configuration to the operational state. I.e.,
        simulate that we actually implemented the configuration changes with
        the system controller hardware.
        """
        self.log.info(f'iterate changes kp={kp} op={op} oldv={oldv}'
                      f' newv={newv}')
        if op is tm.MOP_CREATED or op is tm.MOP_MODIFIED or \
           op is tm.MOP_VALUE_SET:
            c_name = kp[0][0]
            c_class = None
            c_parent = None
            c_parent_rel_pos = -1
            with tm.maapi.single_read_trans('admin', 'sys-read-cfg',
                                            port=self.port, path=None) as t:
                root = tm.maagic.get_root(t)
                c_class = root.hw__hardware.component[c_name].hw__class
                c_parent = root.hw__hardware.component[c_name].parent
                c_parent_rel_pos = \
                    root.hw__hardware.component[c_name].parent_rel_pos
            self.log.info(f'c_class {c_class} c_parent {c_parent}'
                          f' c_parent_rel_pos {c_parent_rel_pos}')
            found = False
            oldname = ""
            with tm.maapi.single_read_trans('admin', 'sys-read-oper',
                                            port=self.port, path=None,
                                            db=tm.OPERATIONAL) as t:
                root = tm.maagic.get_root(t)
                for component in root.hw__hardware.component:
                    if "Tail-f System" in component.description and \
                       component.hw__class == c_class and \
                       (component.parent == c_parent or component.parent in
                        state[0]) and \
                       component.parent_rel_pos == c_parent_rel_pos:
                        self.log.info('Found!')
                        found = True
                        oldname = component.name
                        break
            if found:
                with tm.maapi.single_write_trans('admin', 'sys-write-oper',
                                                 port=self.port, path=None,
                                                 db=tm.OPERATIONAL) as t:
                    if oldname != c_name:
                        self.log.info(f'Move the list entry from {oldname}'
                                      f' to {c_name}')
                        t.move([c_name],
                               f'/hw:hardware/hw:component{{{oldname}}}')
                        state[0].append(oldname)
                    tvs = _tm.cdb.get_modifications_iter(
                        self.subsock,
                        tm.cdb.GET_MODS_INCLUDE_LISTS)
                    t.set_values(tvs, str(kp))
                    t.apply()
        return tm.ITER_CONTINUE


class LastChangeIter:
    """Iterate component changes"""
    def __init__(self, port, name='last-change'):
        """Init"""
        self.name = name
        self.port = port
        d = tm.dp.Daemon("change-notifier", port=port, path=None)
        self.nctx = \
            _tm.dp.register_notification_stream(
                d.ctx(),
                None,
                tm.dp.take_worker_socket(d,
                                         'chnotif',
                                         'chnotif-key'),
                'hardware_state')
        d.start()

    def update_last_change(self, dt):
        with tm.maapi.single_write_trans('admin', 'sys-last-change',
                                         port=self.port, path=None,
                                         db=tm.OPERATIONAL) as t:
            t.set_elem(dt, '/hw:hardware/last-change')
            t.apply()

    def pre_iterate(self):
        """Set the initial state"""
        return [True]

    def iterate(self, kp, op, oldv, newv, state):
        """Set the last-change leaf if the oper state changed and send a
           hardware-state-change NETCONF/RESTCONF notification
        """
        # print(f'\niterate last change kp={kp} op={op} oldv={oldv}
        #       newv={newv}\n', flush=True)
        if state[0] is True:
            dt = datetime.utcnow().isoformat()
            p = Process(target=self.update_last_change, args=(dt,))
            p.start()
            now = datetime.now()
            tmnow = tm.DateTime(
                year=now.year,
                month=now.month,
                day=now.day,
                hour=now.hour,
                min=now.minute,
                sec=now.second,
                micro=now.microsecond,
                timezone=0,
                timezone_minutes=0)
            cs = _tm.cs_node_cd(None, "/hardware-state-change")
            ns = cs.ns()
            tag = cs.tag()
            scs = _tm.cs_node_cd(None, "/hardware-state-change/last-change")
            sns = scs.ns()
            stag = scs.tag()
            tvs = [
                tm.TagValue(xmltag=tm.XmlTag(ns, tag),
                            v=tm.Value((tag, ns), tm.C_XMLBEGIN)),
                tm.TagValue(xmltag=tm.XmlTag(sns, stag),
                            v=tm.Value(dt, tm.C_DATETIME)),
                tm.TagValue(xmltag=tm.XmlTag(ns, tag),
                            v=tm.Value((tag, ns), tm.C_XMLEND))
            ]
            _tm.dp.notification_send(self.nctx, tmnow, tvs)
            state[0] = False
        return tm.ITER_CONTINUE


def change_notif_sub(port):
    """Subscribe to last-change leaf changes"""
    with tm.maapi.Maapi(port=port, path=None) as m:
        m.wait_start(2)
    sub = tm.cdb.OperSubscriber(port=port, path=None)
    sub.register('/hw:hardware/component',
                 LastChangeIter(port), subtype=tm.cdb.SUB_OPERATIONAL)
    sub.start()


def main(port):
    """System controller"""
    log = tm.log.Log(logging.getLogger(__name__))
    with tm.maapi.Maapi(port=port, path=None) as m:
        m.start_phase(1, 1)
    multiprocessing.set_start_method('fork')
    p = Process(target=change_notif_sub, args=(port,))
    p.start()
    sub = tm.cdb.Subscriber(log=log, port=port, path=None)
    sub.register('/hw:hardware/component',
                 ComponentDeletedIter(log),
                 subtype=tm.cdb.SUB_RUNNING)
    sub.register('/hw:hardware/component',
                 ComponentIter(log, port, sub.sock),
                 priority=1,
                 subtype=tm.cdb.SUB_RUNNING)
    sub.start()


if __name__ == '__main__':
    logging.basicConfig(
        level=logging.INFO,
        filename='system-controller.log',
        format='%(asctime)s %(levelname)-8s %(message)s')
    main(int(sys.argv[1]))
