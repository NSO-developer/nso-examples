# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service
from ncs.dp import Action


def child_text(obj, name, default=''):
    try:
        value = getattr(obj, name)
    except Exception:
        return default
    text = str(value)
    return default if text in ('', 'None', 'none') else text


def sensor_path_text(value):
    return ''.join(str(value).split())


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


def device_vendor(root, device_name: str) -> str:
    ned_id = device_ned_id(root, device_name)
    if ('cisco-iosxr-netsim-cli' in ned_id or
            'cisco-iosxr-netsim-nc' in ned_id):
        return 'ios-xr'
    if 'juniper-junos-netsim-nc' in ned_id:
        return 'junos'
    if 'alu-sr-netsim-cli' in ned_id:
        return 'sros'
    return ''


def telemetry_templates(vendor: str):
    if vendor in ('ios-xr', 'junos', 'sros'):
        return ('service-assurance-telemetry',
                'service-assurance-sensor-path')
    return ('', '')


def sample_seconds(profile) -> str:
    try:
        milliseconds = int(str(profile.sample_interval))
    except Exception:
        milliseconds = 30000
    return str(max(1, (milliseconds + 999) // 1000))


def junos_format(encoding: str) -> str:
    return 'json' if encoding == 'json' else 'gpb'


def sros_encoding(encoding: str) -> str:
    return 'json-ietf' if encoding == 'json' else 'proto'


def vendor_sensor_paths(profile, vendor: str):
    paths = []
    try:
        for sensor_path in profile.vendor_sensor_path:
            if str(sensor_path.vendor) == vendor:
                paths.append(sensor_path_text(sensor_path.path))
    except Exception:
        pass
    if paths:
        return paths
    return [
        sensor_path_text(sensor_path)
        for sensor_path in profile.sensor_path
    ]


def stable_subscription_id(
    device_name: str, sensor_group: str, index: int
) -> str:
    seed = f'{device_name}:{sensor_group}:{index}'
    value = sum((offset + 1) * ord(char)
                for offset, char in enumerate(seed))
    return str(100000 + (value % 900000000))


def apply_sensor_paths(service, profile, device, vendor, sensor_group,
                       destination_group, template_name):
    template = ncs.template.Template(service)
    for index, sensor_path in enumerate(vendor_sensor_paths(profile, vendor),
                                        start=1):
        params = ncs.template.Variables()
        params.add('DEVICE', device)
        params.add('DESTINATION_GROUP', destination_group)
        params.add('SENSOR_GROUP', sensor_group)
        params.add('SENSOR_NAME', f'{sensor_group}-{index}')
        params.add('SENSOR_PATH', str(sensor_path))
        params.add('SENSOR_INDEX', index)
        params.add('SAMPLE_SECONDS', sample_seconds(profile))
        params.add('SUBSCRIPTION_ID',
                   stable_subscription_id(device, sensor_group, index))
        template.apply(template_name, params)


class MonitorCallbacks(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        assurance = service.service_assurance
        if child_text(assurance, 'monitoring_state', 'active') == 'disabled':
            self.log.info('Service assurance disabled for ', service.name)
            return proplist

        profile = root.core_network.services.assurance_profile[service.profile]
        destination_group = f'sa-{profile.name}'
        sensor_group = f'sa-{service.name}'
        subscription = f'sa-{service.name}'

        for device in service.device:
            device_name = str(device)
            vendor = device_vendor(root, device_name)
            telemetry_template, sensor_template = telemetry_templates(vendor)
            if not telemetry_template:
                self.log.info('Skipping unsupported assurance device ',
                              device_name)
                continue

            params = ncs.template.Variables()
            params.add('DEVICE', device_name)
            params.add('DESTINATION_GROUP', destination_group)
            params.add('DESTINATION_ADDRESS', profile.destination_address)
            params.add('DESTINATION_PORT', profile.destination_port)
            params.add('ENCODING', profile.encoding)
            params.add('JUNOS_FORMAT', junos_format(str(profile.encoding)))
            params.add('SROS_ENCODING', sros_encoding(str(profile.encoding)))
            params.add('SENSOR_GROUP', sensor_group)
            params.add('SUBSCRIPTION', subscription)
            params.add('SAMPLE_INTERVAL', profile.sample_interval)
            params.add('SAMPLE_SECONDS', sample_seconds(profile))
            params.add('SOURCE_INTERFACE', profile.source_interface)

            template = ncs.template.Template(service)
            template.apply(telemetry_template, params)
            apply_sensor_paths(service, profile, device_name, vendor,
                               sensor_group, destination_group,
                               sensor_template)

        self.log.info('Service create(service=', service._path, ') DONE')
        return proplist


class MonitorSelfTest(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        root = ncs.maagic.get_root(trans)
        service = ncs.maagic.get_node(trans, kp)
        profile = root.core_network.services.assurance_profile[service.profile]

        supported = 0
        unsupported = 0
        sensor_paths = 0
        for device in service.device:
            vendor = device_vendor(root, str(device))
            telemetry_template, _ = telemetry_templates(vendor)
            if telemetry_template:
                supported += 1
                sensor_paths += len(vendor_sensor_paths(profile, vendor))
            else:
                unsupported += 1

        output.success = (
            unsupported == 0 and supported > 0 and sensor_paths > 0
        )
        output.supported_devices = supported
        output.unsupported_devices = unsupported
        output.sensor_paths = sensor_paths
        output.message = (
            f'Assurance monitor {service.name}: '
            f'supported devices={supported}, '
            f'unsupported devices={unsupported}, rendered sensor paths='
            f'{sensor_paths}.'
        )


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('service-assurance Main RUNNING')
        self.register_service('service-assurance-monitor-service',
                              MonitorCallbacks)
        self.register_action('service-assurance-self-test', MonitorSelfTest)

    def teardown(self):
        self.log.info('service-assurance Main FINISHED')
