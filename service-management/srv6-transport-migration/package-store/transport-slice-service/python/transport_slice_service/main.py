# -*- mode: python; python-indent: 4 -*-
import ipaddress
import re

import ncs
from ncs.application import Service
from ncs.dp import Action


UNSET = ('', 'None', 'none')


def text(value, default=''):
    try:
        rendered = str(value)
    except Exception:
        return default
    return default if rendered in UNSET else rendered


def enum_text(value, default=''):
    try:
        rendered = str(value)
    except Exception:
        return default
    return default if rendered in ('', 'None') else rendered


def exists(node) -> bool:
    if node is None:
        return False
    backend = getattr(node, '_backend', None)
    path = getattr(node, '_path', None)
    if backend is not None and path:
        try:
            return bool(backend.exists(path))
        except Exception:
            pass
    return text(node) != ''


def safe_name(*parts, limit=24) -> str:
    raw = '-'.join(str(part) for part in parts if str(part))
    safe = re.sub(r'[^A-Za-z0-9-]+', '-', raw).strip('-')
    return (safe or 'nss-service')[:limit]


def device_ned_id(root, device_name: str) -> str:
    device_type = root.devices.device[device_name].device_type
    try:
        cli_ned_id = str(device_type.cli.ned_id)
        if cli_ned_id not in ('', 'None'):
            return cli_ned_id
    except Exception:
        pass
    try:
        netconf_ned_id = str(device_type.netconf.ned_id)
        if netconf_ned_id not in ('', 'None'):
            return netconf_ned_id
    except Exception:
        pass
    return ''


def xr_device(root, device_name: str) -> bool:
    ned_id = device_ned_id(root, device_name)
    return ('cisco-iosxr-netsim-cli' in ned_id or
            'cisco-iosxr-netsim-nc' in ned_id)


def junos_device(root, device_name: str) -> bool:
    return 'juniper-junos-netsim-nc' in device_ned_id(root, device_name)


def sros_device(root, device_name: str) -> bool:
    return 'alu-sr-netsim-cli' in device_ned_id(root, device_name)


def te_head_end_device(root, device_name: str) -> bool:
    return xr_device(root, device_name) or junos_device(root, device_name)


def assurance_device(root, device_name: str) -> bool:
    return (xr_device(root, device_name) or
            junos_device(root, device_name) or
            sros_device(root, device_name))


def get_services(root):
    return root.core_network.services


def get_slo(root, connection_group):
    template_id = text(connection_group.slo_sle_template)
    if not template_id:
        return None
    return root.network_slice_services.slo_sle_templates.slo_sle_template[
        template_id
    ]


def selected_sdps(service, connection_group):
    selected = {str(sdp_id) for sdp_id in connection_group.sdp}
    if not selected:
        return list(service.sdp)
    return [sdp for sdp in service.sdp if str(sdp.sdp_id) in selected]


def device_for_sdp(root, sdp):
    port = root.inventory.port[sdp.attachment_circuit_id]
    return str(port.device)


def interface_for_sdp(root, sdp):
    port = root.inventory.port[sdp.attachment_circuit_id]
    return str(port.interface)


def next_hop_for_sdp(sdp):
    if not text(sdp.ipv4_subnet):
        return ''
    subnet = ipaddress.IPv4Network(str(sdp.ipv4_subnet))
    return str(subnet[int(sdp.customer_ip_index)])


def provider_ip_for_sdp(sdp):
    if not text(sdp.ipv4_subnet):
        return ''
    subnet = ipaddress.IPv4Network(str(sdp.ipv4_subnet))
    return str(subnet[int(sdp.provider_ip_index)])


def rsvp_te_head_end_for_tunnel(root, tunnel_id):
    try:
        tunnels = root.te.tunnels.tunnel
    except Exception:
        return ''

    for tunnel in tunnels:
        if text(getattr(tunnel, 'identifier', '')) != str(tunnel_id):
            continue
        for leaf in ('te_ext__head_end', 'head_end'):
            head_end = text(getattr(tunnel, leaf, ''))
            if head_end:
                return head_end
    return ''


