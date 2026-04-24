# -*- mode: python; python-indent: 4 -*-
import ipaddress

import ncs
from ncs.application import Service


def add_many(params, values):
    for key, value in values.items():
        params.add(key, str(value))
    return params


def service_names(service):
    token = f'TE-{service.tenant_id}'
    if service.vrf_name:
        vrf_name = str(service.vrf_name)
    else:
        vrf_name = f'TENANT-{service.tenant_id}'
    xr_prefix_set = f'{token}-PS'
    return {
        'VRF_NAME': vrf_name,
        'RD': f'{service.local_as}:{service.tenant_id}',
        'DESCRIPTION': f'{service.customer} {service.name}',
        'XR_PREFIX_SET_NAME': xr_prefix_set,
        'XR_IN_POLICY_NAME': f'{token}-IN',
        'XR_OUT_POLICY_NAME': f'{token}-OUT',
        'XR_IN_POLICY_VALUE': '  pass\r\n',
        'XR_OUT_POLICY_VALUE': (
            f'  if destination in {xr_prefix_set} then\r\n'
            '    pass\r\n'
            '  else\r\n'
            '    drop\r\n'
            '  endif\r\n'
        ),
        'EOS_PREFIX_LIST_NAME': f'{token}-PL',
        'EOS_ROUTE_MAP_NAME': f'{token}-RM',
    }


def validate_site_pair(root, service):
    selected = [str(site_name) for site_name in service.site]
    if len(selected) != 2:
        raise ValueError('Exactly two sites are required for this example.')

    left = root.tenant_edge_catalog.site[selected[0]]
    right = root.tenant_edge_catalog.site[selected[1]]
    if str(left.device) == str(right.device):
        raise ValueError(
            'The selected sites must terminate on different devices.'
        )

    for site in (left, right):
        network = ipaddress.IPv4Interface(str(site.local_prefix))
        peer_address = ipaddress.IPv4Address(str(site.peer_address))
        if peer_address not in network.network:
            raise ValueError(
                f'Peer address {site.peer_address} is not inside '
                f'{site.local_prefix} '
                f'for catalog site {site.name}.'
            )

    return [left, right]


def site_template_data(site, service):
    iface = ipaddress.IPv4Interface(str(site.local_prefix))
    names = service_names(service)
    data = {
        **names,
        'DEVICE': site.device,
        'INTERFACE_ID': site.interface_id,
        'LOCAL_PREFIX': site.local_prefix,
        'LOCAL_IP': iface.ip,
        'LOCAL_MASK': iface.network.netmask,
        'PEER_ADDRESS': site.peer_address,
        'PEER_AS': site.peer_as,
        'LOCAL_AS': service.local_as,
        'MAX_PREFIX': service.max_prefix,
    }
    return data


class ServiceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        sites = validate_site_pair(root, service)
        template = ncs.template.Template(service)

        for site in sites:
            if str(site.vendor) not in ('iosxr', 'eos'):
                raise ValueError(f'Unsupported catalog vendor {site.vendor}')

            site_data = site_template_data(site, service)
            base = ncs.template.Variables()
            add_many(base, site_data)
            template.apply('tenant-edge-base', base)

            seq = 10
            for prefix in service.export_prefix:
                prefix_vars = ncs.template.Variables()
                add_many(prefix_vars, site_data)
                prefix_vars.add('PREFIX_ENTRY', str(prefix))
                prefix_vars.add('SEQ', seq)
                seq += 10
                template.apply('tenant-edge-prefix', prefix_vars)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Main RUNNING')
        self.register_service('tenant-edge-service', ServiceCallbacks)

    def teardown(self):
        self.log.info('Main FINISHED')
