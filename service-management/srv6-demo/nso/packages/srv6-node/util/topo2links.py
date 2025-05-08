#!/usr/bin/env python3
import ncs
import sys
import yaml


def import_topology_yaml(filename: str):
    with open(filename) as f:
        data = yaml.safe_load(f)

    with ncs.maapi.single_write_trans('admin', 'system') as t:
        root = ncs.maagic.get_root(t)
        links = root.core_network.links
        for l in data['xr_l2networks']:
            link = [x for part in l for x in part.split(':', 1)]
            if link[0].startswith('ce') or link[2].startswith('ce'):
                continue
            if not links.exists(link):
                print(f'Adding link {link}')
                links.create(link)
        t.apply()


if __name__ == '__main__':
    import_topology_yaml(sys.argv[1])
