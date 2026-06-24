# -*- mode: python; python-indent: 4 -*-
import heapq
import importlib
import re
import sys
from collections import defaultdict
from pathlib import Path

import ncs
from ncs.application import Application
from ncs.dp import Action


UNSET = ('', 'None', 'none')
HEALTH_RANK = {
    'normal': 0,
    'degraded': 1,
    'down': 2,
}
DEGRADED_DELAY_PENALTY = 5000
DEGRADED_COST_PENALTY = 100000
LOSS_COST_PENALTY = 25000
DEFAULT_COLOR_BASE = 3400
DEFAULT_COLOR_POOL = 'transport-sr-colors'
COLOR_ALLOC_BACKEND_AUTO = 'auto'
COLOR_ALLOC_BACKEND_RM = 'resource-manager'
COLOR_ALLOC_BACKEND_LOCAL = 'local'


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


def int_value(value, default=0) -> int:
    try:
        rendered = str(value)
        if rendered in UNSET:
            return default
        return int(rendered)
    except Exception:
        return default


def normalized_health_state(value) -> str:
    state = enum_text(value, 'normal')
    return state if state in HEALTH_RANK else 'normal'


def safe_name(*parts, limit=24) -> str:
    raw = '-'.join(str(part) for part in parts if str(part))
    safe = re.sub(r'[^A-Za-z0-9-]+', '-', raw).strip('-')
    return (safe or 'nss-service')[:limit]


def safe_allocation_name(*parts, limit=128) -> str:
    raw = '-'.join(str(part) for part in parts if str(part))
    safe = re.sub(r'[^A-Za-z0-9_.:-]+', '-', raw).strip('-')
    return (safe or 'transport-color')[:limit]


def find_rm_python_dir():
    module_path = Path(__file__).resolve()
    for parent in module_path.parents:
        candidate = parent / 'resource-manager' / 'python'
        if (candidate / 'resource_manager').is_dir():
            return candidate
    return None


def get_rm_allocator():
    try:
        from resource_manager.service import Allocator
        return Allocator
    except ModuleNotFoundError as exc:
        if exc.name not in ('resource_manager', 'resource_manager.service'):
            raise

        rm_python = find_rm_python_dir()
        if rm_python is not None:
            rm_python_path = str(rm_python)
            if rm_python_path not in sys.path:
                sys.path.insert(0, rm_python_path)
            module = importlib.import_module('resource_manager.service')
            return module.Allocator
        raise


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
        return 'ios-xr'
    if 'juniper-junos-netsim-nc' in ned_id:
        return 'junos'
    if 'alu-sr-netsim-cli' in ned_id:
        return 'sros'
    return 'unknown'


def te_headend_supported(root, device_name: str) -> bool:
    return device_kind(root, device_name) in ('ios-xr', 'junos')


def effective_transport_profile(connection_group):
    profile = enum_text(getattr(connection_group, 'transport_profile', ''),
                        'none')
    if profile != 'none':
        return profile
    mode = enum_text(getattr(connection_group, 'transport_mode', ''), 'none')
    if mode == 'odn-template':
        return 'srv6'
    return 'none'


def selected_connection_groups(service, connection_group_name: str):
    groups = []
    for connection_group in service.connection_group:
        if not connection_group_name:
            groups.append(connection_group)
        elif (
            str(connection_group.connection_group_id) == connection_group_name
        ):
            groups.append(connection_group)
    return groups


def selected_sdps(service, connection_group):
    selected = {str(sdp_id) for sdp_id in connection_group.sdp}
    if not selected:
        return list(service.sdp)
    return [sdp for sdp in service.sdp if str(sdp.sdp_id) in selected]


def device_for_sdp(root, sdp):
    port = root.inventory.port[str(sdp.attachment_circuit_id)]
    return str(port.device)


def enabled_core_devices(root):
    devices = []
    for device in root.core_network.devices:
        try:
            if not bool(device.enabled):
                continue
        except Exception:
            pass
        devices.append(str(device.name))
    return sorted(devices)


def configured_srv6_devices(root):
    try:
        return sorted(str(service.name)
                      for service in root.core_network.services.srv6_node)
    except Exception:
        return []


def canonical_link_key(device_a: str, interface_a: str,
                       device_b: str, interface_b: str):
    endpoints = sorted(((device_a, interface_a), (device_b, interface_b)))
    return tuple(endpoints)


def health_state_worse(left: str, right: str) -> str:
    if HEALTH_RANK.get(left, 0) >= HEALTH_RANK.get(right, 0):
        return left
    return right


def link_health_inputs(root):
    inputs = {}
    try:
        entries = root.core_network.services.transport_health.link
    except Exception:
        return inputs
    for entry in entries:
        key = canonical_link_key(
            str(entry.device_a), str(entry.interface_a),
            str(entry.device_b), str(entry.interface_b)
        )
        inputs[key] = {
            'name': str(entry.name),
            'state': normalized_health_state(entry.state),
            'measured_delay': int_value(entry.measured_delay_microseconds),
            'loss': int_value(entry.loss_per_million),
            'reason': text(getattr(entry, 'reason', '')),
        }
    return inputs


def topology_metrics(root):
    physical_links = 0
    directed_srlgs = 0
    highest_delay = 0
    for link in root.core_network.links:
        try:
            if not bool(link.enabled):
                continue
        except Exception:
            pass
        physical_links += 1
        try:
            highest_delay = max(highest_delay, int(str(link.te_delay_metric)))
        except Exception:
            pass
        try:
            directed_srlgs += len([value for value in link.srlg]) * 2
        except Exception:
            pass
    return physical_links * 2, directed_srlgs, highest_delay


def enabled_te_edges(root):
    edges = []
    health_inputs = link_health_inputs(root)
    summary = {
        'impaired_links': 0,
        'blocked_links': 0,
        'health_inputs': len(health_inputs),
    }
    for link in root.core_network.links:
        try:
            if not bool(link.enabled):
                continue
        except Exception:
            pass

        device_a = str(link.device_a)
        device_b = str(link.device_b)
        interface_a = str(link.interface_a)
        interface_b = str(link.interface_b)
        link_id = f'{device_a}:{interface_a}<->{device_b}:{interface_b}'
        health = health_inputs.get(
            canonical_link_key(device_a, interface_a, device_b, interface_b),
            {}
        )
        health_state = normalized_health_state(health.get('state', 'normal'))
        if health_state == 'down':
            summary['blocked_links'] += 2
            continue

        configured_delay = int_value(link.te_delay_metric, 1000)
        measured_delay = int_value(health.get('measured_delay', 0))
        effective_delay = measured_delay or configured_delay
        if health_state == 'degraded' and not measured_delay:
            effective_delay += DEGRADED_DELAY_PENALTY

        loss = int_value(health.get('loss', 0))
        health_penalty = 0
        if health_state == 'degraded':
            health_penalty += DEGRADED_COST_PENALTY
        if loss:
            health_penalty += LOSS_COST_PENALTY
        if health_state != 'normal' or loss or measured_delay:
            summary['impaired_links'] += 2

        srlgs = {int_value(value) for value in getattr(link, 'srlg', [])}
        common = {
            'link_id': link_id,
            'default_metric': int_value(link.te_default_metric, 10),
            'igp_metric': int_value(link.te_igp_metric, 10),
            'delay': effective_delay,
            'configured_delay': configured_delay,
            'srlgs': srlgs,
            'health_state': health_state,
            'health_name': text(health.get('name', '')),
            'health_reason': text(health.get('reason', '')),
            'loss': loss,
            'health_penalty': health_penalty,
        }
        edges.append({
            **common,
            'src': device_a,
            'dst': device_b,
            'src_int': interface_a,
            'dst_int': interface_b,
        })
        edges.append({
            **common,
            'src': device_b,
            'dst': device_a,
            'src_int': interface_b,
            'dst_int': interface_a,
        })
    return edges, summary


def edge_cost(edge, metric_type: str) -> int:
    if metric_type == 'igp':
        base = edge['igp_metric']
    elif metric_type == 'te':
        base = edge['default_metric']
    else:
        base = edge['delay']
    return base + edge.get('health_penalty', 0)


def shortest_path(edges, source: str, target: str, metric_type: str,
                  forbidden_srlgs=None, forbidden_links=None):
    forbidden_srlgs = forbidden_srlgs or set()
    forbidden_links = forbidden_links or set()
    adjacency = defaultdict(list)
    for edge in edges:
        if edge['link_id'] in forbidden_links:
            continue
        if forbidden_srlgs and edge['srlgs'] & forbidden_srlgs:
            continue
        adjacency[edge['src']].append(edge)

    queue = []
    counter = 0
    heapq.heappush(queue, (0, counter, source, []))
    best = {}
    while queue:
        cost, _, node, path = heapq.heappop(queue)
        if node == target:
            return path
        if node in best and best[node] <= cost:
            continue
        best[node] = cost
        seen = {edge['src'] for edge in path}
        seen.add(node)
        for edge in adjacency.get(node, []):
            if edge['dst'] in seen:
                continue
            counter += 1
            heapq.heappush(
                queue,
                (cost + edge_cost(edge, metric_type), counter,
                 edge['dst'], path + [edge])
            )
    return []


def path_delay(path) -> int:
    return sum(edge['delay'] for edge in path)


def path_srlgs(path):
    srlgs = set()
    for edge in path:
        srlgs.update(edge['srlgs'])
    return srlgs


def path_links(path):
    return {edge['link_id'] for edge in path}


def path_devices(path):
    if not path:
        return ''
    devices = [path[0]['src']]
    devices.extend(edge['dst'] for edge in path)
    return ' -> '.join(devices)


