# -*- mode: python; python-indent: 4 -*-
from contextlib import contextmanager
import importlib
import ipaddress
import sys
from pathlib import Path

import ncs


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


def select_customer_vni(
        root, allocation_name: str, requested: int | None) -> int | None:
    pool_name = 'customer-vni'
    pools = root.ralloc__resource_pools.idalloc__id_pool
    if pool_name not in pools:
        return requested

    pool = pools[pool_name]
    if pool.allocation.exists(allocation_name):
        return int(pool.allocation[allocation_name].resource)

    allocated = {int(alloc.resource) for alloc in pool.allocation}
    if requested:
        if requested in allocated:
            raise ValueError(
                f'VNI {requested} is already allocated from {pool_name}')
        return requested

    try:
        start = int(pool.range.start)
        end = int(pool.range.end)
    except Exception:
        return None

    for candidate in range(start, end + 1):
        if candidate not in allocated:
            return candidate

    raise ValueError(f'No free VNIs left in {pool_name}')


@contextmanager
def maybe_rm_service(service, context: str = 'notsystem'):
    try:
        get_rm_allocator()
    except ModuleNotFoundError:
        yield None
        return

    base_trans = ncs.maagic.get_trans(service)
    with ncs.maapi.Maapi() as maapi:
        maapi.start_user_session('admin', context)
        usid = maapi.get_my_user_session_id()
        with maapi.attach(base_trans.th, usid=usid) as attached_trans:
            yield ncs.maagic.get_node(attached_trans, service._path)


def assign_vni(customer, service_kind: str, requested: int,
               proplist: list[tuple], service, root) -> int:
    try:
        # If Resource Manager is available, use it to allocate a VNI.
        Allocator = get_rm_allocator()
        allocation_name = f'{service_kind}-{service.name}'
        candidate = select_customer_vni(root, allocation_name, requested)
        request = Allocator(service).id(candidate).pool('customer-vni')
        return request.allocate(allocation_name)
    except ModuleNotFoundError:
        # Otherwise fall back to the simple inventory-backed allocator below.
        pass

    if requested:
        vni = requested
    else:
        # Allocate and persist a VNI in opaque properties, avoiding
        # new allocation during re-deploy.
        vni = None
        for p, v in proplist:
            if p == 'VNI':
                vni = int(v)
                break

        if not vni:
            # For simplicity we don't handle previously assigned but
            # now released IDs. In practice we would likely call out
            # to some external system anyway.
            last_vni = list(root.inventory.vni.filter(xpath_expr='last()'))
            if last_vni:
                vni = int(last_vni[0].vnid) + 1
            else:
                vni = 1
            proplist.append(('VNI', str(vni)))

    if vni in root.inventory.vni:
        entry = root.inventory.vni[vni]
    else:
        entry = root.inventory.vni.create(vni)
        entry.customer = customer.name
        entry.service = service_kind

    if entry.customer != customer.name:
        raise ValueError(f'Cannot assign VNI {vni} from customer '
                         f'{entry.customer} to {customer.name}!')
    return vni


def extract_interface_number(intf: str):
    for i, c in enumerate(intf):
        if c.isdigit():
            return intf[i:]
    raise ValueError(f'Unknown interface format {intf}')


def construct_ip_from_base(base, index: int):
    ip_base = int(ipaddress.ip_address(base))
    return ipaddress.ip_address(ip_base + index)


def port_configuration(root, customer, pid, vni=None, force=False) \
        -> ncs.template.Variables:
    """
    Create template variables for specified port (pid).
    """
    port = root.inventory.port[pid]
    if port.customer != customer.name:
        raise ValueError(f'Cannot assign port {pid} from customer '
                         f'{port.customer} to {customer.name}!')
    if port.in_use and not force:
        raise ValueError(f'Port {pid} is already in use. '
                         f'Use (the) force if you know better.')

    port.in_use = True
    intf_number = extract_interface_number(port.interface)
    router_id = ''
    loopback_ip = ''
    srv6_prefix = ''
    try:
        device_info = root.core_network.devices[port.device]
        mgmt_base = root.core_network.settings.management_base
        router_id = str(construct_ip_from_base(mgmt_base, device_info.index))
        loopback_ip = f'fd00::{device_info.index}'
        srv6_prefix = f'5f00:0:{device_info.index}:'
    except Exception:
        pass

    customer_key = customer.configuration_key or f'CUSTOMER_{customer.cid}'

    params = ncs.template.Variables()
    params.add('AS', 65000)
    params.add('PE', port.device)
    params.add('INTERFACE', port.interface)
    params.add('INTERFACE_NO', intf_number)
    params.add('ROUTER_ID', router_id)
    params.add('LOOPBACK_IP', loopback_ip)
    params.add('SRV6_PREFIX', srv6_prefix)
    params.add('CUSTOMER', customer_key)
    params.add('VNI', vni if vni else '')
    return params
