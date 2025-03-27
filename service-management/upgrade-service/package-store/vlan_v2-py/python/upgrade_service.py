import ncs
import _ncs.cdb as cdb


class UpgradeService(ncs.upgrade.Upgrade):
    def upgrade(self, cdbsock, trans):
        # start a session against running
        cdb.start_session2(cdbsock, ncs.cdb.RUNNING,
                           ncs.cdb.LOCK_SESSION | ncs.cdb.LOCK_WAIT)

        # maagic object for writing upgraded data
        root = ncs.maagic.get_root(trans)
        vlan = root.ncs__services.vl__vlan

        # loop over a list and do some work
        num = cdb.num_instances(cdbsock, '/services/vlan')
        for i in range(0, num):
            name = cdb.get(cdbsock, f'/services/vlan[{i}]/name')
            path = f'/services/vlan{{{name}}}'
            print(f'SERVICENAME = {name}')
            iface = cdb.get(cdbsock, f'{path}/iface')
            unit = cdb.get(cdbsock, f'{path}/unit')
            vid = cdb.get(cdbsock, f'{path}/vid')
            vlan[name].global_id = f'{iface}-{unit}-{vid}'

        return True