def path_health_state(path) -> str:
    state = 'normal'
    for edge in path:
        state = health_state_worse(state, edge.get('health_state', 'normal'))
        if int_value(edge.get('loss', 0)):
            state = health_state_worse(state, 'degraded')
    return state


def path_health_summary(path) -> str:
    if not path:
        return 'none'
    state = path_health_state(path)
    details = []
    for edge in path:
        edge_state = edge.get('health_state', 'normal')
        loss = int_value(edge.get('loss', 0))
        if edge_state == 'normal' and not loss:
            continue
        reason = edge.get('health_reason') or edge.get('health_name')
        detail = f'{edge["link_id"]}:{edge_state}'
        if loss:
            detail += f'/loss={loss}ppm'
        if reason:
            detail += f' ({reason})'
        details.append(detail)
    if not details:
        return state
    return f'{state}; ' + '; '.join(details)


def compute_candidate_paths(edges, pe_devices, metric_type: str):
    if len(pe_devices) != 2:
        return {
            'primary': [],
            'backup': [],
            'srlg_disjoint_backup': False,
        }
    source, target = pe_devices
    primary = shortest_path(edges, source, target, metric_type)
    backup = []
    srlg_disjoint = False
    if primary:
        backup = shortest_path(
            edges, source, target, metric_type,
            forbidden_srlgs=path_srlgs(primary)
        )
        srlg_disjoint = bool(backup)
        if not backup:
            backup = shortest_path(
                edges, source, target, metric_type,
                forbidden_links=path_links(primary)
            )
    return {
        'primary': primary,
        'backup': backup,
        'srlg_disjoint_backup': srlg_disjoint,
    }


def slo_template_values(root, connection_group):
    values = {
        'latency_bound_us': 0,
        'isolation': 'shared',
    }
    template_name = text(getattr(connection_group, 'slo_sle_template', ''))
    if not template_name:
        return values
    try:
        template = (
            root.network_slice_services.slo_sle_templates
            .slo_sle_template[template_name]
        )
        values['latency_bound_us'] = (
            int_value(getattr(template, 'latency_bound', 0)) * 1000
        )
        values['isolation'] = enum_text(
            getattr(template, 'isolation', ''), 'shared'
        )
    except Exception:
        pass
    return values


def matching_service_health(root, service_id: str, cg_id: str,
                            transport_profile: str, phase: str):
    records = []
    try:
        entries = root.core_network.services.transport_health.service
    except Exception:
        return records
    for entry in entries:
        if str(entry.slice_service) != service_id:
            continue
        if str(entry.connection_group) != cg_id:
            continue
        if enum_text(entry.transport_profile, '') != transport_profile:
            continue
        entry_phase = enum_text(entry.phase, 'always')
        if entry_phase not in ('always', phase):
            continue
        records.append({
            'state': normalized_health_state(entry.state),
            'delay': int_value(entry.measured_delay_microseconds),
            'loss': int_value(entry.loss_per_million),
            'phase': entry_phase,
            'reason': text(getattr(entry, 'reason', '')),
        })
    return records


def service_health_summary(records):
    if not records:
        return {
            'state': 'normal',
            'inputs': 0,
            'delay': 0,
            'loss': 0,
            'reasons': [],
        }
    state = 'normal'
    delay = 0
    loss = 0
    reasons = []
    for record in records:
        state = health_state_worse(state, record['state'])
        if record['loss']:
            state = health_state_worse(state, 'degraded')
        delay = max(delay, record['delay'])
        loss = max(loss, record['loss'])
        if record['reason']:
            reasons.append(record['reason'])
    return {
        'state': state,
        'inputs': len(records),
        'delay': delay,
        'loss': loss,
        'reasons': reasons,
    }


def service_health_text(summary) -> str:
    if not summary['inputs']:
        return 'normal'
    detail = summary['state']
    if summary['delay']:
        detail += f', delay={summary["delay"]}usec'
    if summary['loss']:
        detail += f', loss={summary["loss"]}ppm'
    if summary['reasons']:
        detail += f', reason={"; ".join(summary["reasons"])}'
    return detail


def create_finding(output, state, severity: str, area: str, message: str):
    entry = output.finding.create(state['finding_id'])
    state['finding_id'] += 1
    entry.severity = severity
    entry.area = area
    entry.message = message
    if severity == 'error':
        state['errors'] += 1
    elif severity == 'warning':
        state['warnings'] += 1


def create_step(output, state, command: str, reason: str):
    entry = output.recommended_step.create(state['step_id'])
    state['step_id'] += 1
    entry.command = command
    entry.reason = reason


def pm_profile_count(root, service_id: str) -> int:
    services = root.core_network.services
    pm_name = safe_name('nss', service_id, 'pm')
    try:
        return 1 if pm_name in services.pm_profile else 0
    except Exception:
        return 0


def assurance_monitor_count(
    root, service_id: str, connection_groups, pe_devices
):
    services = root.core_network.services
    count = 0
    try:
        monitors = services.assurance_monitor
    except Exception:
        return 0
    for connection_group in connection_groups:
        cg_id = str(connection_group.connection_group_id)
        for device in pe_devices:
            name = safe_name('NSS', service_id, cg_id, device, limit=64)
            if name in monitors:
                count += 1
    return count


def sr_color_pool(root, pool_name: str):
    pool_name = text(pool_name)
    if not pool_name:
        return None
    try:
        pools = root.core_network.services
        pools = pools.transport_resource_pools.sr_color_pool
        if pool_name in pools:
            return pools[pool_name]
    except Exception:
        pass
    try:
        pools = root.core_network.services
        pools = pools.transport_resource_pools.sr_color_pool
        for pool in pools:
            if str(pool.name) == pool_name:
                return pool
    except Exception:
        pass
    return None


def pool_bounds(pool):
    start = int_value(getattr(pool, 'start_color', DEFAULT_COLOR_BASE),
                      DEFAULT_COLOR_BASE)
    end = int_value(getattr(pool, 'end_color', start), start)
    return start, end


def sr_color_pool_allocation(pool, color):
    if pool is None or not color:
        return None
    color = int_value(color)
    try:
        return pool.allocation[color]
    except Exception:
        pass
    try:
        return pool.allocation[str(color)]
    except Exception:
        pass
    try:
        for allocation in pool.allocation:
            if int_value(allocation.color) == color:
                return allocation
    except Exception:
        pass
    return None


def create_sr_color_allocation(pool, color):
    try:
        return pool.allocation.create(color)
    except Exception:
        return pool.allocation.create(str(color))


def delete_sr_color_allocation(pool, color):
    color = int_value(color)
    for key in (color, str(color)):
        try:
            del pool.allocation[key]
            return
        except Exception:
            pass
        try:
            pool.allocation.delete(key)
            return
        except Exception:
            pass


def allocation_matches(allocation, service_id: str, connection_group: str):
    if allocation is None:
        return False
    return (
        str(allocation.service_id) == service_id and
        str(allocation.connection_group) == connection_group
    )


def allocated_color_for(root, pool_name: str, service_id: str,
                        connection_group: str) -> int:
    pool = sr_color_pool(root, pool_name)
    if pool is None:
        return 0
    try:
        for allocation in pool.allocation:
            if allocation_matches(allocation, service_id, connection_group):
                return int_value(allocation.color)
    except Exception:
        pass
    return 0


def rm_id_pool(root, pool_name: str):
    pool_name = text(pool_name)
    if not pool_name:
        return None
    try:
        pools = root.ralloc__resource_pools.idalloc__id_pool
        if pool_name in pools:
            return pools[pool_name]
    except Exception:
        pass
    try:
        for pool in root.ralloc__resource_pools.idalloc__id_pool:
            if str(pool.name) == pool_name:
                return pool
    except Exception:
        pass
    return None


def rm_allocator_available() -> bool:
    try:
        get_rm_allocator()
        return True
    except ModuleNotFoundError:
        return False


def rm_allocation_name(service_id: str, cg_id: str) -> str:
    return safe_allocation_name('transport-color', service_id, cg_id)


def rm_allocation(pool, allocation_name: str):
    if pool is None or not allocation_name:
        return None
    try:
        if pool.allocation.exists(allocation_name):
            return pool.allocation[allocation_name]
    except Exception:
        pass
    try:
        return pool.allocation[allocation_name]
    except Exception:
        pass
    try:
        for allocation in pool.allocation:
            if str(allocation.id) == allocation_name:
                return allocation
    except Exception:
        pass
    return None


def allocated_rm_color_for(root, pool_name: str, service_id: str,
                           cg_id: str) -> int:
    pool = rm_id_pool(root, pool_name)
    allocation = rm_allocation(pool, rm_allocation_name(service_id, cg_id))
    if allocation is None:
        return 0
    return int_value(allocation.resource)


def rm_pool_allocated_colors(root, pool_name: str) -> set:
    colors = set()
    pool = rm_id_pool(root, pool_name)
    if pool is None:
        return colors
    try:
        for allocation in pool.allocation:
            color = int_value(allocation.resource)
            if color:
                colors.add(color)
    except Exception:
        pass
    return colors


def used_sr_colors(root) -> set:
    colors = set()
    try:
        for service in root.network_slice_services.slice_service:
            for connection_group in service.connection_group:
                color = int_value(getattr(connection_group, 'color', 0))
                if color:
                    colors.add(color)
    except Exception:
        pass
    try:
        pools = root.core_network.services
        pools = pools.transport_resource_pools.sr_color_pool
        for pool in pools:
            for allocation in pool.allocation:
                color = int_value(allocation.color)
                if color:
                    colors.add(color)
    except Exception:
        pass
    try:
        for template in root.core_network.services.odn_template:
            color = int_value(template.color)
            if color:
                colors.add(color)
    except Exception:
        pass
    try:
        for attachment in root.core_network.services.odn_color_attachment:
            color = int_value(attachment.color)
            if color:
                colors.add(color)
    except Exception:
        pass
    return colors


