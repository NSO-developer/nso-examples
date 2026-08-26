# -*- mode: python; python-indent: 4 -*-
import ipaddress
import os
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree

import _ncs
import ncs
from ncs.application import NanoService, Service
from ncs.dp import Action


CREATE_CALLBACK_APPLY_LABEL = 'create-callback-apply'


def commit_label(root):
    trans = ncs.maagic.get_trans(root)
    params = trans.get_params()
    getter = getattr(params, 'get_label', None)
    if getter is not None:
        value = getter()
        if value is not None:
            return value

    for param in trans.get_trans_params():
        if _ncs.hash2str(param.tag) == 'label':
            return param.v.as_pyval()
    return None


def use_create_callback_apply(root):
    return commit_label(root) == CREATE_CALLBACK_APPLY_LABEL


def decrypt(value):
    return _ncs.decrypt(str(value))


def is_dry_run(root):
    checker = getattr(ncs.maagic.get_trans(root).get_params(),
                      'is_dry_run', None)
    return checker is not None and checker()


def authgroup_name(device):
    return f'ztp-{device}'


def set_authgroup_credentials(root, device_name, username, password):
    groups = root.devices.authgroups.group
    name = authgroup_name(device_name)
    group = groups[name] if name in groups else groups.create(name)
    umap = group.umap['admin'] if 'admin' in group.umap else group.umap.create(
        'admin')
    umap.remote_name = username
    umap.remote_password = password
    return name


def set_device_transport(device, service, authgroup):
    device.address = service.address
    device.port = service.port
    device.authgroup = authgroup
    ned_id = str(service.ned_id)
    device.device_type.cli.ned_id = (
        ned_id if ':' in ned_id else f'{ned_id}:{ned_id}')


def onboard_router(root, service):
    devices = root.devices.device
    device_name = str(service.device)
    device = devices[device_name] if device_name in devices else (
        devices.create(device_name))
    authgroup = set_authgroup_credentials(
        root, device_name, service.bootstrap_username,
        service.bootstrap_password)
    set_device_transport(device, service, authgroup)
    device.state.admin_state = 'unlocked'


def activate_netsim_credentials(service, username, password):
    env_file = Path('../netsim') / str(service.device) / str(
        service.device) / 'env.sh'
    if not env_file.exists():
        return False

    ipc_port = None
    for line in env_file.read_text(encoding='utf-8').splitlines():
        if line.startswith('export CONFD_IPC_PORT='):
            ipc_port = line.split('=', 1)[1].strip().strip('"')
            break
    if ipc_port is None:
        raise ncs.error.Error(f'missing CONFD_IPC_PORT in {env_file}')

    config_ns = 'http://tail-f.com/ns/config/1.0'
    aaa_ns = 'http://tail-f.com/ns/aaa/1.1'
    nacm_ns = 'urn:ietf:params:xml:ns:yang:ietf-netconf-acm'
    config = ElementTree.Element(f'{{{config_ns}}}config')
    aaa = ElementTree.SubElement(config, f'{{{aaa_ns}}}aaa')
    authentication = ElementTree.SubElement(
        aaa, f'{{{aaa_ns}}}authentication')
    users = ElementTree.SubElement(authentication, f'{{{aaa_ns}}}users')
    user = ElementTree.SubElement(users, f'{{{aaa_ns}}}user')
    for tag, value in (
            ('name', username), ('uid', '501'), ('gid', '20'),
            ('password', f'$0${password}'),
            ('ssh_keydir', f'/var/confd/homes/{username}/.ssh'),
            ('homedir', f'/var/confd/homes/{username}')):
        ElementTree.SubElement(user, f'{{{aaa_ns}}}{tag}').text = str(value)

    nacm = ElementTree.SubElement(config, f'{{{nacm_ns}}}nacm')
    groups = ElementTree.SubElement(nacm, f'{{{nacm_ns}}}groups')
    group = ElementTree.SubElement(groups, f'{{{nacm_ns}}}group')
    ElementTree.SubElement(group, f'{{{nacm_ns}}}name').text = 'admin'
    ElementTree.SubElement(
        group, f'{{{nacm_ns}}}user-name').text = str(username)

    ncs_dir = os.environ.get('NCS_DIR')
    if not ncs_dir:
        raise ncs.error.Error('NCS_DIR is required for netsim credential setup')
    confd_load = Path(ncs_dir) / 'netsim/confd/bin/confd_load'
    env = os.environ.copy()
    env.pop('NCS_IPC_PATH', None)
    env['CONFD_IPC_PORT'] = ipc_port
    subprocess.run(
        [str(confd_load), '-m', '-l'],
        input=ElementTree.tostring(config, encoding='utf-8'),
        check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        env=env)
    return True


