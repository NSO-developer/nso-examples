# -*- mode: python; python-indent: 4 -*-
import hashlib

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


def device_kind(root, device_name: str) -> str:
    ned_id = device_ned_id(root, device_name)
    if ('cisco-iosxr-netsim-cli' in ned_id or
            'cisco-iosxr-netsim-nc' in ned_id):
        return 'xr'
    if 'juniper-junos-netsim-nc' in ned_id:
        return 'junos'
    if 'alu-sr-netsim-cli' in ned_id:
        return 'sros'
    raise ValueError(
        f'PM device {device_name} must be IOS XR, Junos, or SR OS netsim, '
        f'got {ned_id or "unknown device type"}'
    )


def is_ipv4(value: str) -> bool:
    return '.' in value


def safe_pm_name(*parts, limit=32) -> str:
    raw = '-'.join(str(part) for part in parts if str(part))
    safe = ''.join(char if char.isalnum() or char in '._-' else '-'
                   for char in raw).strip('-')
    if not safe:
        return 'pm'
    if len(safe) <= limit:
        return safe
    digest = hashlib.sha1(safe.encode('utf-8')).hexdigest()[:6]
    return f'{safe[:limit - 7]}-{digest}'


def ip_choice(params, prefix: str, value) -> None:
    rendered = text(value)
    params.add(f'{prefix}_IPV4', rendered if '.' in rendered else '')
    params.add(f'{prefix}_IPV6', rendered if ':' in rendered else '')


def apply_delay_profile(template, device, profile, kind='xr'):
    params = ncs.template.Variables()
    params.add('DEVICE', device)
    params.add('PROFILE', profile.name)
    params.add('PROTOCOL', profile.protocol)
    params.add('MEASUREMENT_MODE', profile.measurement_mode)
    params.add('TX_INTERVAL', profile.tx_interval)
    params.add('COMPUTATION_INTERVAL', profile.computation_interval)
    params.add('PERIODIC_INTERVAL', profile.periodic_interval)
    params.add('PERIODIC_THRESHOLD', profile.periodic_threshold)
    params.add('DSCP', text(profile.dscp))
    params.add('TRAFFIC_CLASS', profile.traffic_class)
    if kind not in ('xr', 'sros'):
        return
    template.apply('transport-pm-delay-profile', params)


def apply_liveness_profile(template, device, profile, kind='xr'):
    if kind != 'xr':
        return
    params = ncs.template.Variables()
    params.add('DEVICE', device)
    params.add('PROFILE', profile.name)
    params.add('TX_INTERVAL', profile.tx_interval)
    params.add('BURST_INTERVAL', profile.burst_interval)
    params.add('MULTIPLIER', profile.multiplier)
    params.add('DSCP', text(profile.dscp))
    template.apply('transport-pm-liveness-profile', params)


class PmProfileCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        template = ncs.template.Template(service)

        profile_devices = {str(device) for device in service.device}
        for attachment in service.interface_attachment:
            profile_devices.add(str(attachment.device))
        for attachment in service.sr_policy_attachment:
            profile_devices.add(str(attachment.device))
        for attachment in service.odn_color_attachment:
            profile_devices.add(str(attachment.device))
        for attachment in service.rsvp_te_attachment:
            profile_devices.add(str(attachment.device))
        for attachment in service.endpoint_attachment:
            profile_devices.add(str(attachment.device))

        for device in sorted(profile_devices):
            kind = device_kind(root, device)
            if kind == 'xr' and service.protocol_profile.exists():
                params = ncs.template.Variables()
                params.add('DEVICE', device)
                params.add('QUERIER_SRC_PORT',
                           service.protocol_profile.querier_src_port)
                params.add('QUERIER_DST_PORT',
                           service.protocol_profile.querier_dst_port)
                template.apply('transport-pm-protocol-profile', params)

            for profile in service.delay_profile:
                apply_delay_profile(template, device, profile, kind)
            for profile in service.liveness_profile:
                apply_liveness_profile(template, device, profile, kind)

        for attachment in service.interface_attachment:
            if device_kind(root, str(attachment.device)) != 'xr':
                self.log.info('Skipping non-XR interface PM attachment ',
                              attachment.device, ' ', attachment.interface)
                continue
            params = ncs.template.Variables()
            params.add('DEVICE', attachment.device)
            params.add('INTERFACE', attachment.interface)
            params.add('DELAY_PROFILE', text(attachment.delay_profile))
            params.add('ADVERTISE_DELAY', attachment.advertise_delay)
            ip_choice(params, 'NEXT_HOP', attachment.next_hop)
            template.apply('transport-pm-interface-attachment', params)

        for attachment in service.sr_policy_attachment:
            if device_kind(root, str(attachment.device)) != 'xr':
                self.log.info('Skipping non-XR SR policy PM attachment ',
                              attachment.device, ' ', attachment.policy_name)
                continue
            params = ncs.template.Variables()
            params.add('DEVICE', attachment.device)
            params.add('POLICY_NAME', attachment.policy_name)
            params.add('DELAY_PROFILE', text(attachment.delay_profile))
            params.add('LIVENESS_PROFILE', text(attachment.liveness_profile))
            params.add('INVALIDATION_ACTION', attachment.invalidation_action)
            template.apply('transport-pm-sr-policy-attachment', params)

        for attachment in service.odn_color_attachment:
            if device_kind(root, str(attachment.device)) != 'xr':
                self.log.info('Skipping non-XR ODN PM attachment ',
                              attachment.device, ' ', attachment.color)
                continue
            params = ncs.template.Variables()
            params.add('DEVICE', attachment.device)
            params.add('COLOR', attachment.color)
            params.add('DELAY_PROFILE', text(attachment.delay_profile))
            params.add('LIVENESS_PROFILE', text(attachment.liveness_profile))
            template.apply('transport-pm-odn-color-attachment', params)

        for attachment in service.rsvp_te_attachment:
            if device_kind(root, str(attachment.device)) != 'xr':
                self.log.info('Skipping non-XR RSVP-TE PM attachment ',
                              attachment.device, ' ', attachment.tunnel_id)
                continue
            params = ncs.template.Variables()
            params.add('DEVICE', attachment.device)
            params.add('TUNNEL_ID', attachment.tunnel_id)
            template.apply('transport-pm-rsvp-te-attachment', params)

        for attachment in service.endpoint_attachment:
            kind = device_kind(root, str(attachment.device))
            params = ncs.template.Variables()
            params.add('DEVICE', attachment.device)
            params.add('DELAY_PROFILE', text(attachment.delay_profile))
            params.add('LIVENESS_PROFILE', text(attachment.liveness_profile))
            ip_choice(params, 'ENDPOINT', attachment.address)
            ip_choice(params, 'SOURCE', attachment.source_address)
            params.add('ATTACHMENT_NAME',
                       safe_pm_name(service.name, attachment.delay_profile,
                                    attachment.address))
            if kind == 'sros' and not is_ipv4(text(attachment.address)):
                self.log.info('Skipping SR OS IPv6 PM endpoint attachment ',
                              attachment.device, ' ', attachment.address)
                continue
            template.apply('transport-pm-endpoint-attachment', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class PmSelfTest(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        service = ncs.maagic.get_node(trans, kp)

        devices = {str(device) for device in service.device}
        for attachment in service.interface_attachment:
            devices.add(str(attachment.device))
        for attachment in service.sr_policy_attachment:
            devices.add(str(attachment.device))
        for attachment in service.odn_color_attachment:
            devices.add(str(attachment.device))
        for attachment in service.rsvp_te_attachment:
            devices.add(str(attachment.device))
        for attachment in service.endpoint_attachment:
            devices.add(str(attachment.device))

        xr = junos = sros = skipped = 0
        for device in sorted(devices):
            try:
                kind = device_kind(root, device)
            except ValueError:
                skipped += 1
                continue
            if kind == 'xr':
                xr += 1
            elif kind == 'junos':
                junos += 1
            elif kind == 'sros':
                sros += 1

        output.success = skipped == 0
        output.xr_native_devices = xr
        output.junos_native_devices = junos
        output.sros_native_devices = sros
        output.endpoint_attachments = len(list(service.endpoint_attachment))
        output.skipped_attachments = skipped
        output.message = (
            f'PM profile {service.name}: IOS XR native delay-measurement '
            f'devices={xr}, Junos PM/RPM devices={junos}, '
            f'SR OS OAM-PM/TWAMP-light devices={sros}. '
            'Policy, ODN, RSVP-TE, and interface PM attachments remain '
            'XR-native in this netsim; endpoint probes cover Junos/SR OS.'
        )


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('transport-pm Main RUNNING')
        self.register_service(
            'transport-pm-profile-service', PmProfileCallbacks
        )
        self.register_action('transport-pm-self-test', PmSelfTest)

    def teardown(self):
        self.log.info('transport-pm Main FINISHED')