def local_color_pool_stats(root, pool_name: str):
    stats = {
        'pool': text(pool_name),
        'backend': 'local-pool',
        'exists': False,
        'allocated': 0,
        'available': 0,
        'start': 0,
        'end': 0,
    }
    pool = sr_color_pool(root, pool_name)
    if pool is None:
        return stats
    start, end = pool_bounds(pool)
    stats.update({
        'exists': True,
        'start': start,
        'end': end,
    })
    try:
        stats['allocated'] = sum(1 for _ in pool.allocation)
    except Exception:
        stats['allocated'] = 0
    if start > end:
        return stats
    used = {color for color in used_sr_colors(root) if start <= color <= end}
    stats['available'] = max(0, (end - start + 1) - len(used))
    return stats


def rm_color_pool_stats(root, pool_name: str):
    stats = {
        'pool': text(pool_name),
        'backend': 'resource-manager',
        'exists': False,
        'allocated': 0,
        'available': 0,
        'start': 0,
        'end': 0,
    }
    pool = rm_id_pool(root, pool_name)
    if pool is None:
        return stats
    try:
        start = int_value(pool.range.start)
        end = int_value(pool.range.end)
    except Exception:
        return stats
    stats.update({
        'exists': True,
        'start': start,
        'end': end,
    })
    try:
        stats['allocated'] = sum(1 for _ in pool.allocation)
    except Exception:
        stats['allocated'] = 0
    if start > end:
        return stats
    used = (
        {color for color in used_sr_colors(root) if start <= color <= end} |
        {color for color in rm_pool_allocated_colors(root, pool_name)
         if start <= color <= end}
    )
    stats['available'] = max(0, (end - start + 1) - len(used))
    return stats


def resolve_allocation_backend(root, color_pool_name: str,
                               allocation_backend: str) -> str:
    backend = enum_text(allocation_backend, COLOR_ALLOC_BACKEND_AUTO)
    if backend == COLOR_ALLOC_BACKEND_RM:
        return COLOR_ALLOC_BACKEND_RM
    if backend == COLOR_ALLOC_BACKEND_LOCAL:
        return COLOR_ALLOC_BACKEND_LOCAL
    if (
        rm_allocator_available()
        and rm_id_pool(root, color_pool_name) is not None
    ):
        return COLOR_ALLOC_BACKEND_RM
    return COLOR_ALLOC_BACKEND_LOCAL


def color_pool_stats(root, pool_name: str, allocation_backend='auto'):
    backend = resolve_allocation_backend(root, pool_name, allocation_backend)
    if backend == COLOR_ALLOC_BACKEND_RM:
        return rm_color_pool_stats(root, pool_name)
    return local_color_pool_stats(root, pool_name)


def resolve_transport_color(
    root, service_id: str, cg_id: str, connection_group,
    target_profile: str, target_color='',
    color_pool_name=DEFAULT_COLOR_POOL, color_base=DEFAULT_COLOR_BASE,
    offset=0, reserved_colors=None, allocation_backend='auto'
):
    result = {
        'color': '',
        'color_int': 0,
        'source': 'none',
        'pool': text(color_pool_name),
        'backend': enum_text(allocation_backend, COLOR_ALLOC_BACKEND_AUTO),
        'reason': '',
    }
    if target_profile not in ('sr-mpls', 'srv6'):
        result['reason'] = 'Target profile does not use SR color steering.'
        return result

    explicit = text(target_color)
    if explicit:
        result.update({
            'color': explicit,
            'color_int': int_value(explicit),
            'source': 'explicit',
            'reason': 'Explicit target color supplied by the operator.',
        })
        return result

    existing = text(getattr(connection_group, 'color', ''))
    if existing:
        result.update({
            'color': existing,
            'color_int': int_value(existing),
            'source': 'existing',
            'reason': 'Existing connection-group color is preserved.',
        })
        return result

    backend = resolve_allocation_backend(root, color_pool_name,
                                         allocation_backend)
    result['backend'] = backend

    allocated = allocated_color_for(root, color_pool_name, service_id, cg_id)
    if backend == COLOR_ALLOC_BACKEND_LOCAL and allocated:
        result.update({
            'color': str(allocated),
            'color_int': allocated,
            'source': 'local-pool',
            'reason': (
                f'Reusing existing local allocation from {color_pool_name}.'
            ),
        })
        return result

    rm_allocated = allocated_rm_color_for(root, color_pool_name,
                                          service_id, cg_id)
    if backend == COLOR_ALLOC_BACKEND_RM and rm_allocated:
        result.update({
            'color': str(rm_allocated),
            'color_int': rm_allocated,
            'source': 'resource-manager',
            'reason': (
                f'Reusing Resource Manager allocation '
                f'{rm_allocation_name(service_id, cg_id)} from '
                f'{color_pool_name}.'
            ),
        })
        return result

    if backend == COLOR_ALLOC_BACKEND_RM:
        if not rm_allocator_available():
            result.update({
                'source': 'resource-manager-unavailable',
                'reason': (
                    'Resource Manager 5 Python API is not available; no '
                    'fallback is used when color-allocation-backend is '
                    'resource-manager. Use auto or local to fall back to the '
                    'local transport-resource-pools model.'
                ),
            })
            return result
        pool = rm_id_pool(root, color_pool_name)
        if pool is None:
            result.update({
                'source': 'resource-manager-pool-missing',
                'reason': (
                    f'Resource Manager ID pool {color_pool_name} is not '
                    'configured.'
                ),
            })
            return result
        try:
            start = int_value(pool.range.start)
            end = int_value(pool.range.end)
        except Exception:
            result.update({
                'source': 'resource-manager-pool-invalid',
                'reason': (
                    f'Resource Manager ID pool {color_pool_name} has no '
                    'usable range.'
                ),
            })
            return result
        reserved = {int_value(color) for color in (reserved_colors or set())}
        used = (
            used_sr_colors(root) |
            rm_pool_allocated_colors(root, color_pool_name) |
            {color for color in reserved if color}
        )
        for color in range(start, end + 1):
            if color not in used:
                result.update({
                    'color': str(color),
                    'color_int': color,
                    'source': 'resource-manager',
                    'reason': (
                        f'First free color from Resource Manager ID pool '
                        f'{color_pool_name}.'
                    ),
                })
                return result
        result.update({
            'source': 'resource-manager-pool-exhausted',
            'reason': (
                f'Resource Manager ID pool {color_pool_name} has no free '
                'colors.'
            ),
        })
        return result

    pool = sr_color_pool(root, color_pool_name)
    if pool is not None:
        start, end = pool_bounds(pool)
        reserved = {int_value(color) for color in (reserved_colors or set())}
        used = used_sr_colors(root) | {color for color in reserved if color}
        for color in range(start, end + 1):
            if color not in used:
                result.update({
                    'color': str(color),
                    'color_int': color,
                    'source': 'local-pool',
                    'reason': (
                        f'First free color from local pool {color_pool_name}.'
                    ),
                })
                return result
        result.update({
            'source': 'local-pool-exhausted',
            'reason': (
                f'Local SR color pool {color_pool_name} has no free colors.'
            ),
        })
        return result

    fallback = int_value(color_base, DEFAULT_COLOR_BASE) + int_value(offset)
    result.update({
        'color': str(fallback),
        'color_int': fallback,
        'source': 'fallback-base',
        'reason': (
            f'Color pool {color_pool_name} is not configured; using fallback '
            f'base color {fallback}.'
        ),
    })
    return result


def reserve_transport_color(root, pool_name: str, service_id: str, cg_id: str,
                            target_profile: str, color_info):
    if color_info.get('source') == 'resource-manager':
        Allocator = get_rm_allocator()
        service = root.network_slice_services.slice_service[service_id]
        allocation_name = rm_allocation_name(service_id, cg_id)
        pool = rm_id_pool(root, pool_name)
        if pool is None:
            raise ValueError(f'Resource Manager ID pool {pool_name} missing')
        existing = rm_allocation(pool, allocation_name)
        if existing is not None:
            current = int_value(existing.resource)
            if current != color_info['color_int']:
                raise ValueError(
                    f'Resource Manager allocation {allocation_name} already '
                    f'uses color {current}, not {color_info["color_int"]}.'
                )
            return
        request = (
            Allocator(service)
            .id(color_info['color_int'])
            .pool(pool_name)
        )
        request.allocate(allocation_name)
        return

    if color_info.get('source') != 'local-pool':
        return
    pool = sr_color_pool(root, pool_name)
    if pool is None:
        return
    color = color_info['color_int']
    allocation = sr_color_pool_allocation(pool, color)
    if allocation is not None and not allocation_matches(allocation,
                                                         service_id, cg_id):
        raise ValueError(
            f'SR color {color} in pool {pool_name} is already allocated to '
            f'{allocation.service_id}/{allocation.connection_group}.'
        )
    if allocation is None:
        allocation = create_sr_color_allocation(pool, color)
    allocation.service_id = service_id
    allocation.connection_group = cg_id
    allocation.target_profile = target_profile
    allocation.owner = 'transport-advisor'
    allocation.reason = 'Reserved by guarded transport migration.'


def desired_color_value(target_color, connection_group):
    explicit = text(target_color)
    if explicit:
        return explicit
    return text(getattr(connection_group, 'color', ''))


