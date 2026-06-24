# -*- mode: python; python-indent: 4 -*-
import ast
import base64
import hashlib
import ipaddress
import re

import ncs
from ncs.application import Service
from ncs.dp import Action, ValidationError, ValidationPoint


UNSET_VALUES = {'', 'None', 'none'}


def ned_id(root, device_name: str) -> str:
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


def head_end_kind(root, device_name: str) -> str:
    device_ned_id = ned_id(root, device_name)
    if ('cisco-iosxr-netsim-cli' in device_ned_id or
            'cisco-iosxr-netsim-nc' in device_ned_id):
        return 'xr'
    if 'juniper-junos-netsim-nc' in device_ned_id:
        return 'junos'
    raise ValidationError(
        f'Head-end {device_name} must be an IOS XR or Junos netsim device, '
        f'got {device_ned_id or "unknown device type"}'
    )


def router_id(root, device_name: str) -> str:
    mgmt_base = root.core_network.settings.management_base
    mgmt_base = ipaddress.ip_address(str(mgmt_base))
    index = int(root.core_network.devices[device_name].index)
    return str(mgmt_base + index)


def maybe_attr(obj, *names):
    for name in names:
        try:
            return getattr(obj, name)
        except Exception:
            continue
    return None


def node_exists(node) -> bool:
    if node is None:
        return False

    backend = getattr(node, '_backend', None)
    path = getattr(node, '_path', None)
    if backend is not None and path:
        try:
            return bool(backend.exists(path))
        except Exception:
            pass

    try:
        text = str(node)
    except Exception:
        return False
    return text not in UNSET_VALUES


def node_text(node):
    if not node_exists(node):
        return None
    try:
        text = str(node).strip()
    except Exception:
        return None
    return text if text not in UNSET_VALUES else None


def child_text(obj, *names):
    return node_text(maybe_attr(obj, *names))


def endpoint_text(tunnel, name):
    endpoint = maybe_attr(tunnel, name)
    if endpoint is None:
        return None
    return child_text(endpoint, 'te_node_id', 'node_id')


def explicit_path_name(
    tunnel_name: str, path_name: str, preference: int
) -> str:
    base = f'{tunnel_name}-{path_name}-{preference}'
    safe = re.sub(r'[^A-Za-z0-9_.-]+', '-', base).strip('-')
    return safe[:64] or f'{tunnel_name}-{preference}'


def tunnel_id(service) -> int:
    ident = child_text(service, 'identifier')
    if ident is not None:
        try:
            value = int(ident)
            if 1 <= value <= 65535:
                return value
        except ValueError:
            pass

    digest = hashlib.sha1(str(service.name).encode('utf-8')).hexdigest()[:4]
    value = int(digest, 16) % 65535
    return value or 1


def decode_generic_label(label_value: str) -> int:
    compact = ''.join(label_value.split())
    literal = compact.rstrip('=')
    if literal.startswith(("b'", 'b"')):
        raw = ast.literal_eval(literal)
    elif re.fullmatch(r'[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2})*', compact):
        raw = bytes(int(part, 16) for part in compact.split(':'))
    else:
        compact += '=' * (-len(compact) % 4)
        raw = base64.b64decode(compact)
    if not raw:
        raise ValidationError('Label hop generic value must not be empty')
    if len(raw) > 4:
        raise ValidationError(
            f'Unsupported TE label width {len(raw)} bytes; expected 4 or fewer'
        )
    return int.from_bytes(raw, byteorder='big')


def metric_type_from_paths(service):
    try:
        paths = service.primary_paths.primary_path
    except Exception:
        return None

    for path in paths:
        try:
            metrics = path.optimizations.optimization_metric
        except Exception:
            metrics = []
        for metric in metrics:
            metric_type = child_text(metric, 'metric_type')
            if metric_type is None:
                continue
            if 'metric-te' in metric_type:
                return 'te'
            if 'metric-igp' in metric_type:
                return 'igp'
            if 'metric-latency' in metric_type:
                return 'delay'
    return None


def is_path_method(path, needle: str) -> bool:
    method = child_text(path, 'path_computation_method') or ''
    return needle in method


def rsvp_signaling(service) -> bool:
    signaling = child_text(service, 'signaling_type') or ''
    return 'rsvp' in signaling


def te_ext_node(service, name):
    return maybe_attr(service, f'te_ext__{name}', name)


class IetfTeValidator(ValidationPoint):
    @ValidationPoint.validate
    def cb_validate(self, tctx, keypath, value, validationpoint):
        return ncs.CONFD_OK