def template_variables(root, service):
    settings = root.base_settings
    management = ipaddress.ip_interface(f'{service.management_ipv4}/24')
    variables = ncs.template.Variables()
    variables.add('BGP_AO_KEY_STRING', settings.bgp_ao_key_string)
    variables.add('BOOTSTRAP_USERNAME', service.bootstrap_username)
    variables.add('DEVICE', service.device)
    variables.add('DOMAIN_NAME', settings.domain_name)
    variables.add('GRPC_PORT', str(settings.grpc_port))
    variables.add('ISIS_AUTH_KEY_STRING', settings.isis_auth_key_string)
    variables.add('LOGGING_HOSTNAME_PREFIX',
                  str(service.device).replace('-', ''))
    variables.add('LOGGING_SERVER_1', settings.logging_server_1)
    variables.add('LOGGING_SERVER_2', settings.logging_server_2)
    variables.add('LOOPBACK0_IPV4', service.loopback0_ipv4)
    variables.add('LOOPBACK0_IPV6', service.loopback0_ipv6)
    variables.add('LOOPBACK100_IPV4', service.loopback100_ipv4)
    variables.add('MACSEC_KEY_ID', settings.macsec_key_id)
    variables.add('MACSEC_KEY_STRING', settings.macsec_key_string)
    variables.add('MANAGEMENT_GATEWAY', str(management.network[1]))
    variables.add('MANAGEMENT_IPV4', service.management_ipv4)
    variables.add('NTP_SERVER_1', settings.ntp_server_1)
    variables.add('NTP_SERVER_2', settings.ntp_server_2)
    variables.add('NTP_SERVER_3', settings.ntp_server_3)
    variables.add('PERMANENT_PASSWORD', decrypt(settings.permanent_password))
    variables.add('PERMANENT_USERNAME', settings.permanent_username)
    variables.add('SNMPV3_AUTH_PASSWORD',
                  decrypt(settings.snmpv3_auth_password))
    variables.add('SNMPV3_PRIV_PASSWORD',
                  decrypt(settings.snmpv3_priv_password))
    variables.add('SNMPV3_USERNAME', settings.snmpv3_username)
    return variables


def apply_credentials(root, service):
    settings = root.base_settings
    password = decrypt(settings.permanent_password)
    if not is_dry_run(root):
        activate_netsim_credentials(
            service, settings.permanent_username, password)
    authgroup = set_authgroup_credentials(
        root, str(service.device), settings.permanent_username, password)
    set_device_transport(
        root.devices.device[service.device], service, authgroup)


def render_base_stage(root, service, state):
    if state == 'brfs:credentials-activated':
        apply_credentials(root, service)
        return

    template = ncs.template.Template(service)
    variables = template_variables(root, service)
    if state == 'brfs:hardware-configured':
        template.apply('base-rfs-hardware-template', variables)
    elif state == 'brfs:base-configured':
        template.apply('base-rfs-template', variables)


BASE_PLAN_STATES = (
    ('brfs:onboarded', True, False),
    ('brfs:config-synced', True, False),
    ('brfs:hardware-configured', True, True),
    ('brfs:reloaded', True, False),
    ('brfs:reload-synced', True, False),
    ('brfs:base-configured', True, True),
    ('brfs:credentials-activated', False, True),
    ('brfs:credentials-disconnected', False, False),
    ('brfs:credentials-synced', False, False),
    ('ncs:ready', False, False),
)