def desired_color(input, connection_group):
    return desired_color_value(getattr(input, 'target_color', ''),
                               connection_group)


def add_underlay_steps(output, state, missing_srv6_devices):
    if not missing_srv6_devices:
        return
    create_step(output, state, 'config',
                'Enter configuration mode for staged SRv6 enablement.')
    for device in missing_srv6_devices:
        create_step(output, state, f'core-network services srv6-node {device}',
                    'Enable SRv6 underlay capability before moving services.')
    create_step(output, state, 'commit dry-run outformat native',
                'Preview the multivendor native SRv6 underlay changes.')
    create_step(output, state, 'commit',
                'Commit the staged underlay turn-up transaction.')
    create_step(output, state, 'top',
                'Return to operational mode before reassessing the slice.')


def add_service_steps(output, state, service_id: str, connection_groups,
                      target_profile: str, input):
    create_step(output, state, 'config',
                'Enter configuration mode for the service transport change.')
    for connection_group in connection_groups:
        cg_id = str(connection_group.connection_group_id)
        create_step(
            output, state,
            f'network-slice-services slice-service {service_id} '
            f'connection-group {cg_id} transport-profile {target_profile}',
            'Change only the service transport intent; NSO computes the '
            'native diff.'
        )
        color = desired_color(input, connection_group)
        if target_profile in ('sr-mpls', 'srv6') and color:
            create_step(
                output, state,
                f'network-slice-services slice-service {service_id} '
                f'connection-group {cg_id} color {color}',
                'Use the assessed SR color for ODN steering.'
            )
    create_step(output, state, 'commit dry-run outformat native',
                'Preview exactly what will change on each vendor device.')
    create_step(output, state, 'commit',
                'Commit the service migration transaction.')
    create_step(output, state, 'top',
                'Return to operational mode for lifecycle checks.')
    create_step(output, state,
                f'network-slice-services slice-service {service_id} '
                'migration-readiness',
                'Confirm the slice lifecycle artifacts after migration.')
    create_step(output, state,
                f'network-slice-services slice-service {service_id} '
                'check-sync',
                'Verify NSO still owns and can reconcile the migrated '
                'service.')


def add_verification_steps(output, state, service_id: str):
    create_step(output, state,
                f'network-slice-services slice-service {service_id} '
                'migration-readiness',
                'Confirm the slice lifecycle artifacts remain complete.')
    create_step(output, state,
                f'network-slice-services slice-service {service_id} '
                'check-sync',
                'Verify the already-migrated service is still in sync.')


def readiness_score(errors: int, warnings: int, missing_srv6: int,
                    target_color_missing: bool, pm_profiles: int,
                    assurance_monitors: int, te_links: int) -> int:
    score = 100
    score -= min(errors * 30, 60)
    score -= min(warnings * 8, 24)
    if missing_srv6:
        score -= 25
    if target_color_missing:
        score -= 20
    if pm_profiles == 0:
        score -= 15
    if assurance_monitors == 0:
        score -= 15
    if te_links == 0:
        score -= 10
    return max(0, min(100, score))


def add_assessment_finding(assessment, severity: str, area: str, message: str):
    assessment['findings'].append({
        'id': len(assessment['findings']) + 1,
        'severity': severity,
        'area': area,
        'message': message,
    })
    if severity == 'error':
        assessment['errors'] += 1
    elif severity == 'warning':
        assessment['warnings'] += 1


def add_assessment_step(assessment, command: str, reason: str):
    assessment['steps'].append({
        'id': len(assessment['steps']) + 1,
        'command': command,
        'reason': reason,
    })


def add_plan_finding(plan, severity: str, area: str, message: str):
    plan['findings'].append({
        'id': len(plan['findings']) + 1,
        'severity': severity,
        'area': area,
        'message': message,
    })
    if severity == 'error':
        plan['errors'] += 1
    elif severity == 'warning':
        plan['warnings'] += 1


def add_underlay_step_records(assessment, missing_srv6_devices):
    if not missing_srv6_devices:
        return
    add_assessment_step(
        assessment, 'config',
        'Enter configuration mode for staged SRv6 enablement.'
    )
    for device in missing_srv6_devices:
        add_assessment_step(
            assessment, f'core-network services srv6-node {device}',
            'Enable SRv6 underlay capability before moving services.'
        )
    add_assessment_step(
        assessment, 'commit dry-run outformat native',
        'Preview the multivendor native SRv6 underlay changes.'
    )
    add_assessment_step(
        assessment, 'commit',
        'Commit the staged underlay turn-up transaction.'
    )
    add_assessment_step(
        assessment, 'top',
        'Return to operational mode before reassessing the slice.'
    )


def add_service_step_records(assessment, service_id: str, connection_groups,
                             target_profile: str, color_resolutions):
    add_assessment_step(
        assessment, 'config',
        'Enter configuration mode for the service transport change.'
    )
    for connection_group in connection_groups:
        cg_id = str(connection_group.connection_group_id)
        add_assessment_step(
            assessment,
            f'network-slice-services slice-service {service_id} '
            f'connection-group {cg_id} transport-profile {target_profile}',
            'Change only the service transport intent; NSO computes the '
            'native diff.'
        )
        color_info = color_resolutions.get(cg_id, {})
        color = text(color_info.get('color', ''))
        if target_profile in ('sr-mpls', 'srv6') and color:
            add_assessment_step(
                assessment,
                f'network-slice-services slice-service {service_id} '
                f'connection-group {cg_id} color {color}',
                f'Use the assessed SR color for ODN steering '
                f'({color_info.get("source", "unknown")}).'
            )
    add_assessment_step(
        assessment, 'commit dry-run outformat native',
        'Preview exactly what will change on each vendor device.'
    )
    add_assessment_step(
        assessment, 'commit',
        'Commit the service migration transaction.'
    )
    add_assessment_step(
        assessment, 'top',
        'Return to operational mode for lifecycle checks.'
    )
    add_assessment_step(
        assessment,
        f'network-slice-services slice-service {service_id} '
        'migration-readiness',
        'Confirm the slice lifecycle artifacts after migration.'
    )
    add_assessment_step(
        assessment,
        f'network-slice-services slice-service {service_id} check-sync',
        'Verify NSO still owns and can reconcile the migrated service.'
    )


def add_verification_step_records(assessment, service_id: str):
    add_assessment_step(
        assessment,
        f'network-slice-services slice-service {service_id} '
        'migration-readiness',
        'Confirm the slice lifecycle artifacts remain complete.'
    )
    add_assessment_step(
        assessment,
        f'network-slice-services slice-service {service_id} check-sync',
        'Verify the already-migrated service is still in sync.'
    )


def recommendation_reason(action: str, service_id: str, cg_id: str,
                          current_profile: str, target_profile: str,
                          primary_path: str, primary_delay: int,
                          latency_bound: int, missing_srv6_devices,
                          blocking_reason: str) -> str:
    if action == 'block':
        return blocking_reason
    if action == 'stage-underlay':
        return (
            'Stage SRv6 underlay first on '
            f'{", ".join(missing_srv6_devices)}; keep {service_id}/{cg_id} '
            f'on {current_profile} until the transport domain is ready.'
        )
    if action == 'no-op':
        return (
            f'{service_id}/{cg_id} already uses {target_profile}; use '
            'readiness and check-sync actions for lifecycle verification.'
        )
    bound_text = (
        f' within {latency_bound} usec bound'
        if latency_bound and primary_delay <= latency_bound
        else ''
    )
    return (
        f'Migrate {service_id}/{cg_id} from {current_profile} to '
        f'{target_profile}; candidate path {primary_path} has '
        f'{primary_delay} usec delay{bound_text}.'
    )


