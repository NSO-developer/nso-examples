# -*- mode: python; python-indent: 4 -*-
import socket
import ncs
import _ncs
from _ncs import cdb
from ncs.dp import Action

DBFILES = [cdb.A_CDB, cdb.O_CDB, cdb.S_CDB]


class CompactionTask(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output):
        self.log.info(f'CompactionTask {name}')
        if _ncs.PATH is None:
            c_sock = socket.socket()
        else:
            c_sock = socket.socket(family=socket.AF_UNIX)
        _ncs.cdb.connect(c_sock, ip='127.0.0.1', port=_ncs.NCS_PORT,
                         path=_ncs.PATH, type=_ncs.cdb.DATA_SOCKET)

        for db in DBFILES:
            if self.is_compaction_needed(c_sock, db):
                self.log.info('CompactionTask STARTED')
                self.trigger_compaction(c_sock, db)
                self.log.info('CompactionTask DONE')
            else:
                self.log.info('CompactionTask NOT NEEDED')

    def is_compaction_needed(self, c_sock, db):
        info = _ncs.cdb.get_compaction_info(c_sock, db)
        # Given the returned compaction information, the need for compaction
        # can be evaluated according to the appropriate criteria. For the
        # purpose of this showcase this is kept simple.
        # Compact if there has been more than five transactions
        if info['ntrans'] > 5:
            return True
        else:
            return False

    def trigger_compaction(self, c_sock, db):
        _ncs.cdb.initiate_journal_dbfile_compaction(c_sock, db)


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class Main(ncs.application.Application):
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('Main RUNNING')

        # Service callback for compaction action
        self.register_action('compact', CompactionTask)
        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Main FINISHED')
