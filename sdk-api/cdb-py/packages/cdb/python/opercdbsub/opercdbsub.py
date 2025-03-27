import ncs


class DiffIter(ncs.cdb.OperSubscriber):
    """Iterate oper data changes"""
    def init(self):
        self.register('/test/stats-item', priority=100)

    def pre_iterate(self):
        self.log.debug('oper data DiffIter: pre_iterate')
        return self.log

    def iterate(self, kp, op, oldv, newv, state):
        """Handle oper data changes"""
        log = state
        log.debug(f'iterate kp={str(kp)} op={op} oldv={oldv} newv={newv}')
        if op is ncs.MOP_CREATED:
            pass
        elif op is ncs.MOP_DELETED:
            pass
        # elif op is ...
        return ncs.ITER_RECURSE


class Opercdbsub(ncs.application.Application):
    def setup(self):
        """Register a CDB subscriber to be notified of operational data changes
        under the test container.
        """
        self.log.debug('Opercdbsub RUNNING')
        self.sub = DiffIter(app=self)
        self.sub.start()

    def teardown(self):
        self.sub.stop()
        self.log.debug('Opercdbsub FINISHED')