def upsert_l3vpn(root, service, connection_group, sdps, odn_name,
                 transport_profile):
    vpn_name = safe_name(
        'nss', service.service_id, connection_group.connection_group_id
    )
    vpn = (
        root.l3vpn[vpn_name]
        if vpn_name in root.l3vpn else root.l3vpn.create(vpn_name)
    )
    vpn.customer = service.customer
    vpn.force = True
    vpn.transport_profile = (
        'srv6' if transport_profile == 'srv6'
        else 'sr-mpls' if transport_profile == 'sr-mpls'
        else 'rsvp-te'
    )
    if odn_name:
        vpn.odn_template = odn_name
    else:
        try:
            del vpn.odn_template
        except Exception:
            pass

    # Keep the example deterministic; link ids follow SDP order.
    for index, sdp in enumerate(sdps, start=1):
        link = vpn.link[index] if index in vpn.link else vpn.link.create(index)
        link.enabled = True
        link.port = sdp.attachment_circuit_id
        if text(sdp.ipv4_subnet):
            link.subnet = sdp.ipv4_subnet
        link.pe_ip = sdp.provider_ip_index
        link.ce_ip = sdp.customer_ip_index

        port = root.inventory.port[sdp.attachment_circuit_id]
        ce_name = text(getattr(port, 'ce', ''))
        ce_as = ''
        if ce_name:
            ce = root.inventory.ce[ce_name]
            if ce.managed:
                ce_as = text(getattr(ce, 'as_number', ''))
        if ce_as:
            link.bgp_peering.enabled = True
            link.bgp_peering.peer_as = int(ce_as)
        else:
            link.bgp_peering.enabled = False
            try:
                del link.bgp_peering.peer_as
            except Exception:
                pass
    return vpn_name


def upsert_l2vpn(root, service, connection_group, sdps):
    vpn_name = safe_name(
        'nss', service.service_id, connection_group.connection_group_id
    )
    vpn = (
        root.l2vpn[vpn_name]
        if vpn_name in root.l2vpn else root.l2vpn.create(vpn_name)
    )
    vpn.customer = service.customer
    vpn.force = True
    for sdp in sdps:
        port = str(sdp.attachment_circuit_id)
        if port not in vpn.ports:
            vpn.ports.create(port)
    return vpn_name


def apply_odn(template, connection_group, head_ends, odn_name):
    for head_end in sorted(head_ends):
        params = ncs.template.Variables()
        params.add('ODN_NAME', odn_name)
        params.add('HEAD_END', head_end)
        params.add('COLOR', connection_group.color)
        params.add('METRIC_TYPE', connection_group.metric_type)
        params.add('SID_ALGORITHM', connection_group.sid_algorithm)
        params.add('SRV6', 'true'
                   if effective_transport_profile(connection_group) == 'srv6'
                   else '')
        template.apply('transport-slice-odn-template', params)


def effective_transport_profile(connection_group):
    profile = enum_text(getattr(connection_group, 'transport_profile', ''),
                        'none')
    if profile != 'none':
        return profile

    # Compatibility with payloads created before transport-profile existed.
    mode = enum_text(connection_group.transport_mode, 'none')
    if mode == 'odn-template':
        return 'srv6'
    return 'none'


def apply_pm(template, root, service, connection_group, sdps, slo,
             transport_profile, head_ends):
    delay_profile = 'nss-delay'
    if slo:
        delay_profile = text(
            getattr(slo, 'pm_delay_profile', ''), 'nss-delay'
        )
    pm_name = safe_name('nss', service.service_id, 'pm')
    for sdp in sdps:
        device = device_for_sdp(root, sdp)
        next_hop = next_hop_for_sdp(sdp)
        if not next_hop:
            continue
        params = ncs.template.Variables()
        params.add('PM_NAME', pm_name)
        params.add('DEVICE', device)
        params.add('INTERFACE', interface_for_sdp(root, sdp))
        params.add('NEXT_HOP', next_hop)
        params.add('SOURCE_ADDRESS', provider_ip_for_sdp(sdp))
        params.add('DELAY_PROFILE', delay_profile)
        params.add(
            'LATENCY_BOUND', text(getattr(slo, 'latency_bound', ''), '50')
        )
        if xr_device(root, device):
            template.apply('transport-slice-pm-profile', params)
        elif assurance_device(root, device):
            template.apply('transport-slice-pm-endpoint-attachment', params)

    if text(connection_group.color):
        for sdp in sdps:
            device = device_for_sdp(root, sdp)
            if xr_device(root, device):
                params = ncs.template.Variables()
                params.add('PM_NAME', pm_name)
                params.add('DEVICE', device)
                params.add('COLOR', connection_group.color)
                params.add('DELAY_PROFILE', delay_profile)
                template.apply('transport-slice-pm-odn-attachment', params)

    if transport_profile == 'rsvp-te':
        tunnel_head_end = rsvp_te_head_end_for_tunnel(
            root, connection_group.rsvp_te.tunnel_id)
        for device in sorted(head_ends):
            if not xr_device(root, device):
                continue
            if tunnel_head_end and device != tunnel_head_end:
                continue
            params = ncs.template.Variables()
            params.add('PM_NAME', pm_name)
            params.add('DEVICE', device)
            params.add('TUNNEL_ID', connection_group.rsvp_te.tunnel_id)
            template.apply('transport-slice-pm-rsvp-te-attachment', params)


