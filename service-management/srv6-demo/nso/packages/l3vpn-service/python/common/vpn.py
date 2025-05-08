# -*- mode: python; python-indent: 4 -*-
import ncs


def assign_vni(customer, service_kind: str, requested: int,
               proplist: list[tuple], root) -> int:
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

    customer_key = customer.configuration_key or f'CUSTOMER_{customer.cid}'

    params = ncs.template.Variables()
    params.add('PE', port.device)
    params.add('INTERFACE_NO', intf_number)
    params.add('CUSTOMER', customer_key)
    params.add('VNI', vni if vni else '')
    return params
