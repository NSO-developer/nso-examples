from ipaddress import IPv4Network
import ncs


def main():
    services = []
    with open('services.txt', 'r') as f:
        for line in f:
            # Ignore comment lines
            if line.startswith('#'):
                continue

            parts = line.split('|')
            services.append([x.strip() for x in parts])

    with ncs.maapi.single_write_trans('admin', 'python') as t:
        root = ncs.maagic.get_root(t)
        for sr in services:
            name = sr[0]
            service = root.iface.create(name)
            service.device = sr[1]
            service.interface = sr[2]
            service.ip_address = sr[3]

            # If mask is missing, extract it from interface config
            if not sr[4]:
                devconfig = root.devices.device[service.device].config
                intf = devconfig.interface.GigabitEthernet[service.interface]
                netmask = intf.ip.address.primary.mask
                sr[4] = IPv4Network(f'0.0.0.0/{netmask}').prefixlen

            service.cidr_netmask = sr[4]

            if sr[5]:
                service.variant = sr[5]

        # Commit without generating device configs
        t.apply(flags=ncs.maapi.COMMIT_NCS_NO_DEPLOY)


main()