def build_transport_plan(
    root, service_filter: str, connection_group_filter: str,
    target_profile: str, color_base: int, color_pool_name: str,
    allocation_backend: str, max_services_per_wave: int,
    require_srv6_underlay: bool, include_no_op: bool
):
    te_links, srlgs, _ = topology_metrics(root)
    pool_stats = color_pool_stats(root, color_pool_name, allocation_backend)
    core_devices = enabled_core_devices(root)
    srv6_devices = configured_srv6_devices(root)
    missing_srv6_devices = []
    if target_profile == 'srv6':
        missing_srv6_devices = sorted(set(core_devices) - set(srv6_devices))

    plan = {
        'decision': 'no-candidates',
        'target_profile': target_profile,
        'candidate_count': 0,
        'migrate_count': 0,
        'stage_underlay_count': 0,
        'blocked_count': 0,
        'no_op_count': 0,
        'te_links': te_links,
        'srlgs': srlgs,
        'impaired_links': 0,
        'blocked_links': 0,
        'missing_srv6_devices': missing_srv6_devices,
        'color_pool': text(color_pool_name),
        'allocated_colors': pool_stats['allocated'],
        'available_colors': pool_stats['available'],
        'recommendations': [],
        'findings': [],
        'errors': 0,
        'warnings': 0,
        'summary': '',
    }

    edges, health_summary = enabled_te_edges(root)
    plan['impaired_links'] = health_summary['impaired_links']
    plan['blocked_links'] = health_summary['blocked_links']
    if not edges:
        add_plan_finding(
            plan, 'warning', 'topology',
            'No enabled TE links were found in core-network.'
        )
    else:
        add_plan_finding(
            plan, 'info', 'topology',
            f'Planning graph has {te_links} directed TE links and {srlgs} '
            'directed SRLG assignments.'
        )
    if health_summary['blocked_links']:
        add_plan_finding(
            plan, 'warning', 'health',
            f'{health_summary["blocked_links"]} directed TE link(s) were '
            'removed from the planning graph by down health input.'
        )
    if health_summary['impaired_links']:
        add_plan_finding(
            plan, 'warning', 'health',
            f'{health_summary["impaired_links"]} directed TE link(s) have '
            'degraded, measured-delay, or loss health input.'
        )

    if target_profile == 'srv6' and missing_srv6_devices:
        add_plan_finding(
            plan, 'warning', 'underlay',
            'SRv6 underlay is not yet enabled on all PE/core nodes: '
            f'{", ".join(missing_srv6_devices)}.'
        )

    try:
        services = root.network_slice_services.slice_service
    except Exception:
        add_plan_finding(
            plan, 'error', 'slice',
            'The NSS service model is not available in this transaction.'
        )
        return plan

    color_offset = 0
    reserved_colors = set()
    for service in services:
        service_id = str(service.service_id)
        if service_filter and service_id != service_filter:
            continue
        for connection_group in service.connection_group:
            cg_id = str(connection_group.connection_group_id)
            if connection_group_filter and cg_id != connection_group_filter:
                continue

            current_profile = effective_transport_profile(connection_group)
            if current_profile == target_profile and not include_no_op:
                continue

            color_info = resolve_transport_color(
                root, service_id, cg_id, connection_group, target_profile,
                '', color_pool_name, color_base, color_offset,
                reserved_colors, allocation_backend
            )
            color = color_info['color']
            if color_info['source'] in ('local-pool', 'resource-manager'):
                reserved_colors.add(color_info['color_int'])
            if color_info['source'] == 'fallback-base':
                color_offset += 1

            assessment = build_assessment(
                root, service_id, cg_id, target_profile, color, False,
                'pre-check', color_pool_name, color_base, allocation_backend
            )
            pe_devices = assessment['pe_devices']
            metric_type = enum_text(
                getattr(connection_group, 'metric_type', ''), 'latency'
            )
            if metric_type not in ('igp', 'te', 'latency'):
                metric_type = 'latency'

            paths = compute_candidate_paths(edges, pe_devices, metric_type)
            primary = paths['primary']
            backup = paths['backup']
            primary_path = path_devices(primary)
            backup_path = path_devices(backup)
            primary_delay = path_delay(primary)
            backup_delay = path_delay(backup)
            primary_health = path_health_summary(primary)
            slo_values = slo_template_values(root, connection_group)
            latency_bound = slo_values['latency_bound_us']

            action = 'migrate'
            blocking_reason = ''
            local_warnings = 0
            if assessment['errors']:
                action = 'block'
                blocking_reason = next(
                    finding['message'] for finding in assessment['findings']
                    if finding['severity'] == 'error'
                )
            elif (
                current_profile == target_profile
                and not missing_srv6_devices
            ):
                action = 'no-op'
            elif len(pe_devices) != 2:
                action = 'block'
                blocking_reason = (
                    'Topology planner currently expects exactly two PE '
                    f'endpoints per connection group; found {len(pe_devices)}.'
                )
                add_plan_finding(plan, 'error', 'topology', blocking_reason)
            elif not primary:
                action = 'block'
                blocking_reason = (
                    f'No TE path was found between {", ".join(pe_devices)}.'
                )
                add_plan_finding(plan, 'error', 'topology', blocking_reason)
            elif (target_profile == 'srv6' and missing_srv6_devices and
                  require_srv6_underlay):
                action = 'stage-underlay'

            if (primary and latency_bound and primary_delay > latency_bound):
                local_warnings += 1
                add_plan_finding(
                    plan, 'warning', 'slo',
                    f'{service_id}/{cg_id} primary path delay '
                    f'{primary_delay} usec exceeds bound {latency_bound} usec.'
                )
            if primary and path_health_state(primary) != 'normal':
                local_warnings += 1
                add_plan_finding(
                    plan, 'warning', 'health',
                    f'{service_id}/{cg_id} primary path health is '
                    f'{primary_health}.'
                )
            if (primary and slo_values['isolation'] == 'dedicated' and
                    not paths['srlg_disjoint_backup']):
                local_warnings += 1
                add_plan_finding(
                    plan, 'warning', 'srlg',
                    f'{service_id}/{cg_id} has dedicated isolation intent but '
                    'no SRLG-disjoint backup path in the current topology.'
                )
            for finding in assessment['findings']:
                if finding['severity'] == 'warning':
                    local_warnings += 1
                    add_plan_finding(
                        plan, 'warning', finding['area'],
                        f'{service_id}/{cg_id}: {finding["message"]}'
                    )
                elif finding['severity'] == 'error':
                    add_plan_finding(
                        plan, 'error', finding['area'],
                        f'{service_id}/{cg_id}: {finding["message"]}'
                    )

            reason = recommendation_reason(
                action, service_id, cg_id, current_profile, target_profile,
                primary_path or 'none', primary_delay, latency_bound,
                missing_srv6_devices, blocking_reason
            )
            recommendation = {
                'id': len(plan['recommendations']) + 1,
                'wave_id': 0,
                'action': action,
                'service_id': service_id,
                'connection_group': cg_id,
                'current_profile': current_profile,
                'target_profile': target_profile,
                'recommended_color': int_value(color),
                'color_source': color_info['source'],
                'color_pool': text(color_pool_name),
                'pe_devices': ', '.join(pe_devices),
                'metric_type': metric_type,
                'primary_path': primary_path,
                'primary_delay': primary_delay,
                'backup_path': backup_path,
                'backup_delay': backup_delay,
                'srlg_disjoint_backup': bool(paths['srlg_disjoint_backup']),
                'path_health': primary_health,
                'latency_bound': latency_bound,
                'readiness_score': assessment['score'],
                'reason': reason,
                'warnings': local_warnings,
            }
            plan['recommendations'].append(recommendation)
            plan['candidate_count'] += 1
            if action == 'migrate':
                plan['migrate_count'] += 1
            elif action == 'stage-underlay':
                plan['stage_underlay_count'] += 1
            elif action == 'block':
                plan['blocked_count'] += 1
            elif action == 'no-op':
                plan['no_op_count'] += 1

    wave_id = 1
    in_wave = 0
    for recommendation in plan['recommendations']:
        if recommendation['action'] != 'migrate':
            continue
        if in_wave >= max_services_per_wave:
            wave_id += 1
            in_wave = 0
        recommendation['wave_id'] = wave_id
        in_wave += 1

    if plan['candidate_count'] == 0:
        decision = 'no-candidates'
    elif plan['blocked_count']:
        decision = 'not-ready'
    elif plan['stage_underlay_count']:
        decision = 'stage-underlay-first'
    elif plan['migrate_count'] == 0:
        decision = 'no-op'
    elif plan['warnings']:
        decision = 'ready-with-warnings'
    else:
        decision = 'ready'
    plan['decision'] = decision
    plan['summary'] = (
        f'TE manager plan: {decision}. Considered '
        f'{plan["candidate_count"]} connection group(s): '
        f'{plan["migrate_count"]} ready to migrate, '
        f'{plan["stage_underlay_count"]} waiting for underlay, '
        f'{plan["blocked_count"]} blocked, {plan["no_op_count"]} no-op. '
        f'Health inputs removed {plan["blocked_links"]} directed TE link(s) '
        f'and impaired {plan["impaired_links"]}. '
        f'Color pool {plan["color_pool"]} via {pool_stats["backend"]}: '
        f'{plan["available_colors"]} available, '
        f'{plan["allocated_colors"]} allocated. '
        f'Missing SRv6 nodes: {", ".join(missing_srv6_devices) or "none"}.'
    )
    return plan


def render_plan_output(output, plan):
    output.decision = plan['decision']
    output.target_profile = plan['target_profile']
    output.candidate_count = plan['candidate_count']
    output.migrate_count = plan['migrate_count']
    output.stage_underlay_count = plan['stage_underlay_count']
    output.blocked_count = plan['blocked_count']
    output.no_op_count = plan['no_op_count']
    output.te_links = plan['te_links']
    output.srlgs = plan['srlgs']
    output.impaired_links = plan['impaired_links']
    output.blocked_links = plan['blocked_links']
    output.missing_srv6_devices = ', '.join(plan['missing_srv6_devices'])
    output.color_pool = plan['color_pool']
    output.allocated_colors = plan['allocated_colors']
    output.available_colors = plan['available_colors']
    output.summary = plan['summary']

    for recommendation in plan['recommendations']:
        entry = output.recommendation.create(recommendation['id'])
        entry.wave_id = recommendation['wave_id']
        entry.action = recommendation['action']
        entry.service_id = recommendation['service_id']
        entry.connection_group = recommendation['connection_group']
        entry.current_profile = recommendation['current_profile']
        entry.target_profile = recommendation['target_profile']
        if recommendation['recommended_color']:
            entry.recommended_color = recommendation['recommended_color']
        entry.color_source = recommendation['color_source']
        entry.color_pool = recommendation['color_pool']
        entry.pe_devices = recommendation['pe_devices']
        entry.metric_type = recommendation['metric_type']
        entry.primary_path = recommendation['primary_path']
        entry.primary_delay_microseconds = recommendation['primary_delay']
        entry.backup_path = recommendation['backup_path']
        entry.backup_delay_microseconds = recommendation['backup_delay']
        entry.srlg_disjoint_backup = recommendation['srlg_disjoint_backup']
        entry.path_health = recommendation['path_health']
        entry.latency_bound_microseconds = recommendation['latency_bound']
        entry.readiness_score = recommendation['readiness_score']
        entry.reason = recommendation['reason']

    for finding in plan['findings']:
        entry = output.finding.create(finding['id'])
        entry.severity = finding['severity']
        entry.area = finding['area']
        entry.message = finding['message']


