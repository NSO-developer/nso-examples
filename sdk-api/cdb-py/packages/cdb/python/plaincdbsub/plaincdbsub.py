import ncs


class DiffIter(ncs.cdb.Subscriber):
    """Iterate configuration changes"""
    def init(self):
        self.register('/devices/device{ex0}/config', priority=100)

    def pre_iterate(self):
        self.log.info('configuration DiffIter: pre_iterate')
        return self.log

    def iterate(self, kp, op, oldv, newv, state):
        """Handle configuration changes"""
        log = state
        log.info(f'iterate kp={str(kp)} op={op} oldv={oldv} newv={newv}')
        if op is ncs.MOP_CREATED:
            pass
        elif op is ncs.MOP_DELETED:
            pass
        # elif op is ...
        return ncs.ITER_RECURSE


class Plaincdbsub(ncs.application.Application):
    def setup(self):
        """Register a CDB subscriber to be notified of config changes under the
        ex0 device config.
        """
        self.log.debug('Plaincdbsub RUNNING')
        self.sub = DiffIter(app=self)
        self.sub.start()

    def teardown(self):
        self.sub.stop()
        self.log.info('Plaincdbsub FINISHED')