def apply_assurance(template, root, service, connection_group, sdps, slo,
                    realized_service):
    profile = text(service.assurance_profile)
    if not profile and slo is not None:
        profile = text(getattr(slo, 'assurance_profile', ''))
    if not profile:
        return

    assurance = service.service_assurance
    if not exists(assurance) and slo is not None:
        assurance = slo.service_assurance
    if exists(assurance) and text(assurance.monitoring_state) == 'disabled':
        return

    devices = sorted({device_for_sdp(root, sdp) for sdp in sdps
                      if assurance_device(root, device_for_sdp(root, sdp))})
    for device in devices:
        params = ncs.template.Variables()
        monitor_name = safe_name(
            'NSS',
            service.service_id,
            connection_group.connection_group_id,
            device,
            limit=64,
        )
        params.add('MONITOR_NAME',
                   monitor_name)
        params.add('PROFILE', profile)
        params.add('DEVICE', device)
        params.add('MONITORED_SERVICE', realized_service)
        params.add('MONITORING_STATE',
                   text(getattr(assurance, 'monitoring_state', ''), 'active'))
        params.add('PROFILE_NAME',
                   text(getattr(assurance, 'profile_name', ''),
                        'transport-slice'))
        params.add('RULE_NAME',
                   text(getattr(assurance, 'rule_name', ''), 'slice-slo'))
        params.add('PRESERVATION',
                   text(getattr(assurance, 'preservation', ''), 'current'))
        template.apply('transport-slice-assurance-monitor', params)


class SliceCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        template = ncs.template.Template(service)

        for connection_group in service.connection_group:
            sdps = selected_sdps(service, connection_group)
            if len(sdps) < 2:
                raise ValueError(
                    f'Connection group {connection_group.connection_group_id} '
                    'must reference at least two SDPs'
                )

            slo = get_slo(root, connection_group)
            head_ends = {
                device_for_sdp(root, sdp) for sdp in sdps
                if te_head_end_device(root, device_for_sdp(root, sdp))
            }
            odn_name = ''
            transport_profile = effective_transport_profile(connection_group)
            if transport_profile in ('sr-mpls', 'srv6'):
                odn_name = safe_name('nss', service.service_id,
                                     connection_group.connection_group_id,
                                     'odn')
                apply_odn(template, connection_group, head_ends, odn_name)

            connectivity_type = enum_text(connection_group.connectivity_type,
                                          'l3vpn')
            if connectivity_type == 'l2vpn':
                realized_service = upsert_l2vpn(root, service,
                                                connection_group, sdps)
            else:
                realized_service = upsert_l3vpn(root, service,
                                                connection_group, sdps,
                                                odn_name, transport_profile)

            apply_pm(template, root, service, connection_group, sdps, slo,
                     transport_profile, head_ends)
            apply_assurance(template, root, service, connection_group, sdps,
                            slo, realized_service)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class MigrationReadiness(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        service = ncs.maagic.get_node(trans, kp)

        rsvp_bindings = 0
        sr_bindings = 0
        for connection_group in service.connection_group:
            profile = effective_transport_profile(connection_group)
            if profile == 'rsvp-te':
                rsvp_bindings += 1
            if profile in ('sr-mpls', 'srv6') and text(connection_group.color):
                sr_bindings += 1

        services = get_services(root)
        try:
            srv6_devices = len(list(services.srv6_node))
        except Exception:
            srv6_devices = 0
        try:
            pm_profiles = len(list(services.pm_profile))
        except Exception:
            pm_profiles = 0
        try:
            assurance_monitors = len(list(services.assurance_monitor))
        except Exception:
            assurance_monitors = 0

        sdp_count = len(list(service.sdp))
        connection_groups = len(list(service.connection_group))
        success = (
            sdp_count >= 2 and connection_groups > 0 and
            (rsvp_bindings + sr_bindings) > 0 and
            pm_profiles > 0 and assurance_monitors > 0
        )

        output.success = success
        output.sdp_count = sdp_count
        output.connection_groups = connection_groups
        output.rsvp_te_bindings = rsvp_bindings
        output.sr_color_bindings = sr_bindings
        output.srv6_enabled_devices = srv6_devices
        output.pm_profiles = pm_profiles
        output.assurance_monitors = assurance_monitors
        output.message = (
            f'Slice {service.service_id}: SDPs={sdp_count}, '
            f'connection-groups={connection_groups}, RSVP-TE bindings='
            f'{rsvp_bindings}, SR color bindings={sr_bindings}, '
            f'SRv6-enabled nodes={srv6_devices}, PM profiles={pm_profiles}, '
            f'assurance monitors={assurance_monitors}.'
        )


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('transport-slice-service Main RUNNING')
        self.register_service('transport-slice-servicepoint', SliceCallbacks)
        self.register_action('transport-slice-migration-readiness',
                             MigrationReadiness)

    def teardown(self):
        self.log.info('transport-slice-service Main FINISHED')