def build_assessment(root, service_id: str, connection_group_name: str,
                     target_profile: str, target_color='',
                     include_cli=True, health_phase='pre-check',
                     color_pool_name=DEFAULT_COLOR_POOL,
                     color_base=DEFAULT_COLOR_BASE,
                     allocation_backend=COLOR_ALLOC_BACKEND_AUTO):
    assessment = {
        'service_id': service_id,
        'target_profile': target_profile,
        'current_profiles': [],
        'connection_group_ids': [],
        'sdp_count': 0,
        'pe_devices': [],
        'srv6_enabled_devices': 0,
        'missing_srv6_devices': [],
        'pm_profiles': 0,
        'assurance_monitors': 0,
        'te_links': 0,
        'srlgs': 0,
        'impaired_links': 0,
        'blocked_links': 0,
        'healthy_te_path': False,
        'primary_path': '',
        'primary_delay': 0,
        'path_health': 'none',
        'transport_health_state': 'normal',
        'resolved_color': 0,
        'color_source': 'none',
        'color_pool': text(color_pool_name),
        'color_resolutions': {},
        'highest_delay': 0,
        'all_profiles_match': False,
        'target_color_missing': False,
        'findings': [],
        'steps': [],
        'errors': 0,
        'warnings': 0,
        'decision': 'not-ready',
        'score': 0,
        'summary': '',
    }

    services = root.network_slice_services.slice_service
    if service_id not in services:
        add_assessment_finding(
            assessment, 'error', 'slice',
            f'No NSS slice-service named {service_id} exists.'
        )
        assessment['summary'] = f'Slice {service_id} was not found.'
        return assessment

    service = services[service_id]
    connection_groups = selected_connection_groups(service,
                                                   connection_group_name)
    if not connection_groups:
        add_assessment_finding(
            assessment, 'error', 'slice',
            f'No connection-group {connection_group_name} exists in slice '
            f'{service_id}.'
        )

    all_sdps = []
    color_offset = 0
    reserved_colors = set()
    for connection_group in connection_groups:
        cg_id = str(connection_group.connection_group_id)
        assessment['connection_group_ids'].append(cg_id)
        profile = effective_transport_profile(connection_group)
        assessment['current_profiles'].append(f'{cg_id}:{profile}')
        sdps = selected_sdps(service, connection_group)
        all_sdps.extend(sdps)
        if len(sdps) < 2:
            add_assessment_finding(
                assessment, 'error', 'slice',
                f'Connection-group {cg_id} has fewer than two SDPs.'
            )
        color_info = resolve_transport_color(
            root, service_id, cg_id, connection_group, target_profile,
            target_color, color_pool_name, color_base, color_offset,
            reserved_colors, allocation_backend
        )
        assessment['color_resolutions'][cg_id] = color_info
        if color_info['source'] in ('local-pool', 'resource-manager'):
            reserved_colors.add(color_info['color_int'])
        if color_info['source'] == 'fallback-base':
            color_offset += 1
        if color_info['color_int'] and not assessment['resolved_color']:
            assessment['resolved_color'] = color_info['color_int']
            assessment['color_source'] = color_info['source']
        if (target_profile in ('sr-mpls', 'srv6') and
                not color_info['color']):
            assessment['target_color_missing'] = True
            add_assessment_finding(
                assessment, 'error', 'transport',
                f'Connection-group {cg_id} needs an SR color for '
                f'{target_profile}: {color_info["reason"]}'
            )
        elif target_profile in ('sr-mpls', 'srv6'):
            add_assessment_finding(
                assessment, 'info', 'transport',
                f'Connection-group {cg_id} resolved SR color '
                f'{color_info["color"]} from {color_info["source"]}: '
                f'{color_info["reason"]}'
            )
        slo_values = slo_template_values(root, connection_group)
        latency_bound = slo_values['latency_bound_us']
        health_records = matching_service_health(
            root, service_id, cg_id, target_profile, health_phase
        )
        service_health = service_health_summary(health_records)
        assessment['transport_health_state'] = health_state_worse(
            assessment['transport_health_state'], service_health['state']
        )
        if health_records:
            health_detail = service_health_text(service_health)
            if service_health['state'] == 'down':
                add_assessment_finding(
                    assessment, 'error', 'health',
                    f'{service_id}/{cg_id} {target_profile} canary health '
                    f'is down during {health_phase}: {health_detail}.'
                )
            elif service_health['state'] == 'degraded':
                add_assessment_finding(
                    assessment, 'warning', 'health',
                    f'{service_id}/{cg_id} {target_profile} canary health '
                    f'is degraded during {health_phase}: {health_detail}.'
                )
            else:
                add_assessment_finding(
                    assessment, 'info', 'health',
                    f'{service_id}/{cg_id} {target_profile} canary health '
                    f'is normal during {health_phase}: {health_detail}.'
                )
            if service_health['loss']:
                add_assessment_finding(
                    assessment, 'warning', 'health',
                    f'{service_id}/{cg_id} {target_profile} canary reports '
                    f'{service_health["loss"]} ppm loss.'
                )
            if (latency_bound and service_health['delay'] and
                    service_health['delay'] > latency_bound):
                assessment['transport_health_state'] = health_state_worse(
                    assessment['transport_health_state'], 'degraded'
                )
                add_assessment_finding(
                    assessment, 'warning', 'slo',
                    f'{service_id}/{cg_id} {target_profile} canary delay '
                    f'{service_health["delay"]} usec exceeds bound '
                    f'{latency_bound} usec.'
                )

    pe_devices = sorted({device_for_sdp(root, sdp) for sdp in all_sdps})
    assessment['pe_devices'] = pe_devices
    te_headends = sorted(device for device in pe_devices
                         if te_headend_supported(root, device))
    unsupported_headends = sorted(set(pe_devices) - set(te_headends))
    if unsupported_headends:
        add_assessment_finding(
            assessment, 'warning', 'multivendor-te',
            'The current netsim has no SR policy/ODN head-end renderer '
            f'for: {", ".join(unsupported_headends)}.'
        )
    if target_profile in ('sr-mpls', 'srv6') and not te_headends:
        add_assessment_finding(
            assessment, 'error', 'multivendor-te',
            'No selected PE has a supported SR policy/ODN renderer.'
        )

    core_devices = enabled_core_devices(root)
    srv6_devices = configured_srv6_devices(root)
    assessment['srv6_enabled_devices'] = len(srv6_devices)
    if target_profile == 'srv6':
        missing = sorted(set(core_devices) - set(srv6_devices))
        assessment['missing_srv6_devices'] = missing
        if missing:
            add_assessment_finding(
                assessment, 'warning', 'underlay',
                'SRv6 underlay is not enabled on all PE/core transport '
                f'nodes yet: {", ".join(missing)}.'
            )

    pm_profiles = pm_profile_count(root, service_id)
    assessment['pm_profiles'] = pm_profiles
    if pm_profiles == 0:
        add_assessment_finding(
            assessment, 'warning', 'pm',
            'No matching NSS PM profile exists yet. Commit or re-deploy the '
            'slice to create PM attachments.'
        )

    assurance_monitors = assurance_monitor_count(root, service_id,
                                                 connection_groups,
                                                 pe_devices)
    assessment['assurance_monitors'] = assurance_monitors
    if assurance_monitors == 0:
        add_assessment_finding(
            assessment, 'warning', 'assurance',
            'No matching NSS assurance monitors exist yet. Load assurance '
            'inputs and commit/re-deploy the slice.'
        )

    te_links, srlgs, highest_delay = topology_metrics(root)
    assessment['te_links'] = te_links
    assessment['srlgs'] = srlgs
    assessment['highest_delay'] = highest_delay
    edges, health_summary = enabled_te_edges(root)
    assessment['impaired_links'] = health_summary['impaired_links']
    assessment['blocked_links'] = health_summary['blocked_links']
    if te_links == 0:
        add_assessment_finding(
            assessment, 'warning', 'topology',
            'No enabled TE links were found in core-network.'
        )
    else:
        add_assessment_finding(
            assessment, 'info', 'topology',
            f'Local graph has {te_links} directed TE links, {srlgs} '
            f'directed SRLG assignments, and max per-link delay '
            f'{highest_delay} usec.'
        )
    if health_summary['blocked_links'] or health_summary['impaired_links']:
        add_assessment_finding(
            assessment, 'info', 'health',
            f'Health overlay removed {health_summary["blocked_links"]} '
            f'directed TE link(s) and annotated '
            f'{health_summary["impaired_links"]} directed TE link(s).'
        )

    if len(pe_devices) == 2:
        metric_type = 'latency'
        if connection_groups:
            metric_type = enum_text(
                getattr(connection_groups[0], 'metric_type', ''), 'latency'
            )
            if metric_type not in ('igp', 'te', 'latency'):
                metric_type = 'latency'
        paths = compute_candidate_paths(edges, pe_devices, metric_type)
        primary = paths['primary']
        assessment['healthy_te_path'] = (
            bool(primary) and path_health_state(primary) == 'normal'
        )
        assessment['primary_path'] = path_devices(primary)
        assessment['primary_delay'] = path_delay(primary)
        assessment['path_health'] = path_health_summary(primary)
        if not primary:
            add_assessment_finding(
                assessment, 'error', 'topology',
                f'No healthy TE path was found between '
                f'{", ".join(pe_devices)} '
                'after applying link health inputs.'
            )
        elif path_health_state(primary) != 'normal':
            assessment['transport_health_state'] = health_state_worse(
                assessment['transport_health_state'], 'degraded'
            )
            add_assessment_finding(
                assessment, 'warning', 'health',
                f'Selected PE-to-PE path health is '
                f'{assessment["path_health"]}.'
            )
        for connection_group in connection_groups:
            cg_id = str(connection_group.connection_group_id)
            latency_bound = (
                slo_template_values(root, connection_group)['latency_bound_us']
            )
            if (primary and latency_bound and
                    assessment['primary_delay'] > latency_bound):
                add_assessment_finding(
                    assessment, 'warning', 'slo',
                    f'{service_id}/{cg_id} selected TE path delay '
                    f'{assessment["primary_delay"]} usec exceeds bound '
                    f'{latency_bound} usec.'
                )
    elif pe_devices:
        add_assessment_finding(
            assessment, 'warning', 'topology',
            'Health-aware TE path validation expects exactly two PE '
            f'endpoints; found {len(pe_devices)}.'
        )

    add_assessment_finding(
        assessment, 'info', 'transport',
        f'Current profiles: '
        f'{", ".join(assessment["current_profiles"]) or "none"}; '
        f'target profile: {target_profile}; TE-capable head-ends: '
        f'{", ".join(te_headends) or "none"}.'
    )

    all_profiles_match = bool(connection_groups) and all(
        effective_transport_profile(connection_group) == target_profile
        for connection_group in connection_groups
    )
    assessment['all_profiles_match'] = all_profiles_match

    if include_cli:
        add_underlay_step_records(assessment,
                                  assessment['missing_srv6_devices'])
        if all_profiles_match and not assessment['missing_srv6_devices']:
            add_verification_step_records(assessment, service_id)
        elif not assessment['target_color_missing'] and connection_groups:
            add_service_step_records(assessment, service_id,
                                     connection_groups, target_profile,
                                     assessment['color_resolutions'])

    if assessment['errors']:
        decision = 'not-ready'
    elif (all_profiles_match and not assessment['missing_srv6_devices'] and
          not assessment['warnings']):
        decision = 'no-op'
    elif target_profile == 'srv6' and assessment['missing_srv6_devices']:
        decision = 'stage-underlay-first'
    elif assessment['warnings']:
        decision = 'ready-with-warnings'
    else:
        decision = 'ready'

    score = readiness_score(
        assessment['errors'], assessment['warnings'],
        len(assessment['missing_srv6_devices']),
        assessment['target_color_missing'], pm_profiles,
        assurance_monitors, te_links
    )
    assessment['decision'] = decision
    assessment['score'] = score
    assessment['sdp_count'] = len({str(sdp.sdp_id) for sdp in all_sdps})
    assessment['summary'] = (
        f'Advisor decision for slice {service_id}: {decision} '
        f'(score {score}/100). NSO sees SDPs={assessment["sdp_count"]}, '
        f'PEs={", ".join(pe_devices) or "none"}, current='
        f'{", ".join(assessment["current_profiles"]) or "none"}, '
        f'target={target_profile}, PM profiles={pm_profiles}, assurance '
        f'monitors={assurance_monitors}, missing SRv6 nodes='
        f'{", ".join(assessment["missing_srv6_devices"]) or "none"}, '
        f'color={assessment["resolved_color"] or "none"} '
        f'({assessment["color_source"]}), '
        f'path={assessment["primary_path"] or "none"}, '
        f'path-health={assessment["path_health"]}, '
        f'canary-health={assessment["transport_health_state"]}.'
    )
    return assessment


