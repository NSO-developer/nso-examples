import ncs
import _ncs.cdb as cdb


class UpgradeService(ncs.upgrade.Upgrade):
    def upgrade(self, cdbsock, trans):
        # start a session against running
        cdb.start_session2(cdbsock, ncs.cdb.RUNNING,
                           ncs.cdb.LOCK_SESSION | ncs.cdb.LOCK_WAIT)

        # maagic object for writing upgraded data
        root = ncs.maagic.get_root(trans)
        tunnel = root.ncs__services.tl__tunnel

        # loop over a list and do some work
        num = cdb.num_instances(cdbsock, '/services/vlan')
        for i in range(0, num):
            name = cdb.get(cdbsock, f'/services/vlan[{i}]/name')
            old_path = f'/services/vlan{{{name}}}'
            print(f'SERVICENAME = {name}')
            tunnel.create(name)
            tunnel[name].gid = cdb.get(cdbsock, f'{old_path}/global-id')
            tunnel[name].interface = cdb.get(cdbsock, f'{old_path}/iface')
            tunnel[name].assembly = cdb.get(cdbsock, f'{old_path}/unit')
            tunnel[name].tunnel_id = cdb.get(cdbsock, f'{old_path}/vid')
            tunnel[name].descr = cdb.get(cdbsock, f'{old_path}/description')

        return True