def plan_state_statuses(service):
    statuses = {}
    try:
        for component in service.plan.component:
            if str(component.type) != 'brfs:base-router':
                continue
            for state in component.state:
                statuses[str(state.name)] = str(state.status)
    except (AttributeError, KeyError):
        pass
    return statuses


def render_next_base_stage(root, service):
    statuses = plan_state_statuses(service)
    for state, refresh_bootstrap, renders_config in BASE_PLAN_STATES:
        if statuses.get(state) == 'reached':
            continue
        if refresh_bootstrap:
            onboard_router(root, service)
        if renders_config:
            render_base_stage(root, service, state)
        return state, renders_config

    # An update to an already-ready service refreshes every configured stage.
    for state in ('brfs:hardware-configured', 'brfs:base-configured',
                  'brfs:credentials-activated'):
        render_base_stage(root, service, state)
    return 'all-config-stages', True


class BaseRfsCallbacks(Service):
    @Service.pre_modification
    def cb_pre_modification(self, tctx, op, kp, root, proplist):
        if op not in (ncs.dp.NCS_SERVICE_CREATE, ncs.dp.NCS_SERVICE_UPDATE):
            return

        service = ncs.maagic.cd(root, kp)
        if use_create_callback_apply(root):
            statuses = plan_state_statuses(service)
            if statuses.get('brfs:credentials-activated') != 'reached':
                onboard_router(root, service)
                self.log.info('Base RFS onboarding applied in '
                              'pre-modification; config deferred to nano '
                              'create callbacks for ', kp)
            else:
                self.log.info('Base RFS permanent credentials retained for ',
                              kp)
            return

        stage, rendered = render_next_base_stage(root, service)
        self.log.info('Base RFS pre-modification for ', kp, ' stage=', stage,
                      ' rendered-config=', rendered)

    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        if use_create_callback_apply(root):
            self.log.info('Base RFS create-callback-apply enabled for ',
                          service._path)


class BaseRfsNanoCallbacks(NanoService):
    @NanoService.create
    def cb_nano_create(self, tctx, root, service, plan, component, state,
                       proplist, component_proplist):
        self.log.info('Base RFS nano create(state=', state,
                      ' device=', service.device, ')')
        if state == 'brfs:onboarded':
            self.log.info('Base RFS onboarding skipped; pre-modification '
                          'created the non-FASTMAP device and authgroup')
            return

        if use_create_callback_apply(root):
            self.log.info(
                'Base RFS config applied in nano create callback for ', state)
            render_base_stage(root, service, state)
        else:
            self.log.info('Base RFS nano config skipped; pre-modification '
                          'performs the staged write for ', state)


class ReloadDeviceAction(Action):
    @Action.action
    def cb_action(self, uinfo, name, keypath, ainput, aoutput, trans):
        service = ncs.maagic.get_node(trans, keypath)
        device = str(service.device)
        self.log.info('Restart netsim device ', device,
                      ' after hardware configuration')
        subprocess.run(
            ['ncs-netsim', '--dir', '../netsim', 'stop', device],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            encoding='utf-8')
        subprocess.run(
            ['ncs-netsim', '--dir', '../netsim', 'start', device],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            encoding='utf-8')
        aoutput.result = True


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('Base RFS package RUNNING')
        with ncs.maapi.Maapi() as maapi:
            maapi.install_crypto_keys()

        self.register_service('base-rfs-servicepoint', BaseRfsCallbacks)
        for state in ('brfs:onboarded', 'brfs:hardware-configured',
                      'brfs:base-configured',
                      'brfs:credentials-activated'):
            self.register_nano_service(
                'base-rfs-servicepoint', 'brfs:base-router', state,
                BaseRfsNanoCallbacks)
        self.register_action('base-rfs-reload-device', ReloadDeviceAction)

    def teardown(self):
        self.log.info('Base RFS package FINISHED')
