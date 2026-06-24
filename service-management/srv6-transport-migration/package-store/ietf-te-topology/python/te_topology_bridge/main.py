# -*- mode: python; python-indent: 4 -*-
import ipaddress
import re

import ncs
from ncs.application import Application
from ncs.dp import Action


DEFAULT_NETWORK_ID = 'srv6-transport-migration-te'


def only_works_in_config(func):
    def hint_on_error(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except Exception as exc:
            if getattr(exc, 'confd_errno', 0) == ncs.ERR_NOT_WRITABLE:
                raise Exception('This action requires configure mode.')
            raise
    return hint_on_error


def construct_ip_from_base(base, index: int):
    ip_base = int(ipaddress.ip_address(str(base)))
    return ipaddress.ip_address(ip_base + index)


def router_id(root, device_name: str) -> str:
    info = root.core_network.devices[device_name]
    management_base = root.core_network.settings.management_base
    return str(construct_ip_from_base(management_base, int(info.index)))


def enabled_links(root):
    def key(link):
        return (
            str(link.device_a),
            str(link.interface_a),
            str(link.device_b),
            str(link.interface_b),
        )

    for link in sorted(root.core_network.links, key=key):
        try:
            if not bool(link.enabled):
                continue
        except Exception:
            pass
        yield link


def interface_ids(root):
    ids = {}
    for link in enabled_links(root):
        for device, interface in (
            (str(link.device_a), str(link.interface_a)),
            (str(link.device_b), str(link.interface_b)),
        ):
            per_device = ids.setdefault(device, {})
            if interface not in per_device:
                per_device[interface] = len(per_device) + 1
    return ids


def safe_id(*parts) -> str:
    raw = '-'.join(str(part) for part in parts)
    return re.sub(r'[^A-Za-z0-9_.-]+', '_', raw)


def create_presence(container):
    try:
        container.create()
    except Exception:
        pass


def leaf_int(node, name: str, default: int) -> int:
    try:
        value = getattr(node, name)
        return int(str(value))
    except Exception:
        return default


def leaf_list_values(node, name: str):
    try:
        return [int(str(value)) for value in getattr(node, name)]
    except Exception:
        return []


def add_leaf_list_value(leaf_list, value):
    try:
        leaf_list.create(value)
    except Exception:
        try:
            leaf_list.append(value)
        except Exception:
            pass


class ExportTeTopology(Action):
    @Action.action
    @only_works_in_config
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        network_id = str(input.network_id or DEFAULT_NETWORK_ID)

        networks = root.networks
        if network_id in networks.network:
            if not bool(input.overwrite):
                raise ValueError(
                    f'RFC 8345 network {network_id} already exists; '
                    'set overwrite true to refresh it'
                )
            del networks.network[network_id]

        network = networks.network.create(network_id)
        create_presence(network.network_types.te_topology)
        network.te_topology_identifier.provider_id = 0
        network.te_topology_identifier.client_id = 0
        network.te_topology_identifier.topology_id = network_id
        create_presence(network.te)
        network.te.name = (
            'SRv6 transport migration TE topology exported from core-network'
        )
        network.te.preference = 100

        tp_ids = interface_ids(root)
        node_count = 0
        tp_count = 0
        for device in root.core_network.devices:
            if not bool(device.enabled):
                continue

            device_name = str(device.name)
            node = network.node.create(device_name)
            node.te_node_id = router_id(root, device_name)
            create_presence(node.te)
            node.te.te_node_attributes.admin_status = 'up'
            node_count += 1

            device_tp_ids = sorted(tp_ids.get(device_name, {}).items())
            for ifname, te_tp_id in device_tp_ids:
                tp = node.termination_point.create(ifname)
                tp.te_tp_id = te_tp_id
                create_presence(tp.te)
                tp.te.name = ifname
                tp.te.admin_status = 'up'
                tp_count += 1

        link_count = 0
        link_index = 1
        srlg_count = 0
        for link in enabled_links(root):
            for src_dev, src_if, dst_dev, dst_if in (
                (
                    str(link.device_a),
                    str(link.interface_a),
                    str(link.device_b),
                    str(link.interface_b),
                ),
                (
                    str(link.device_b),
                    str(link.interface_b),
                    str(link.device_a),
                    str(link.interface_a),
                ),
            ):
                link_id = safe_id(src_dev, src_if, 'to', dst_dev, dst_if)
                te_link = network.link.create(link_id)
                te_link.source.source_node = src_dev
                te_link.source.source_tp = src_if
                te_link.destination.dest_node = dst_dev
                te_link.destination.dest_tp = dst_if
                create_presence(te_link.te)
                attrs = te_link.te.te_link_attributes
                attrs.name = f'{src_dev} {src_if} to {dst_dev} {dst_if}'
                attrs.access_type = 'point-to-point'
                attrs.admin_status = 'up'
                attrs.link_index = link_index
                attrs.te_default_metric = leaf_int(
                    link, 'te_default_metric', 10
                )
                attrs.te_igp_metric = leaf_int(link, 'te_igp_metric', 10)
                attrs.te_delay_metric = leaf_int(link, 'te_delay_metric', 1000)
                for srlg in leaf_list_values(link, 'srlg'):
                    add_leaf_list_value(attrs.te_srlgs.value, srlg)
                    srlg_count += 1
                link_count += 1
                link_index += 1

        output.network_id = network_id
        output.nodes = node_count
        output.links = link_count
        output.termination_points = tp_count
        output.srlgs = srlg_count


class Main(Application):
    def setup(self):
        self.log.info('te-topology-bridge Main RUNNING')
        self.register_action('te-topology-export', ExportTeTopology)

    def teardown(self):
        self.log.info('te-topology-bridge Main FINISHED')
