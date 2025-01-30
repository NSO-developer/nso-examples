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
        route_ipstr = ipaddress.IPv4Address(u'1.0.0.2')
        rule_ipstr = ipaddress.IPv4Address(u'1.0.0.1')
        for i in range(0, service.num_routes):
            rv = ncs.template.Variables()
            rv.add('DEVICE', service.device)
            rv.add('IPROUTE', route_ipstr)
            rv.add('IPACCESS', rule_ipstr)
            tmpl = ncs.template.Template(service)
            tmpl.apply('rfs-acl-template', rv)

            ipint = int(ipaddress.IPv4Address(u'{}'.format(route_ipstr)))
            ipint += 1
            route_ipstr = ipaddress.IPv4Address(ipint)
            ipint = int(ipaddress.IPv4Address(u'{}'.format(rule_ipstr)))
            ipint += 1
            rule_ipstr = ipaddress.IPv4Address(ipint)


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