def render_assessment_output(output, assessment):
    output.service_id = assessment['service_id']
    output.target_profile = assessment['target_profile']
    output.decision = assessment['decision']
    output.readiness_score = assessment['score']
    output.current_profile = ', '.join(assessment['current_profiles'])
    if assessment['resolved_color']:
        output.resolved_color = assessment['resolved_color']
    output.color_source = assessment['color_source']
    output.color_pool = assessment['color_pool']
    output.sdp_count = assessment['sdp_count']
    output.pe_devices = ', '.join(assessment['pe_devices'])
    output.srv6_enabled_devices = assessment['srv6_enabled_devices']
    output.missing_srv6_devices = ', '.join(
        assessment['missing_srv6_devices']
    )
    output.pm_profiles = assessment['pm_profiles']
    output.assurance_monitors = assessment['assurance_monitors']
    output.te_links = assessment['te_links']
    output.srlgs = assessment['srlgs']
    output.impaired_links = assessment['impaired_links']
    output.blocked_links = assessment['blocked_links']
    output.healthy_te_path = assessment['healthy_te_path']
    output.primary_path = assessment['primary_path']
    output.primary_delay_microseconds = assessment['primary_delay']
    output.transport_health_state = assessment['transport_health_state']
    output.highest_delay_microseconds = assessment['highest_delay']
    output.summary = assessment['summary']

    for finding in assessment['findings']:
        entry = output.finding.create(finding['id'])
        entry.severity = finding['severity']
        entry.area = finding['area']
        entry.message = finding['message']

    for step in assessment['steps']:
        entry = output.recommended_step.create(step['id'])
        entry.command = step['command']
        entry.reason = step['reason']


def add_stage(output, state, stage_name: str, status: str, message: str):
    entry = output.stage.create(state['stage_id'])
    state['stage_id'] += 1
    entry.name = stage_name
    entry.status = status
    entry.message = message


def add_migration_findings(output, state, phase: str, assessment):
    for finding in assessment['findings']:
        entry = output.finding.create(state['finding_id'])
        state['finding_id'] += 1
        entry.phase = phase
        entry.severity = finding['severity']
        entry.area = finding['area']
        entry.message = finding['message']


def blocking_findings(assessment, allow_warnings=False,
                      ignore_underlay_warning=False):
    blockers = [finding for finding in assessment['findings']
                if finding['severity'] == 'error']
    if not allow_warnings:
        blockers.extend(
            finding for finding in assessment['findings']
            if (finding['severity'] == 'warning' and not (
                ignore_underlay_warning and finding['area'] == 'underlay'
            ))
        )
    return blockers


def native_dry_run_text(stage_name: str, result) -> str:
    lines = [f'### {stage_name} native dry-run']
    if not result:
        lines.append('native {\n}')
        return '\n'.join(lines)

    device_data = result.get('device') if isinstance(result, dict) else None
    if device_data:
        for device in sorted(device_data):
            rendered = str(device_data[device]).rstrip()
            lines.append(f'device {device}')
            lines.append(rendered if rendered else '  <no native changes>')
        return '\n'.join(lines)

    local = result.get('local-node') if isinstance(result, dict) else None
    if local:
        lines.append(str(local).rstrip())
    else:
        lines.append(str(result).rstrip())
    return '\n'.join(lines)


def run_write_stage(stage_name: str, execute: bool, mutator,
                    comment: str):
    with ncs.maapi.single_write_trans('admin', 'transport-advisor') as t:
        root = ncs.maagic.get_root(t)
        mutator(root)
        params = t.get_params()
        params.dry_run_native()
        result = t.apply_params(True, params)
        dry_run = native_dry_run_text(stage_name, result)
        if execute:
            commit_params = t.get_params()
            commit_params.label('transport-advisor')
            commit_params.comment(comment)
            t.apply_params(True, commit_params)
        return dry_run


def stage_srv6_underlay(root, devices):
    services = root.core_network.services.srv6_node
    for device in devices:
        if device not in services:
            services.create(device)


def capture_service_state(root, service_id: str, connection_group_ids):
    service = root.network_slice_services.slice_service[service_id]
    snapshot = {}
    for cg_id in connection_group_ids:
        connection_group = service.connection_group[cg_id]
        snapshot[cg_id] = {
            'transport_profile': enum_text(
                getattr(connection_group, 'transport_profile', ''), 'none'
            ),
            'transport_mode': enum_text(
                getattr(connection_group, 'transport_mode', ''), 'none'
            ),
            'color': text(getattr(connection_group, 'color', '')),
        }
    return snapshot


def set_service_transport(root, service_id: str, connection_group_ids,
                          target_profile: str, target_color,
                          color_pool_name=DEFAULT_COLOR_POOL,
                          color_base=DEFAULT_COLOR_BASE,
                          allocation_backend=COLOR_ALLOC_BACKEND_AUTO):
    service = root.network_slice_services.slice_service[service_id]
    reserved_colors = set()
    color_offset = 0
    for cg_id in connection_group_ids:
        connection_group = service.connection_group[cg_id]
        color_info = resolve_transport_color(
            root, service_id, cg_id, connection_group, target_profile,
            target_color, color_pool_name, color_base, color_offset,
            reserved_colors, allocation_backend
        )
        if color_info['source'] in ('local-pool', 'resource-manager'):
            reserved_colors.add(color_info['color_int'])
        if color_info['source'] == 'fallback-base':
            color_offset += 1
        reserve_transport_color(root, color_pool_name, service_id, cg_id,
                                target_profile, color_info)
        connection_group.transport_profile = target_profile
        try:
            connection_group.transport_mode = 'none'
        except Exception:
            pass
        color = color_info['color']
        if target_profile in ('sr-mpls', 'srv6') and color:
            connection_group.color = int(color)


def restore_service_transport(root, service_id: str, snapshot):
    service = root.network_slice_services.slice_service[service_id]
    for cg_id, values in snapshot.items():
        connection_group = service.connection_group[cg_id]
        connection_group.transport_profile = values['transport_profile']
        try:
            connection_group.transport_mode = values['transport_mode']
        except Exception:
            pass
        if values['color']:
            connection_group.color = int(values['color'])
        else:
            try:
                del connection_group.color
            except Exception:
                pass


