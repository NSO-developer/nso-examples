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
        config = root.ncs__devices.device[service.device].config
        for i in range(0, service.num_routes):
            config.route.create("ics", ipstr, "255.255.255.255", "1.0.0.1")
            config.route["ics", ipstr, "255.255.255.255", "1.0.0.1"].metric = 1
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
            ipint += 1
            ipstr = ipaddress.IPv4Address(ipint)
        ipstr = ipaddress.IPv4Address(u'1.0.0.1')
        config.access_list.access_list_id.create("tailf_42")
        aclid = config.access_list.access_list_id["tailf_42"]
        for i in range(0, service.num_routes):
            rule = f'extended permit tcp host {ipstr} host {ipstr} eq https'
            aclid.rule.create(rule)
            aclid.rule[rule].log.create()
            ipint = int(ipaddress.IPv4Address(u'{}'.format(ipstr)))
            ipint += 1
            ipstr = ipaddress.IPv4Address(ipint)
        aclid.rule.create('extended deny ip any4 any4')
        aclid.rule['extended deny ip any4 any4'].log.create()
        aclid.rule.create('extended deny ip any6 any6')
        aclid.rule['extended deny ip any6 any6'].log.create()


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
