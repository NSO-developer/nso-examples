# -*- mode: python90; python-indent: 4 -*-
"""Example NCS Service module.
"""

import ncs
from network import getIpAddress, getIpPrefix, getNetMask, getNextIPV4Address
from network import prefixToWildcardMask

# ---------------------------------
# SERVICE CALLBACK OBJECT/FUNCTIONS
# ---------------------------------


class ServiceCallbacks(ncs.application.Service):
    # The pre_modification() and post_modification() callbacks are optional,
    # and are invoked outside FASTMAP. pre_modification() is invoked before
    # create, update, or delete of the service, as indicated by the enum
    # ncs_service_operation op parameter. Conversely
    # post_modification() is invoked after create, update, or delete
    # of the service. These functions can be useful e.g. for
    # allocations that should be stored and existing also when the
    # service instance is removed.
    @ncs.application.Service.create
    def cb_create(self, tctx, root, service, proplist):
        # The create() callback is invoked inside NCS FASTMAP and must
        # always be registered.
        self.log.debug("Service ", service)
        topology = root.topology
        endpoints = service.endpoint

        self.log.debug("Topology ", topology, " endpoints ", endpoints)

        for endpoint in endpoints:
            for connection in topology.connection:
                e1_dev = connection.endpoint_1.device
                e2_dev = connection.endpoint_2.device
                ce_name = endpoint.ce_device
                if (e1_dev == ce_name) or (e2_dev == ce_name):
                    conn = connection
                    break
                else:
                    conn = None

            if not conn:
                continue

            pe_endpoint = get_connected_endpoint(conn, endpoint.ce_device)
            ce_endpoint = get_my_endpoint(conn, endpoint.ce_device)

            tv = ncs.template.Variables()
            tv.add('PE', pe_endpoint.device)
            tv.add('CE', ce_endpoint.device)
            tv.add('VLAN_ID', conn.link_vlan)
            tv.add('LINK_PE_ADR', getIpAddress(pe_endpoint.ip_address))
            tv.add('LINK_CE_ADR', getIpAddress(ce_endpoint.ip_address))
            tv.add('LINK_MASK', getNetMask(ce_endpoint.ip_address))
            tv.add('LINK_PREFIX', getIpPrefix(ce_endpoint.ip_address))
            tv.add('PE_INT_NAME', pe_endpoint.interface)
            tv.add('CE_INT_NAME', ce_endpoint.interface)
            tv.add('CE_LOCAL_INT_NAME', endpoint.ce_interface)
            tv.add('LOCAL_CE_ADR',
                   getIpAddress(getNextIPV4Address(endpoint.ip_network)))
            tv.add('LOCAL_CE_NET', getIpAddress(endpoint.ip_network))
            tv.add('CE_MASK', getNetMask(endpoint.ip_network))
            tv.add('BW', endpoint.bandwidth)
            tmpl = ncs.template.Template(service)
            tmpl.apply('l3vpn-pe', tv)
            tmpl.apply('l3vpn-ce', tv)

            # START OF QOS SECTION
            # Check if there exist some qos-policy config
            if root.qos.qos_policy:
                self.setup_qos(service, root.qos, service.qos.qos_policy,
                               pe_endpoint, ce_endpoint,
                               conn)

    def setup_qos(self, service, root_qos, vpn_qos_policy,
                  pe_endpoint, ce_endpoint, conn):
        tv = ncs.template.Variables()
        tv.add('POLICY_NAME', vpn_qos_policy)
        tv.add('CE_INT_NAME', ce_endpoint.interface)
        tv.add('PE_INT_NAME', pe_endpoint.interface)
        tv.add('VLAN_ID', conn.link_vlan)
        tv.add('PE', pe_endpoint.device)
        tv.add('CE', ce_endpoint.device)
        for c in root_qos.qos_policy:
            # Find the globally defined QOS policy our
            # service is referring to.
            # /qos/qos-policy{name}/class
            if vpn_qos_policy == c.name:
                # In our YANG model this node is named class. But class is a
                # reserved keyword in Python, therefore it's prefixed with the
                # model namespace prefix.
                qclass = c.l3vpn__class
                # Iterate over all classes for this policy
                # and its settings.
                for d in qclass:
                    for e in root_qos.qos_class:
                        qv = ncs.template.Variables(tv)
                        setup_qos_class(service, ce_endpoint,
                                        qv, d, e)


