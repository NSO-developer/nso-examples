"""IETF Hardware NMDA card example
See the README file for more information
"""
import argparse
from datetime import datetime
import logging
import multiprocessing
from multiprocessing import Process
import socket
import time
import ncs as tm
import _ncs as _tm

card_serial_number = []  # Used to identiy the card components
deleted = []


class CardDeletedIter:
    """Iterate deleted card/port list entry in config"""
    def __init__(self, log, name='card-deletion'):
        """Init"""
        self.log = log

    def iterate(self, kp, op, oldv, newv, state):
        """Handle deleted card list entry"""
        self.log.info(f'iterate deleted kp={kp} op={op} oldv={oldv}'
                      ' newv={newv}')
        if op is tm.MOP_DELETED:
            c_name = kp[0][0]
            deleted.append(str(c_name))
        return tm.ITER_CONTINUE


class CardIter:
    """Iterate card configuration changes"""
    def __init__(self, log, port, subsock, name='card-changes'):
        """Init"""
        self.log = log
        self.name = name
        self.port = port
        self.subsock = subsock
        # multiprocessing.set_start_method('fork')

    def pre_iterate(self):
        """Get a deleted card to the iter function state and clear the
        global list for the next transaction.
        """
        d = deleted.copy()
        deleted.clear()
        return d

    def apply_operstate_changes(self, t):
        t.apply()

    def iterate(self, kp, op, oldv, newv, state):
        """Apply the intended configuration to the operational state. I.e.,
        simulate that we actually implemented the configuration changes with
        the card hardware.
        """
        self.log.info(f'iterate changes kp={kp} op={op} oldv={oldv}'
                      f' newv={newv}')
        if op is tm.MOP_CREATED or op is tm.MOP_MODIFIED or \
           op is tm.MOP_VALUE_SET:
            c_name = kp[0][0]
            c_class = None
            c_parent = None
            c_parent_rel_pos = -1
            with tm.maapi.single_read_trans('admin', 'card-read-cfg',
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
            with tm.maapi.single_read_trans('admin', 'card-read-oper',
                                            port=self.port, path=None,
                                            db=tm.OPERATIONAL) as t:
                root = tm.maagic.get_root(t)
                for component in root.hw__hardware.component:

                    if card_serial_number[0] in component.description and \
                       component.hw__class == c_class and \
                       (component.parent == c_parent or component.parent in
                        state) and \
                       component.parent_rel_pos == c_parent_rel_pos:
                        self.log.info('Found!')
                        found = True
                        oldname = component.name
                        break
            if found:
                with tm.maapi.single_write_trans('admin', 'card-write-oper',
                                                 port=self.port, path=None,
                                                 db=tm.OPERATIONAL) as t:
                    # m = tm.maapi.Maapi(port=self.port)
                    # m.start_user_session('admin', 'card-write-oper')
                    # t = m.start_write_trans(db=tm.OPERATIONAL)
                    if oldname != c_name:
                        self.log.info(f'Move the list entry from {oldname} to'
                                      f' {c_name}')
                        t.move([c_name],
                               f'/hw:hardware/hw:component{{{oldname}}}')
                        state.append(oldname)
                    tvs = _tm.cdb.get_modifications_iter(
                        self.subsock,
                        tm.cdb.GET_MODS_INCLUDE_LISTS)
                    t.set_values(tvs, str(kp))
                    origin_ns = _tm.str2hash("urn:ietf:params:xml:ns:yang"
                                             ":ietf-origin")
                    origin_system = _tm.str2hash("intended")
                    t.set_attr(tm.ATTR_ORIGIN,
                               tm.Value((origin_ns, origin_system),
                                        tm.C_IDENTITYREF),
                               f'/hw:hardware/hw:component{{{c_name}}}')
                    t.apply()
                # p = Process(target=self.apply_operstate_changes, args=(t,))
                # p.start()
        return tm.ITER_CONTINUE


def register_card(confd_port, card_name, firmware_rev, serial_number, mfg_name,
                  slot_name, port_name, port, slot_rel_pos, card_rel_pos):
    with tm.maapi.single_write_trans('admin', 'system', port=confd_port,
                                     path=None, db=tm.OPERATIONAL) as t:
        root = tm.maagic.get_root(t)
        component = root.hw__hardware.component
        # Register card
        component.create(card_name)
        component[card_name].hw__class = 'ianahw:module'
        component[card_name].description = f'{mfg_name} card {serial_number}'
        component[card_name].parent = slot_name
        component[card_name].parent_rel_pos = slot_rel_pos
        component[card_name].firmware_rev = firmware_rev
        component[card_name].serial_num = serial_number
        component[card_name].mfg_name = mfg_name
        origin_ns = _tm.str2hash("urn:ietf:params:xml:ns:yang:ietf-origin")
        origin_system = _tm.str2hash("system")
        t.set_attr(tm.ATTR_ORIGIN,
                   tm.Value((origin_ns, origin_system), tm.C_IDENTITYREF),
                   f'/hw:hardware/hw:component{{{card_name}}}')
        # Register card port
        if port > -1:
            component.create(port_name)
            component[port_name].hw__class = 'ianahw:port'
            component[port_name].description = \
                f'{mfg_name} card {serial_number} port'
            component[port_name].parent = card_name
            component[port_name].parent_rel_pos = card_rel_pos
            component[port_name].mfg_name = mfg_name
            t.set_attr(tm.ATTR_ORIGIN,
                       tm.Value((origin_ns, origin_system), tm.C_IDENTITYREF),
                       f'/hw:hardware/hw:component{{{port_name}}}')
        # Set the global variable for the card's serial number
        card_serial_number.append(serial_number)
        # Set the last-change leaf
        now = datetime.utcnow().isoformat()
        t.set_elem(now, '/hw:hardware/last-change')
        t.apply()


def trigger_subscription(card_name, port, sub_points):
    with tm.maapi.Maapi(port=port, path=None) as m:
        with tm.maapi.Session(m, 'admin', 'update-oper-state'):
            # Must take a CDB global lock to avoid that the synthetic trigger
            # of the subscriber conflict with an ongoing transaction
            while True:
                try:
                    m.lock(tm.CANDIDATE)
                except (tm.error.Error):
                    # print(f'CDB running locked. Retry in 1s', flush=True)
                    time.sleep(1)
                    continue
                break
            # print(f'Trigger sub points {str(sub_points)}', flush=True)
            sock = socket.socket()
            _tm.cdb.connect_name(sock, tm.cdb.DATA_SOCKET, card_name,
                                 '127.0.0.1', port, None)
            _tm.cdb.trigger_subscriptions(sock, sub_points)
            sock.close()
            m.unlock(tm.CANDIDATE)


def main(args):
    """Card"""
    log = tm.log.Log(logging.getLogger(__name__))
    # Create slot and port identification
    slot_name = 'slot-{}-{}-{}'.format(args.rack, args.subrack, args.slot)
    if args.port > -1:
        port_name = '{}-port-{}'.format(args.card_name, args.port)
    else:
        port_name = None
    slot_rel_pos = 1000000 * args.rack + 10000 * args.subrack + 100 * args.slot
    card_rel_pos = 1000000 * args.rack + 10000 * args.subrack + 100 * \
        args.slot + args.port
    register_card(args.confd_port, args.card_name, args.firmware_rev,
                  args.serial_number, args.mfg_name, slot_name, port_name,
                  args.port, slot_rel_pos, card_rel_pos)
    sub = tm.cdb.Subscriber(log=log, port=args.confd_port, path=None)
    sub_points = []
    sub_points.append(sub.register('/hw:hardware/component',
                      CardDeletedIter(log),
                      subtype=tm.cdb.SUB_RUNNING))
    sub_points.append(sub.register('/hw:hardware/component',
                      CardIter(log, args.confd_port, sub.sock),
                      priority=1,
                      subtype=tm.cdb.SUB_RUNNING))
    if args.start:
        with tm.maapi.Maapi(port=args.confd_port, path=None) as m:
            m.start_phase(2, 1)
    else:
        multiprocessing.set_start_method('fork')
        p = Process(target=trigger_subscription,
                    args=(args.card_name, args.confd_port, sub_points))
        p.start()
    sub.start()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument('-c', '--card_name', default='dummycard',
                        help='Card name. Default: "dummycard"')
    parser.add_argument('-a', '--firmware_rev', default='0',
                        help='Firmware revision. Default: "0"')
    parser.add_argument('-n', '--serial_number', default='0',
                        help='Serial number. Default: "0"')
    parser.add_argument('-m', '--mfg_name', default='dummymfg',
                        help='Manufacturer name. Default: "dummymfg"')
    parser.add_argument('-r', '--rack', type=int, default=0,
                        help='Rack. Default: 0')
    parser.add_argument('-u', '--subrack', type=int, default=0,
                        help='Subrack. Default: 0')
    parser.add_argument('-l', '--slot', type=int, default=0,
                        help='Slot. Default: 0')
    parser.add_argument('-o', '--port', type=int, default=-1,
                        help='Port. Default: -1 (no port)')
    parser.add_argument('-i', '--start', action='store_true',
                        help='Also move ConfD to start phase 2')
    parser.add_argument('-p', '--confd_port', type=int, default=4565,
                        help='Also move ConfD to start phase 2')
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO,
                        filename=f'card-{args.card_name}.log',
                        format='%(asctime)s %(levelname)-8s %(message)s')
    main(args)