class CompatibilityAction(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        for leaf, value in (
            ('success', True),
            ('detail',
             'No-op compatibility action for the single-NSO netsim lab.'),
            ('status', 'ok'),
            ('message',
             'No external LSA cleanup or additional coordination is required '
             'in the srv6-transport-migration example.'),
        ):
            try:
                setattr(output, leaf, value)
            except Exception:
                pass


class IetfTeCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        tunnel_name = str(service.tunnel)
        if tunnel_name not in root.te.tunnels.tunnel:
            raise ValidationError(
                f'IETF TE tunnel {tunnel_name} does not exist'
            )
        tunnel = root.te.tunnels.tunnel[tunnel_name]

        if not rsvp_signaling(tunnel):
            raise ValidationError(
                'The srv6-transport-migration example currently supports only '
                'te-types:path-setup-rsvp tunnels'
            )

        head_end = child_text(tunnel, 'te_ext__head_end', 'head_end')
        if head_end is None:
            raise ValidationError(
                'te-ext:head-end is required in this example'
            )
        if head_end not in root.core_network.devices:
            raise ValidationError(f'Unknown head-end device {head_end}')
        kind = head_end_kind(root, head_end)

        tail_end = child_text(tunnel, 'te_ext__tail_end', 'tail_end')
        if tail_end is not None and tail_end not in root.core_network.devices:
            raise ValidationError(f'Unknown tail-end device {tail_end}')

        source = endpoint_text(tunnel, 'source') or router_id(root, head_end)
        destination = endpoint_text(tunnel, 'destination')
        if destination is None and tail_end is not None:
            destination = router_id(root, tail_end)
        if destination is None:
            raise ValidationError(
                'A destination address or te-ext:tail-end must be provided'
            )

        description = child_text(tunnel, 'description') or ''
        setup_priority = child_text(tunnel, 'setup_priority') or ''
        hold_priority = child_text(tunnel, 'hold_priority') or ''
        bandwidth = ''
        te_bandwidth = maybe_attr(tunnel, 'te_bandwidth')
        if te_bandwidth is not None:
            bandwidth = child_text(te_bandwidth, 'generic') or ''

        metric = metric_type_from_paths(tunnel) or ''
        fast_reroute = 'true' if node_exists(
            te_ext_node(tunnel, 'fast_reroute')) else ''
        path_protection = 'true' if node_exists(
            te_ext_node(tunnel, 'backup')) else ''
        delay_measurement = 'true' if node_exists(
            te_ext_node(tunnel, 'performance_measurement')) else ''

        template = ncs.template.Template(service)
        params = ncs.template.Variables()
        params.add('HEAD_END', head_end)
        params.add('TUNNEL_ID', tunnel_id(tunnel))
        params.add('TUNNEL_NAME', str(tunnel.name))
        params.add('DESCRIPTION', description)
        params.add('SOURCE', source)
        params.add('DESTINATION', destination)
        params.add('SETUP_PRIORITY', setup_priority)
        params.add('HOLD_PRIORITY', hold_priority)
        params.add('BANDWIDTH', bandwidth)
        params.add('PATH_SELECTION_METRIC', metric)
        params.add('FAST_REROUTE', fast_reroute)
        params.add('PATH_PROTECTION', path_protection)
        params.add('DELAY_MEASUREMENT', delay_measurement)
        template.apply('ietf-te-tunnel-base', params)

        path_count = 0
        for path in tunnel.primary_paths.primary_path:
            path_count += 1
            preference = int(child_text(path, 'preference') or path_count)
            path_name = explicit_path_name(
                str(tunnel.name),
                child_text(path, 'name') or f'path-{preference}',
                preference,
            )

            params = ncs.template.Variables()
            params.add('HEAD_END', head_end)
            params.add('TUNNEL_ID', tunnel_id(tunnel))
            params.add('TUNNEL_NAME', str(tunnel.name))
            params.add('PREFERENCE', preference)
            params.add('JUNOS_PRIMARY', 'true' if path_count == 1 else '')
            params.add('JUNOS_SECONDARY', '' if path_count == 1 else 'true')

            if is_path_method(path, 'path-explicitly-defined'):
                params.add('EXPLICIT_NAME', path_name)
                params.add('EXPLICIT_PATH', 'true')
                params.add('USE_PCE', '')
                template.apply('ietf-te-path-option', params)

                try:
                    route_objects = path.explicit_route_objects
                    hops = route_objects.route_object_include_exclude
                except Exception:
                    hops = []
                if not list(hops):
                    raise ValidationError(
                        f'Explicit path '
                        f'{child_text(path, "name") or preference} '
                        'must include at least one route object'
                    )

                for hop in hops:
                    usage = child_text(hop, 'explicit_route_usage')
                    if usage and 'route-exclude-object' in usage:
                        raise ValidationError(
                            'Explicit exclude hops are not supported in this '
                            'example'
                        )

                    params = ncs.template.Variables()
                    params.add('HEAD_END', head_end)
                    params.add('EXPLICIT_NAME', path_name)
                    params.add('INDEX', child_text(hop, 'index'))
                    params.add('KEYWORD', '')
                    params.add('HOP_TYPE', '')
                    params.add('NODE_ID', '')
                    params.add('LABEL', '')

                    numbered_hop = maybe_attr(hop, 'numbered_node_hop')
                    label_hop = maybe_attr(hop, 'label_hop')
                    if node_exists(numbered_hop):
                        hop_type = (
                            child_text(numbered_hop, 'hop_type') or 'strict'
                        )
                        params.add('KEYWORD', 'next-address')
                        params.add('HOP_TYPE', hop_type)
                        params.add(
                            'NODE_ID', child_text(numbered_hop, 'node_id')
                        )
                    elif node_exists(label_hop):
                        if kind == 'junos':
                            raise ValidationError(
                                'Junos RSVP-TE rendering supports '
                                'numbered-node-hop '
                                'explicit route objects only'
                            )
                        generic = child_text(label_hop.te_label, 'generic')
                        if generic is None:
                            raise ValidationError(
                                'Only generic TE label hops are supported'
                            )
                        params.add('KEYWORD', 'next-label')
                        params.add('LABEL', decode_generic_label(generic))
                    else:
                        raise ValidationError(
                            'Only numbered-node-hop and label-hop are '
                            'supported'
                        )

                    template.apply('ietf-te-explicit-path-ro', params)
            else:
                pce_queried = is_path_method(
                    path, 'path-externally-queried'
                )
                if kind == 'junos' and pce_queried:
                    raise ValidationError(
                        'Junos RSVP-TE rendering in this example does not '
                        'support '
                        'externally queried/PCE path options'
                    )
                params.add('EXPLICIT_NAME', path_name)
                params.add('EXPLICIT_PATH', '')
                params.add('USE_PCE', 'true' if pce_queried else '')
                template.apply('ietf-te-path-option', params)

        if path_count == 0:
            raise ValidationError('At least one primary-path is required')

        steering = te_ext_node(tunnel, 'traffic_steering')
        autoroute = maybe_attr(steering, 'autoroute')
        if node_exists(autoroute):
            announce = maybe_attr(autoroute, 'announce')
            announce_enabled = child_text(announce, 'enable')
            params = ncs.template.Variables()
            params.add('HEAD_END', head_end)
            params.add('TUNNEL_ID', tunnel_id(tunnel))
            params.add('TUNNEL_NAME', str(tunnel.name))
            params.add(
                'AUTOROUTE_ANNOUNCE',
                '' if announce_enabled == 'false' else 'true',
            )
            ncs.template.Template(autoroute).apply('ietf-te-autoroute', params)

        forwarding = maybe_attr(steering, 'forwarding_adjacency')
        if node_exists(forwarding):
            if kind != 'xr':
                raise ValidationError(
                    'Forwarding adjacency rendering is currently available '
                    'for IOS XR head-ends only'
                )
            params = ncs.template.Variables()
            params.add('HEAD_END', head_end)
            params.add('TUNNEL_ID', tunnel_id(tunnel))
            ncs.template.Template(forwarding).apply(
                'ietf-te-forwarding-adjacency', params)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('te-tunnel Main RUNNING')
        self.register_service('te-tunnel-servicepoint', IetfTeCallbacks)
        self.register_validation(
            'te-tunnel-service-validation', IetfTeValidator
        )
        for actionpoint in (
            'te-tunnel-cleanup',
            'te-tunnel-error-recovery',
            'te-tunnel-service-error-recovery',
            'te-tunnel-self-test',
            'te-tunnel-internal-plan-change-handler',
            'te-tunnel-internal-fp-configurations',
        ):
            self.register_action(actionpoint, CompatibilityAction)

    def teardown(self):
        self.log.info('te-tunnel Main FINISHED')