def setup_qos_class(service, ce_endpoint, qv, d, e):
    if e.name == d.qos_class:
        try:
            class_dscp = str(e.dscp_value)
        except Exception:
            class_dscp = ' '

        qv.add('CLASS_DSCP', class_dscp)
        qv.add('CLASS_NAME', d.qos_class)
        qv.add('CLASS_BW', d.bandwidth_percentage)
        try:
            if d.priority is True:
                tmpl = ncs.template.Template(service)
                tmpl.apply('l3vpn-qos-prio', qv)
                tmpl.apply('l3vpn-qos-pe-prio', qv)
        except Exception:
            tmpl = ncs.template.Template(service)
            tmpl.apply('l3vpn-qos', qv)
            tmpl.apply('l3vpn-qos-pe', qv)
            tmpl.apply('l3vpn-qos-pe-class', qv)

        # Also list all the globally defined
        # traffic match statements for this
        # class and add them to a arraylist
        # to use for processing.

        for m in e.match_traffic:
            av = ncs.template.Variables()
            set_acl_vars(av, m, 'GLOBAL')
            av.add('CE', ce_endpoint.device)
            tmpl = ncs.template.Template(service)
            tmpl.apply('l3vpn-acl', av)
            av.add('CLASS_NAME', e.name)
            av.add('MATCH_ENTRY', 'GLOBAL-' + m.name)
            tmpl.apply('l3vpn-qos-class', av)


def set_acl_vars(av, match, name_prefix):
    av.add('ACL_NAME', name_prefix + '-' + match.name)
    av.add('PROTOCOL', match.protocol)

    av.add('SOURCE_IP', match.source_ip)
    av.add('PORT_START', match.port_start)
    av.add('PORT_END', match.port_end)

    if match.source_ip == 'any':
        av.add('SOURCE_IP_ADR', 'any')
        av.add('SOURCE_WMASK', ' ')  # Note the 'space' here!
    else:
        av.add('SOURCE_IP_ADR', getIpAddress(match.source_ip))
        av.add('SOURCE_WMASK',
               prefixToWildcardMask(getIpPrefix(match.source_ip)))

    if match.destination_ip == 'any':
        av.add('DEST_IP_ADR', 'any')
        av.add('DEST_WMASK', ' ')  # Note the 'space' here!
    else:
        av.add('DEST_IP_ADR', getIpAddress(match.destination_ip))
        av.add('DEST_WMASK',
               prefixToWildcardMask(getIpPrefix(match.destination_ip)))


def get_connected_endpoint(conn, ce_name):
    if conn.endpoint_1.device == ce_name:
        return conn.endpoint_2
    else:
        return conn.endpoint_1


def get_my_endpoint(conn, cename):
    if conn.endpoint_1.device == cename:
        return conn.endpoint_1
    else:
        return conn.endpoint_2


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS
# ---------------------------------------------

class Service(ncs.application.Application):
    def setup(self):
        # The application class sets up logging for us.
        # It's a normal logging.getLogger() object.
        self.log.info('Worker RUNNING')
        # Create the Service callback object/functions.
        # Service callbacks require a registration for a 'service point',
        # as specified in the corresponding data model.
        self.register_service('l3vpn-servicepoint', ServiceCallbacks)
        # When we registered service, the Application class took care of
        # creating a daemon related to the servicepoint.
        # When this setup method is finished all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.
        self.log.info('Worker FINISHED')
