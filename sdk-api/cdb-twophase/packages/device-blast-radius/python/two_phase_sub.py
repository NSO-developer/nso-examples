# -*- mode: python; python-indent: 4 -*-
import ncs
import _ncs


class BlastRadiusSubscriber(ncs.cdb.TwoPhaseSubscriber):
    """Iterate configuration changes"""
    def init(self):
        _ncs.cdb.mandatory_subscriber(self.sock, self.name)
        self.register('/ncs:devices/device/config')

    # Control when to do the check here
    def should_iterate(self):
        return True

    def pre_iterate(self):
        self.log.info('pre_iterate')
        with ncs.maapi.single_read_trans('system', 'system') as trans:
            root = ncs.maagic.get_root(trans)
            max_ndevs = root.ncs__devices.dbr__blast_radius.max_devices
        return {'max_ndevs': max_ndevs, 'devices': []}

    def prepare(self, kp, op, oldv, newv, state):
        if state['max_ndevs'] is None:
            return ncs.ITER_STOP

        state['devices'].append(kp)
        n_devices = len(state['devices'])
        max_ndevs = state['max_ndevs']

        self.log.info(f'prepare: kp={kp} ndevs={n_devices}'
                      f' max_ndevs={max_ndevs}')

        if n_devices > max_ndevs:
            self.log.info(f'prepare: aborting transaction kp={kp}')
            raise Exception('Too many devices in transaction')
        return ncs.ITER_CONTINUE

    def abort(self, kp, op, oldv, newv, state):
        self.log.info(f'abort: kp={kp} ndevs={state["devices"]}')
        return ncs.ITER_STOP

    def iterate(self, kp, op, oldv, newv, state):
        self.log.info(f'Done iterating: kp={kp}')
        return ncs.ITER_STOP


class TwoPhaseSubApp(ncs.application.Application):
    def setup(self):
        self.log.info('TwoPhaseSubApp RUNNING')
        self.sub = BlastRadiusSubscriber('blast_radius_subscriber', app=self)
        self.sub.start()

    def teardown(self):
        self.log.info('TwoPhaseSubApp FINISHED')
        self.sub.stop()