def read_assessment(service_id: str, connection_group_name: str,
                    target_profile: str, target_color='', include_cli=False,
                    health_phase='pre-check',
                    color_pool_name=DEFAULT_COLOR_POOL,
                    color_base=DEFAULT_COLOR_BASE,
                    allocation_backend=COLOR_ALLOC_BACKEND_AUTO):
    with ncs.maapi.single_read_trans('admin', 'transport-advisor') as t:
        root = ncs.maagic.get_root(t)
        return build_assessment(root, service_id, connection_group_name,
                                target_profile, target_color, include_cli,
                                health_phase, color_pool_name, color_base,
                                allocation_backend)


class PlanTransportMigration(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        service_filter = text(getattr(input, 'slice_service', ''))
        connection_group_filter = text(getattr(input, 'connection_group', ''))
        target_profile = enum_text(input.target_profile, 'srv6')
        color_base = int_value(getattr(input, 'target_color_base', 3400),
                               3400)
        color_pool_name = text(getattr(input, 'color_pool', ''),
                               DEFAULT_COLOR_POOL)
        allocation_backend = enum_text(
            getattr(input, 'color_allocation_backend', ''),
            COLOR_ALLOC_BACKEND_AUTO
        )
        max_services_per_wave = int_value(
            getattr(input, 'max_services_per_wave', 1), 1
        )
        max_services_per_wave = max(1, max_services_per_wave)
        require_srv6_underlay = bool(input.require_srv6_underlay)
        include_no_op = bool(input.include_no_op)
        plan = build_transport_plan(
            root, service_filter, connection_group_filter, target_profile,
            color_base, color_pool_name, allocation_backend,
            max_services_per_wave, require_srv6_underlay, include_no_op
        )
        render_plan_output(output, plan)


class AssessTransportMigration(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        service_id = str(input.slice_service)
        connection_group_name = text(getattr(input, 'connection_group', ''))
        target_profile = enum_text(input.target_profile, 'srv6')
        target_color = text(getattr(input, 'target_color', ''))
        color_base = int_value(getattr(input, 'target_color_base', 3400),
                               3400)
        color_pool_name = text(getattr(input, 'color_pool', ''),
                               DEFAULT_COLOR_POOL)
        allocation_backend = enum_text(
            getattr(input, 'color_allocation_backend', ''),
            COLOR_ALLOC_BACKEND_AUTO
        )
        assessment = build_assessment(root, service_id, connection_group_name,
                                      target_profile, target_color,
                                      bool(input.include_cli), 'pre-check',
                                      color_pool_name, color_base,
                                      allocation_backend)
        render_assessment_output(output, assessment)


class MigrateTransport(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        service_id = str(input.slice_service)
        connection_group_name = text(getattr(input, 'connection_group', ''))
        target_profile = enum_text(input.target_profile, 'srv6')
        target_color = text(getattr(input, 'target_color', ''))
        color_base = int_value(getattr(input, 'target_color_base', 3400),
                               3400)
        color_pool_name = text(getattr(input, 'color_pool', ''),
                               DEFAULT_COLOR_POOL)
        allocation_backend = enum_text(
            getattr(input, 'color_allocation_backend', ''),
            COLOR_ALLOC_BACKEND_AUTO
        )
        execute = bool(input.execute)
        stage_underlay = bool(input.stage_underlay)
        allow_warnings = bool(input.allow_warnings)
        rollback_on_failed = bool(input.rollback_on_failed_post_check)
        state = {'stage_id': 1, 'finding_id': 1}
        dry_runs = []

        pre = read_assessment(service_id, connection_group_name,
                              target_profile, target_color, False,
                              'pre-check', color_pool_name, color_base,
                              allocation_backend)
        output.pre_decision = pre['decision']
        output.readiness_score = pre['score']
        output.color_pool = color_pool_name
        if pre['resolved_color']:
            output.resolved_color = pre['resolved_color']
        output.color_source = pre['color_source']
        add_migration_findings(output, state, 'pre-check', pre)

        blockers = blocking_findings(
            pre, allow_warnings,
            ignore_underlay_warning=stage_underlay and target_profile == 'srv6'
        )
        if blockers:
            output.status = 'blocked'
            output.summary = (
                f'Migration blocked before changes: {blockers[0]["message"]}'
            )
            add_stage(output, state, 'pre-check', 'blocked',
                      output.summary)
            return

        if pre['all_profiles_match'] and not pre['missing_srv6_devices']:
            output.status = 'no-op'
            output.post_decision = pre['decision']
            output.summary = (
                f'Slice {service_id} already matches {target_profile}; '
                'no migration transaction was needed.'
            )
            add_stage(output, state, 'pre-check', 'passed', pre['summary'])
            return

        missing_srv6 = pre['missing_srv6_devices']
        if missing_srv6 and not stage_underlay:
            output.status = 'blocked'
            output.summary = (
                'Migration blocked because SRv6 underlay is missing and '
                'stage-underlay is false.'
            )
            add_stage(output, state, 'underlay', 'blocked',
                      output.summary)
            return

        try:
            if missing_srv6:
                dry_run = run_write_stage(
                    'SRv6 underlay stage', execute,
                    lambda root: stage_srv6_underlay(root, missing_srv6),
                    f'transport-advisor staged SRv6 underlay for {service_id}'
                )
                dry_runs.append(dry_run)
                output.underlay_changed = execute
                add_stage(
                    output, state, 'underlay',
                    'committed' if execute else 'dry-run',
                    'Created missing srv6-node services: '
                    f'{", ".join(missing_srv6)}.'
                )
                if not execute:
                    output.status = 'planned'
                    output.native_dry_run = '\n\n'.join(dry_runs)
                    output.summary = (
                        'Plan produced only. Re-run with execute true to '
                        'commit the staged SRv6 underlay, then migrate the '
                        'service intent.'
                    )
                    return

            ready = read_assessment(service_id, connection_group_name,
                                    target_profile, target_color, False,
                                    'pre-check', color_pool_name, color_base,
                                    allocation_backend)
            if ready['resolved_color']:
                output.resolved_color = ready['resolved_color']
            output.color_source = ready['color_source']
            add_migration_findings(output, state, 'pre-service', ready)
            blockers = blocking_findings(ready, allow_warnings)
            if blockers:
                output.status = 'blocked'
                output.summary = (
                    'Migration blocked after underlay stage: '
                    f'{blockers[0]["message"]}'
                )
                add_stage(output, state, 'pre-service', 'blocked',
                          output.summary)
                output.native_dry_run = '\n\n'.join(dry_runs)
                return

            snapshot = {}
            with ncs.maapi.single_read_trans('admin',
                                             'transport-advisor') as t:
                root = ncs.maagic.get_root(t)
                snapshot = capture_service_state(
                    root, service_id, ready['connection_group_ids']
                )

            dry_run = run_write_stage(
                'Service transport migration', execute,
                lambda root: set_service_transport(
                    root, service_id, ready['connection_group_ids'],
                    target_profile, target_color, color_pool_name, color_base,
                    allocation_backend
                ),
                f'transport-advisor migrated {service_id} to {target_profile}'
            )
            dry_runs.append(dry_run)
            output.service_changed = execute
            add_stage(
                output, state, 'service',
                'committed' if execute else 'dry-run',
                f'Service transport intent set to {target_profile}.'
            )

            if not execute:
                output.status = 'planned'
                output.native_dry_run = '\n\n'.join(dry_runs)
                output.summary = (
                    'Plan produced only. Re-run with execute true to commit '
                    'the guarded service migration.'
                )
                return

            post = read_assessment(service_id, connection_group_name,
                                   target_profile, target_color, False,
                                   'post-check', color_pool_name, color_base,
                                   allocation_backend)
            output.post_decision = post['decision']
            output.readiness_score = post['score']
            add_migration_findings(output, state, 'post-check', post)
            post_blockers = blocking_findings(post, allow_warnings)
            post_failed = (
                bool(post_blockers) or
                not post['all_profiles_match'] or
                bool(post['missing_srv6_devices'])
            )
            if post_failed and rollback_on_failed:
                rollback_dry_run = run_write_stage(
                    'Rollback service transport migration', True,
                    lambda root: restore_service_transport(
                        root, service_id, snapshot
                    ),
                    f'transport-advisor rolled back {service_id}'
                )
                dry_runs.append(rollback_dry_run)
                output.rollback_performed = True
                output.status = 'rolled-back'
                output.summary = (
                    'Post-check failed, so the service transport intent was '
                    'rolled back to its previous state.'
                )
                add_stage(output, state, 'rollback', 'rolled-back',
                          output.summary)
            elif post_failed:
                output.status = 'failed'
                output.summary = (
                    'Post-check failed after committing the migration. '
                    f'Latest decision: {post["decision"]}.'
                )
                add_stage(output, state, 'post-check', 'failed',
                          output.summary)
            else:
                output.status = 'committed'
                output.summary = (
                    f'Slice {service_id} migrated to {target_profile}; '
                    'post-check confirms the service now matches the target '
                    'transport intent.'
                )
                add_stage(output, state, 'post-check', 'passed',
                          post['summary'])

            output.native_dry_run = '\n\n'.join(dry_runs)
        except Exception as exc:
            output.status = 'failed'
            output.summary = f'Migration action failed: {exc}'
            output.native_dry_run = '\n\n'.join(dry_runs)
            add_stage(output, state, 'exception', 'failed', output.summary)


class Main(Application):
    def setup(self):
        self.log.info('transport-advisor Main RUNNING')
        self.register_action('transport-advisor-plan',
                             PlanTransportMigration)
        self.register_action('transport-advisor-assess',
                             AssessTransportMigration)
        self.register_action('transport-advisor-migrate',
                             MigrateTransport)

    def teardown(self):
        self.log.info('transport-advisor Main FINISHED')
