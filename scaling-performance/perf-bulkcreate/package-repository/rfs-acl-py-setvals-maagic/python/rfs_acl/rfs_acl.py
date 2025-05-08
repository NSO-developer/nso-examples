"""NSO example.

See the README file for more information
"""
import ipaddress
import ncs
from ncs.application import Service


class RfsAclServiceCallbacks(Service):
    """Service callback
    """
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        """Service create callback"""
        self.log.info(f'Service create(service={service._path})')
        ipstr = ipaddress.IPv4Address(u'1.0.0.2')
        devstr = f'/devices/device{{{service.device}}}/config'
        config = ncs.maagic.get_memory_node(root, devstr)
        for i in range(0, service.num_routes):
            route = config.route.create(
                "ics", ipstr, "255.255.255.255", "1.0.0.1")
            route.metric = 1
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
            ipint += 1
            ipstr = ipaddress.IPv4Address(ipint)
        ipstr = ipaddress.IPv4Address(u'1.0.0.1')
        aclid = config.access_list.access_list_id.create("tailf_42")
        for i in range(0, service.num_routes):
            rule = aclid.rule.create(
                f'extended permit tcp host {ipstr} host {ipstr} eq https')
            rule.log.create()
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
            ipint += 1
            ipstr = ipaddress.IPv4Address(ipint)
        rule = aclid.rule.create('extended deny ip any4 any4')
        rule.log.create()
        rule = aclid.rule.create('extended deny ip any6 any6')
        rule.log.create()
        ncs.maagic.shared_set_memory_tree(config)


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NSO.
# ---------------------------------------------
class RfsAclApplication(ncs.application.Application):
    """Service appliction
    """
    def setup(self):
        self.log.info('RfsAclApplication RUNNING')
        self.register_service('rfs-acl-servicepoint', RfsAclServiceCallbacks)

    def teardown(self):
        self.log.info('RfsAclApplication FINISHED')
